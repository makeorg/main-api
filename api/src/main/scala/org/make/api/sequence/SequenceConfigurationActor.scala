package org.make.api.sequence

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import org.make.api.sequence.SequenceConfigurationActor._
import org.make.core.sequence.SequenceId
import akka.pattern.{pipe, Backoff, BackoffSupervisor}
import org.make.api.extensions.MakeDBExecutionContextComponent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class SequenceConfigurationActor(makeDBExecutionContextComponent: MakeDBExecutionContextComponent)
    extends Actor
    with DefaultPersistentSequenceConfigServiceComponent
    with MakeDBExecutionContextComponent
    with ActorLogging {
  override def readExecutionContext: ExecutionContext = makeDBExecutionContextComponent.readExecutionContext
  override def writeExecutionContext: ExecutionContext = makeDBExecutionContextComponent.writeExecutionContext
  val defaultConfiguration: SequenceConfiguration = SequenceConfiguration(
    sequenceId = SequenceId("default"),
    newProposalsRatio = 0.5,
    newProposalsVoteThreshold = 100,
    testedProposalsEngagementThreshold = 0.8,
    banditEnabled = true,
    banditMinCount = 3,
    banditProposalsRatio = 0.3
  )

  var configCache: Map[SequenceId, SequenceConfiguration] = Map.empty

  def refreshCache(): Unit = {
    val futureConfigs: Future[Seq[SequenceConfiguration]] = persistentSequenceConfigService.findAll()
    futureConfigs.onComplete {
      case Success(configs) => self ! UpdateSequenceConfiguration(configs)
      case Failure(e) =>
        log.error(e, "Error while refreshing sequence configuration")
        self ! PoisonPill
    }
  }

  def updateConfiguration(configurations: Seq[SequenceConfiguration]): Unit = {
    configCache = configurations.map { configuration =>
      configuration.sequenceId -> configuration
    }.toMap
  }

  override def preStart(): Unit = {
    context.system.scheduler.schedule(0.seconds, 5.minutes, self, ReloadSequenceConfiguration)
  }

  override def receive: Receive = {
    case ReloadSequenceConfiguration                 => refreshCache()
    case UpdateSequenceConfiguration(configurations) => updateConfiguration(configurations)
    case GetSequenceConfiguration(sequenceId) =>
      sender() ! configCache.getOrElse(sequenceId, defaultConfiguration)
    case SetSequenceConfiguration(configuration) =>
      pipe(persistentSequenceConfigService.persist(configuration)).to(sender())
    case GetPersistentSequenceConfiguration(sequenceId) =>
      pipe(persistentSequenceConfigService.findOne(sequenceId)).to(sender())
  }

}

object SequenceConfigurationActor {
  case object ReloadSequenceConfiguration
  case class UpdateSequenceConfiguration(configurations: Seq[SequenceConfiguration])
  case class GetSequenceConfiguration(sequenceId: SequenceId)
  case class SetSequenceConfiguration(sequenceConfiguration: SequenceConfiguration)
  case class GetPersistentSequenceConfiguration(sequenceId: SequenceId)

  val name = "sequence-configuration-backoff"
  val internalName = "sequence-configuration-backoff"

  def props(makeDBExecutionContextComponent: MakeDBExecutionContextComponent): Props =
    BackoffSupervisor.props(
      Backoff.onStop(
        Props(new SequenceConfigurationActor(makeDBExecutionContextComponent)),
        childName = internalName,
        minBackoff = 3.seconds,
        maxBackoff = 30.seconds,
        randomFactor = 0.2
      )
    )

}

trait SequenceConfigurationActorComponent {
  def sequenceConfigurationActor: ActorRef
}
