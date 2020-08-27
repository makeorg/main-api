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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.make.api.proposal.ProposalSupervisor.ProposalSupervisorDependencies
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.ShortenedNames
import org.make.api.technical.crm.SendMailPublisherServiceComponent
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent
import org.make.api.{kafkaDispatcher, MakeBackoffSupervisor}

import scala.concurrent.duration.FiniteDuration

class ProposalSupervisor(dependencies: ProposalSupervisorDependencies, lockDuration: FiniteDuration)
    extends Actor
    with ActorLogging
    with ShortenedNames
    with DefaultProposalCoordinatorServiceComponent
    with ProposalCoordinatorComponent {

  override val proposalCoordinator: ActorRef =
    context.watch(
      context.actorOf(
        ProposalCoordinator
          .props(
            sessionHistoryCoordinatorService = dependencies.sessionHistoryCoordinatorService,
            lockDuration = lockDuration
          ),
        ProposalCoordinator.name
      )
    )

  override def preStart(): Unit = {
    context.watch {
      val (props, name) = MakeBackoffSupervisor.propsAndName(ProposalProducerActor.props, ProposalProducerActor.name)
      context.actorOf(props, name)
    }

    context.watch {
      val (props, name) = MakeBackoffSupervisor.propsAndName(
        ProposalUserHistoryConsumerActor
          .props(dependencies.userHistoryCoordinatorService)
          .withDispatcher(kafkaDispatcher),
        ProposalUserHistoryConsumerActor.name
      )
      context.actorOf(props, name)
    }

    context.watch {
      val (props, name) = MakeBackoffSupervisor.propsAndName(
        ProposalEmailConsumerActor
          .props(dependencies.sendMailPublisherService)
          .withDispatcher(kafkaDispatcher),
        ProposalEmailConsumerActor.name
      )
      context.actorOf(props, name)
    }
    context.watch {
      val (props, name) = MakeBackoffSupervisor.propsAndName(
        ProposalConsumerActor
          .props(dependencies.proposalIndexerService)
          .withDispatcher(kafkaDispatcher),
        ProposalConsumerActor.name
      )
      context.actorOf(props, name)
    }

    ()
  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }

}

object ProposalSupervisor {

  type ProposalSupervisorDependencies =
    ProposalIndexerServiceComponent
      with SendMailPublisherServiceComponent
      with UserHistoryCoordinatorServiceComponent
      with SessionHistoryCoordinatorServiceComponent

  val name: String = "proposal"
  def props(dependencies: ProposalSupervisorDependencies, lockDuration: FiniteDuration): Props =
    Props(new ProposalSupervisor(dependencies, lockDuration))
}
