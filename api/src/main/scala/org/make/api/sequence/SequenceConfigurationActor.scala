package org.make.api.sequence

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.pattern.{pipe, Backoff, BackoffSupervisor}
import org.make.api.sequence.SequenceConfigurationActor._
import org.make.core.sequence.SequenceId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class SequenceConfigurationActor(persistentSequenceConfigurationService: PersistentSequenceConfigurationService)
    extends Actor
    with ActorLogging {
  val defaultConfiguration: SequenceConfiguration = SequenceConfiguration(
    sequenceId = SequenceId("default"),
    newProposalsRatio = 0.5,
    newProposalsVoteThreshold = 100,
    testedProposalsEngagementThreshold = 0.8,
    testedProposalsScoreThreshold = 0.0,
    testedProposalsControversyThreshold = 0.0,
    banditEnabled = true,
    banditMinCount = 1,
    banditProposalsRatio = 0.0,
    ideaCompetitionEnabled = false,
    ideaCompetitionTargetCount = 50,
    ideaCompetitionControversialRatio = 0.0,
    ideaCompetitionControversialCount = 0
  )

  var configCache: Map[SequenceId, SequenceConfiguration] = Map.empty

  def refreshCache(): Unit = {
    val futureConfigs: Future[Seq[SequenceConfiguration]] = persistentSequenceConfigurationService.findAll()
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
      pipe(persistentSequenceConfigurationService.persist(configuration)).to(sender())
    case GetPersistentSequenceConfiguration(sequenceId) =>
      pipe(persistentSequenceConfigurationService.findOne(sequenceId)).to(sender())
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

  def props(persistentSequenceConfigurationService: PersistentSequenceConfigurationService): Props =
    BackoffSupervisor.props(
      Backoff.onStop(
        Props(new SequenceConfigurationActor(persistentSequenceConfigurationService)),
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
