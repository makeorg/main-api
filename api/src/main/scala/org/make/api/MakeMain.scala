package org.make.api

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import kamon.Kamon
import org.make.api.extensions.{DatabaseConfiguration, MakeSettings}
import org.make.core.proposal.ProposalId

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object MakeMain extends App with StrictLogging with MakeApi {

  Kamon.start()

  override implicit val actorSystem = ActorSystem("make-api")
  actorSystem.registerExtension(DatabaseConfiguration)
  actorSystem.actorOf(MakeGuardian.props, MakeGuardian.name)

  private val settings = MakeSettings(actorSystem)
  implicit val ec = actorSystem.dispatcher
  implicit val materializer = ActorMaterializer()

  val host = settings.Http.host
  val port = settings.Http.port

  val bindingFuture: Future[ServerBinding] =
    Http().bindAndHandle(makeRoutes, host, port)

  bindingFuture.map { serverBinding =>
    logger.info(s"Make API bound to ${serverBinding.localAddress} ")
  }.onComplete {
    case util.Failure(ex) =>
      logger.error(s"Failed to bind to $host:$port!", ex)
      actorSystem.terminate()
    case _ =>
  }

  if (settings.sendTestData) {
    // Wait until cluster is ready to send test data
    while (Cluster(actorSystem).state.members.isEmpty) {
      Thread.sleep(10.seconds.toMillis)
    }
    Thread.sleep(10.seconds.toMillis)
    logger.debug("Proposing...")
    proposalService.propose(idGenerator.nextUserId(), ZonedDateTime.now, "Il faut que la demo soit fonctionnelle.")
    val propId: ProposalId = Await.result(
      proposalService
        .propose(idGenerator.nextUserId(), ZonedDateTime.now, "we must propose"),
      Duration.Inf
    ) match {
      case Some(proposal) => proposal.proposalId
      case None           => ProposalId("Invalid ProposalId")
    }
    proposalService.update(propId, ZonedDateTime.now, "we must update a proposal")
    logger.debug("Sent proposals...")
  }

}
