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
import org.make.api.operation.{OperationOfQuestionServiceComponent, OperationServiceComponent}
import org.make.api.crmTemplates.CrmTemplatesServiceComponent
import org.make.api.organisation.OrganisationServiceComponent
import org.make.api.proposal.ProposalSupervisor.ProposalSupervisorDependencies
import org.make.api.question.QuestionServiceComponent
import org.make.api.semantic.SemanticComponent
import org.make.api.sequence.{SequenceConfigurationComponent, SequenceServiceComponent}
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.ShortenedNames
import org.make.api.technical.crm.SendMailPublisherServiceComponent
import org.make.api.user.UserServiceComponent
import org.make.api.{kafkaDispatcher, MakeBackoffSupervisor}

class ProposalSupervisor(userHistoryCoordinator: ActorRef,
                         sessionHistoryCoordinator: ActorRef,
                         dependencies: ProposalSupervisorDependencies)
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
        ProposalUserHistoryConsumerActor.props(userHistoryCoordinator).withDispatcher(kafkaDispatcher),
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
          .props(proposalCoordinatorService, dependencies)
          .withDispatcher(kafkaDispatcher),
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

  type ProposalSupervisorDependencies = CrmTemplatesServiceComponent
    with UserServiceComponent
    with OrganisationServiceComponent
    with ProposalSearchEngineComponent
    with ProposalIndexerServiceComponent
    with QuestionServiceComponent
    with SemanticComponent
    with SendMailPublisherServiceComponent
    with SequenceConfigurationComponent
    with OperationServiceComponent
    with OperationOfQuestionServiceComponent
    with CrmTemplatesServiceComponent
    with SequenceServiceComponent
    with TagServiceComponent

  val name: String = "proposal"
  def props(userHistoryCoordinator: ActorRef,
            sessionHistoryCoordinator: ActorRef,
            dependencies: ProposalSupervisorDependencies): Props =
    Props(new ProposalSupervisor(userHistoryCoordinator, sessionHistoryCoordinator, dependencies))
}
