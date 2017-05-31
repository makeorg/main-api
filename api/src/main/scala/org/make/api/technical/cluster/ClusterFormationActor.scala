package org.make.api.technical.cluster

import java.time.{ZoneOffset, ZonedDateTime}

import akka.actor.{Actor, ActorLogging, ActorRef, AddressFromURIString, Props}
import akka.cluster.Cluster
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.make.api.extensions.MakeSettingsExtension
import org.make.api.technical.ConsulActor
import org.make.api.technical.ConsulActor.{RenewSession, _}
import org.make.api.technical.ConsulEntities.{CreateSessionResponse, ReadResponse, WriteResponse}
import org.make.api.technical.cluster.ClusterFormationActor._
import org.make.core.CirceFormatters

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * Actor responsible for connecting an actor to seeds and handle the node lifecycle
  */
class ClusterFormationActor extends Actor with MakeSettingsExtension with ActorLogging with CirceFormatters {

  private val consulClient: ActorRef = context.actorOf(ConsulActor.props, ConsulActor.name)
  private var sessionId: String = _

  implicit val dispatch: ExecutionContextExecutor = context.dispatcher
  implicit val defaultTimeout: Timeout = Timeout(5.seconds)

  private val scheduler = context.system.scheduler
  private val clusterSettings = settings.Cluster

  private val timers =
    Seq(
      scheduler.schedule(Duration.Zero, clusterSettings.heartbeatInterval, self, Heartbeat),
      scheduler.schedule(Duration.Zero, clusterSettings.heartbeatInterval, self, Connect),
      scheduler
        .schedule(clusterSettings.sessionRenewInterval, clusterSettings.sessionRenewInterval, self, RenewMySession),
      scheduler.schedule(clusterSettings.cleanupInterval, clusterSettings.cleanupInterval, self, Cleanup)
    )

  // Schedule administrative tasks
  override def preStart(): Unit = {
    self ! Init
  }


  override def postStop(): Unit = {
    timers.foreach(_.cancel())
  }

  override def receive: Receive = {
    case Init                        => InitOperations.onInit
    case CreateSessionResponse(id)   => InitOperations.onSessionCreated(id)
    case GetSessionFailed(cause)     => InitOperations.onCreateSessionFailed(cause)
    case WriteSeedSucceeded(_)       => InitOperations.onNewSeed()
    case WriteSeedFailed(_)          => InitOperations.onSeedAlreadyLocked()
    case SeedRetrieved(node)         => InitOperations.onSeedRetrieved(node)
    case ConsulFailure(operation, e) => InitOperations.onConsuleFailure(operation, e)
    case x                           => InitOperations.onOtherInitMessage(x)

  }

  object InitOperations {

    private[ClusterFormationActor] def onOtherInitMessage(x: Any) = {
      context.system.scheduler.scheduleOnce(100.milliseconds, self, x)
    }

    private[ClusterFormationActor] def onConsuleFailure(operation: String, e: Throwable) = {
      log.error("Error when calling {} on consul: {}", operation, e)
      context.system.scheduler.scheduleOnce(2.seconds, self, Init)
    }

    private[ClusterFormationActor] def onSeedRetrieved(node: Node) = {
      Cluster(context.system).join(AddressFromURIString(node.address))
      context.become(ready)
    }

    private[ClusterFormationActor] def onSeedAlreadyLocked() = {
      // Some other node already has the lock, retrieve it and connect to it
      val seedInTheFuture = consulClient ? GetKey(s"${settings.Cluster.name}/seed")

      pipe(seedInTheFuture.map {
        case ReadResponse(_, Some(value)) => SeedRetrieved(parseNode(value))
        case _                            => Init
      }).to(self)
    }

    private[ClusterFormationActor] def onNewSeed() = {
      // I became the master, so I need to connect to myself
      val cluster = Cluster(context.system)
      cluster.join(cluster.selfAddress)
      context.become(ready)
    }

    private[ClusterFormationActor] def onCreateSessionFailed(cause: Throwable) = {
      log.error("Unable to initialize session {}", cause)
      context.system.scheduler.scheduleOnce(2.seconds, self, Init)
    }

    private[ClusterFormationActor] def onSessionCreated(id: String) = {
      log.debug("Got session id {}", id)
      sessionId = id

      val writingSeedInTheFuture = consulClient ? WriteExclusiveKey(
        s"${settings.Cluster.name}/seed",
        id,
        Node(Cluster(context.system).selfAddress.toString, ZonedDateTime.now(ZoneOffset.UTC)).asJson.toString
      )

      pipe(writingSeedInTheFuture.map {
        case r @ WriteResponse(true, _, _)  => WriteSeedSucceeded(r)
        case r @ WriteResponse(false, _, _) => WriteSeedFailed(r)
        case other                          => log.error(s"Unexpected message: $other")
      }).to(self)
    }

    private[ClusterFormationActor] def onInit = {
      log.debug("Starting init process")

      val sessionInTheFuture =
        (consulClient ? CreateSession(settings.Cluster.sessionTimeout)).recoverWith {
          case x => Future.failed(GetSessionFailed(x))
        }
      pipe(sessionInTheFuture).to(self)
    }
  }

  def parseNode(json: String): Node = {
    val asJson = parse(json) match {
      case Right(value) => value
      case Left(e)      => throw e
    }

    asJson.as[Node] match {
      case Right(value) => value
      case Left(e)      => throw e
    }
  }

  def ready: Receive = {
    case Connect =>
      log.debug("Received CONNECT message")
      val cluster = Cluster(context.system)
      if (cluster.state.members.isEmpty) {
        self ! Init
        context.become(receive)
      }
    case Heartbeat =>
      log.debug("Received Heartbeat message")
      val cluster = Cluster(context.system)
      consulClient ! WriteExclusiveKey(
        s"${settings.Cluster.name}/seed",
        sessionId,
        Node(cluster.selfAddress.toString, ZonedDateTime.now(ZoneOffset.UTC)).asJson
          .toString()
      )
      val address = cluster.selfAddress
      consulClient ! WriteKey(
        s"${settings.Cluster.name}/${address.host.get}-${address.port.get}",
        Node(cluster.selfAddress.toString, ZonedDateTime.now(ZoneOffset.UTC)).asJson
          .toString()
      )

    case Cleanup =>
      log.debug("Received Cleanup message")

    case RenewMySession =>
      log.debug("Received RenewSession message")
      consulClient ! RenewSession(this.sessionId)

  }

}

object ClusterFormationActor {

  val name: String = "clustering-actor"
  val props: Props = Props[ClusterFormationActor]

  case class Node(address: String, lastHeartbeat: ZonedDateTime)

  case object Heartbeat

  case object RenewMySession

  case object Cleanup

  case object Connect

  case object Init

  case class InitSessionId(id: String)

  case class GetSessionFailed(cause: Throwable) extends Exception(cause)

  case class WriteSeedSucceeded(consulResponse: WriteResponse)

  case class WriteSeedFailed(consulResponse: WriteResponse)

  case class SeedRetrieved(node: Node)

}
