/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.sequence

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.pattern.{pipe, BackoffOpts, BackoffSupervisor}
import org.make.api.sequence.SequenceConfigurationActor._
import org.make.core.question.QuestionId
import org.make.core.sequence.SequenceId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class SequenceConfigurationActor(persistentSequenceConfigurationService: PersistentSequenceConfigurationService)
    extends Actor
    with ActorLogging {

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
    context.system.scheduler.scheduleWithFixedDelay(0.seconds, 5.minutes, self, ReloadSequenceConfiguration)
    ()
  }

  override def receive: Receive = {
    case ReloadSequenceConfiguration                 => refreshCache()
    case UpdateSequenceConfiguration(configurations) => updateConfiguration(configurations)
    case GetSequenceConfiguration(sequenceId) =>
      sender() ! CachedSequenceConfiguration(configCache.getOrElse(sequenceId, SequenceConfiguration.default))
    case GetSequenceConfigurationByQuestionId(questionId) =>
      sender() ! CachedSequenceConfiguration(
        configCache.values.find(_.questionId == questionId).getOrElse(SequenceConfiguration.default)
      )
    case SetSequenceConfiguration(configuration) =>
      pipe(persistentSequenceConfigurationService.persist(configuration)).to(sender())
      ()
    case GetPersistentSequenceConfiguration(sequenceId) =>
      pipe(persistentSequenceConfigurationService.findOne(sequenceId).map(StoredSequenceConfiguration.apply))
        .to(sender())
      ()
    case GetPersistentSequenceConfigurationByQuestionId(questionId) =>
      pipe(persistentSequenceConfigurationService.findOne(questionId).map(StoredSequenceConfiguration.apply))
        .to(sender())
      ()
  }

}

object SequenceConfigurationActor {
  sealed trait SequenceConfigurationActorProtocol
  case object ReloadSequenceConfiguration extends SequenceConfigurationActorProtocol
  case class UpdateSequenceConfiguration(configurations: Seq[SequenceConfiguration])
      extends SequenceConfigurationActorProtocol
  case class GetSequenceConfiguration(sequenceId: SequenceId) extends SequenceConfigurationActorProtocol
  case class GetSequenceConfigurationByQuestionId(questionId: QuestionId) extends SequenceConfigurationActorProtocol
  case class SetSequenceConfiguration(sequenceConfiguration: SequenceConfiguration)
      extends SequenceConfigurationActorProtocol
  case class GetPersistentSequenceConfiguration(sequenceId: SequenceId) extends SequenceConfigurationActorProtocol
  case class GetPersistentSequenceConfigurationByQuestionId(questionId: QuestionId)
      extends SequenceConfigurationActorProtocol

  case class CachedSequenceConfiguration(sequenceConfiguration: SequenceConfiguration)
  case class StoredSequenceConfiguration(sequenceConfiguration: Option[SequenceConfiguration])

  val name = "sequence-configuration-backoff"
  val internalName = "sequence-configuration-backoff"

  def props(persistentSequenceConfigurationService: PersistentSequenceConfigurationService): Props = {
    val maxNrOfRetries = 50
    BackoffSupervisor.props(
      BackoffOpts
        .onStop(
          Props(new SequenceConfigurationActor(persistentSequenceConfigurationService)),
          childName = internalName,
          minBackoff = 3.seconds,
          maxBackoff = 30.seconds,
          randomFactor = 0.2
        )
        .withMaxNrOfRetries(maxNrOfRetries)
    )
  }

}

trait SequenceConfigurationActorComponent {
  def sequenceConfigurationActor: ActorRef
}
