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
import org.make.api.MakeBackoffSupervisor
import org.make.api.operation.OperationService
import org.make.api.semantic.SemanticService
import org.make.api.sequence.SequenceService
import org.make.api.tag.TagService
import org.make.api.technical.ShortenedNames
import org.make.api.user.UserService

class ProposalSupervisor(userService: UserService,
                         userHistoryCoordinator: ActorRef,
                         sessionHistoryCoordinator: ActorRef,
                         tagService: TagService,
                         sequenceService: SequenceService,
                         operationService: OperationService,
                         semanticService: SemanticService)
    extends Actor
    with ActorLogging
    with ShortenedNames
    with DefaultProposalCoordinatorServiceComponent
    with ProposalCoordinatorComponent {

  override val proposalCoordinator: ActorRef =
    context.watch(
      context.actorOf(
        ProposalCoordinator
          .props(sessionHistoryActor = sessionHistoryCoordinator),
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
        ProposalUserHistoryConsumerActor.props(userHistoryCoordinator),
        ProposalUserHistoryConsumerActor.name
      )
      context.actorOf(props, name)
    }

    context.watch {
      val (props, name) = MakeBackoffSupervisor.propsAndName(
        ProposalEmailConsumerActor.props(userService, this.proposalCoordinatorService, operationService),
        ProposalEmailConsumerActor.name
      )
      context.actorOf(props, name)
    }
    context.watch {
      val (props, name) = MakeBackoffSupervisor.propsAndName(
        ProposalConsumerActor
          .props(
            proposalCoordinatorService,
            userService,
            tagService,
            sequenceService,
            operationService,
            semanticService
          ),
        ProposalConsumerActor.name
      )
      context.actorOf(props, name)
    }
  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }

}

object ProposalSupervisor {

  val name: String = "proposal"
  def props(userService: UserService,
            userHistoryCoordinator: ActorRef,
            sessionHistoryCoordinator: ActorRef,
            tagService: TagService,
            sequenceService: SequenceService,
            operationService: OperationService,
            semanticService: SemanticService): Props =
    Props(
      new ProposalSupervisor(
        userService,
        userHistoryCoordinator,
        sessionHistoryCoordinator,
        tagService,
        sequenceService,
        operationService,
        semanticService
      )
    )
}
