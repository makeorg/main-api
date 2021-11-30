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
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import cats.data.{NonEmptyList => Nel}
import org.make.api.technical.ActorProtocol
import org.make.api.technical.sequence.SequenceCacheConfiguration
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.question.QuestionId
import org.make.core.sequence.SequenceConfiguration

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object SequenceCacheManager {

  def apply(
    config: SequenceCacheConfiguration,
    sequenceService: SequenceService,
    sequenceConfigurationService: SequenceConfigurationService
  ): Behavior[Protocol] = {
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(ExpireChildren, config.checkInactivityTimer)
      handleMessage(Map.empty)(reloadProposals(config, sequenceService, sequenceConfigurationService), config)
    }
  }

  def reloadProposals(
    sequenceCacheConfiguration: SequenceCacheConfiguration,
    sequenceService: SequenceService,
    sequenceConfigurationService: SequenceConfigurationService
  )(questionId: QuestionId): Future[Nel[IndexedProposal]] = {
    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    def simpleSequence(behaviour: SequenceBehaviour, retries: Int = 3): Future[Nel[IndexedProposal]] = {
      sequenceService.simpleSequence(Seq.empty, behaviour, Seq.empty, None).flatMap {
        case Seq() =>
          if (retries > 0) {
            simpleSequence(behaviour, retries - 1)
          } else {
            Future.failed(
              new IllegalStateException(
                s"Question ${questionId.value} might not have any proposals eligible for sequence."
              )
            )
          }
        case head +: tail => Future.successful(Nel(head, tail.toList))
      }
    }

    sequenceConfigurationService.getSequenceConfigurationByQuestionId(questionId).flatMap { config =>
      val customConfig: SequenceConfiguration = config
        .copy(mainSequence = config.mainSequence.copy(sequenceSize = sequenceCacheConfiguration.proposalsPoolSize))
      val behaviour = SequenceBehaviourProvider[Unit].behaviour((), customConfig, questionId, None)
      simpleSequence(behaviour)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def handleMessage(configCache: Map[QuestionId, ActorRef[SequenceCacheActor.Protocol]])(
    implicit reloadProposals: QuestionId => Future[Nel[IndexedProposal]],
    config: SequenceCacheConfiguration
  ): Behavior[Protocol] = {
    Behaviors.setup { context =>
      Behaviors
        .receiveMessage[Protocol] {
          case GetProposal(questionId, replyTo) =>
            configCache.get(questionId) match {
              case None =>
                val cache: ActorRef[SequenceCacheActor.Protocol] =
                  context.spawn(
                    SequenceCacheActor(questionId, reloadProposals, config),
                    SequenceCacheActor.name(questionId)
                  )
                context.watchWith(cache, ChildTerminated(questionId))
                cache ! SequenceCacheActor.GetProposal(replyTo)
                handleMessage(configCache ++ Map(questionId -> cache))
              case Some(actorRef) =>
                actorRef ! SequenceCacheActor.GetProposal(replyTo)
                Behaviors.same
            }
          case ExpireChildren =>
            configCache.values.foreach { ref =>
              ref ! SequenceCacheActor.Expire
            }
            Behaviors.same
          case ChildTerminated(questionId) =>
            handleMessage(configCache - questionId)
        }
    }
  }

  sealed trait Protocol extends ActorProtocol

  sealed trait Command extends Protocol

  final case class GetProposal(questionId: QuestionId, replyTo: ActorRef[IndexedProposal]) extends Command
  case object ExpireChildren extends Command
  final case class ChildTerminated(questionId: QuestionId) extends Command

  val name: String = "sequence-cache-manager"
  val SequenceCacheActorKey: ServiceKey[Protocol] = ServiceKey(name)
}
