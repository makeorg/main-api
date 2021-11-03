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

package org.make.api.proposal

import akka.actor.typed
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.make.api.kafka.kafkaDispatcher
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.{ActorSystemHelper, IdGeneratorComponent}
import org.make.api.technical.crm.SendMailPublisherServiceComponent
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent

import scala.concurrent.duration.FiniteDuration

object ProposalSupervisor {

  type ProposalSupervisorDependencies =
    ProposalIndexerServiceComponent
      with SendMailPublisherServiceComponent
      with UserHistoryCoordinatorServiceComponent
      with SessionHistoryCoordinatorServiceComponent
      with IdGeneratorComponent

  val name: String = "proposal"
  def apply(dependencies: ProposalSupervisorDependencies, lockDuration: FiniteDuration): Behavior[Nothing] = {

    Behaviors.setup[Nothing] { context =>
      val proposalCoordinator: ActorRef[ProposalCommand] = ProposalCoordinator(
        system = context.system,
        sessionHistoryCoordinatorService = dependencies.sessionHistoryCoordinatorService,
        lockDuration = lockDuration,
        idGenerator = dependencies.idGenerator
      )
      context.watch(proposalCoordinator)
      context.system.receptionist ! Receptionist.Register(ProposalCoordinator.Key, proposalCoordinator)

      context.watch(
        context.spawn(
          ActorSystemHelper.superviseWithBackoff(ProposalKafkaProducerBehavior()),
          ProposalKafkaProducerBehavior.name,
          typed.Props.empty.withDispatcherFromConfig(kafkaDispatcher)
        )
      )

      context.watch(
        context.spawn(
          ActorSystemHelper
            .superviseWithBackoff(ProposalUserHistoryConsumerBehavior(dependencies.userHistoryCoordinatorService)),
          ProposalUserHistoryConsumerBehavior.name,
          typed.Props.empty.withDispatcherFromConfig(kafkaDispatcher)
        )
      )

      context.watch(
        context.spawn(
          ActorSystemHelper.superviseWithBackoff(ProposalEmailConsumerActor(dependencies.sendMailPublisherService)),
          ProposalEmailConsumerActor.name,
          typed.Props.empty.withDispatcherFromConfig(kafkaDispatcher)
        )
      )

      context.watch(
        context.spawn(
          ActorSystemHelper.superviseWithBackoff(ProposalConsumerBehavior(dependencies.proposalIndexerService)),
          ProposalConsumerBehavior.name,
          akka.actor.typed.Props.empty.withDispatcherFromConfig(kafkaDispatcher)
        )
      )

      Behaviors.empty
    }
  }
}
