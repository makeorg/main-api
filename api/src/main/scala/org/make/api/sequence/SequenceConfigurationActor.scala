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

import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.make.api.sequence.SequenceConfigurationActor.SequenceConfigurationActorProtocol
import org.make.core.question.QuestionId
import org.make.core.sequence.SequenceConfiguration

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object SequenceConfigurationActor {
  def apply(
    persistentSequenceConfigurationService: PersistentSequenceConfigurationService
  ): Behavior[SequenceConfigurationActorProtocol] = {
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(ReloadSequenceConfiguration, 5.minutes)
      handleMessages(Map.empty)(persistentSequenceConfigurationService)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def handleMessages(configCache: Map[QuestionId, SequenceConfiguration])(
    implicit persistentSequenceConfigurationService: PersistentSequenceConfigurationService
  ): Behavior[SequenceConfigurationActorProtocol] = {
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case ReloadSequenceConfiguration =>
          context.pipeToSelf(persistentSequenceConfigurationService.findAll()) {
            case Success(configurations) => UpdateSequenceConfiguration(configurations)
            case Failure(e)              => UpdateSequenceConfigurationsFailed(e)
          }
          Behaviors.same
        case UpdateSequenceConfiguration(configurations) =>
          val configCache = configurations.map { configuration =>
            configuration.questionId -> configuration
          }.toMap
          handleMessages(configCache)
        case UpdateSequenceConfigurationsFailed(e) =>
          context.log.error("Unable to reload sequence configurations:", e)
          Behaviors.stopped
        case GetSequenceConfiguration(questionId, replyTo) =>
          replyTo ! CachedSequenceConfiguration(configCache.getOrElse(questionId, SequenceConfiguration.default))
          Behaviors.same
      }
    }
  }

  sealed trait SequenceConfigurationActorProtocol
  case object ReloadSequenceConfiguration extends SequenceConfigurationActorProtocol
  private final case class UpdateSequenceConfiguration(configurations: Seq[SequenceConfiguration])
      extends SequenceConfigurationActorProtocol
  private final case class UpdateSequenceConfigurationsFailed(e: Throwable) extends SequenceConfigurationActorProtocol

  final case class GetSequenceConfiguration(questionId: QuestionId, replyTo: ActorRef[CachedSequenceConfiguration])
      extends SequenceConfigurationActorProtocol

  final case class CachedSequenceConfiguration(sequenceConfiguration: SequenceConfiguration)
  val name: String = "sequence-configuration-cache"
  val SequenceCacheActorKey: ServiceKey[SequenceConfigurationActorProtocol] = ServiceKey(name)

}

trait SequenceConfigurationActorComponent {
  def sequenceConfigurationActor: ActorRef[SequenceConfigurationActorProtocol]
}
