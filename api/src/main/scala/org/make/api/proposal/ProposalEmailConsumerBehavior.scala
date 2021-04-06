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

import akka.actor.typed.Behavior
import akka.util.Timeout
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.technical.crm.SendMailPublisherService
import org.make.api.technical.{KafkaConsumerBehavior, TimeSettings}

import scala.concurrent.Future

class ProposalEmailConsumerBehavior(sendMailPublisherService: SendMailPublisherService)
    extends KafkaConsumerBehavior[ProposalEventWrapper] {

  override protected val topicKey: String = ProposalKafkaProducerBehavior.topicKey
  override val groupId = "proposal-email"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: ProposalEventWrapper): Future[Unit] = {
    message.event match {
      case event: ProposalAccepted             => handleProposalAccepted(event)
      case event: ProposalRefused              => handleProposalRefused(event)
      case event: ProposalPostponed            => doNothing(event)
      case event: ProposalViewed               => doNothing(event)
      case event: ProposalUpdated              => doNothing(event)
      case event: ProposalVotesVerifiedUpdated => doNothing(event)
      case event: ProposalVotesUpdated         => doNothing(event)
      case event: ReindexProposal              => doNothing(event)
      case event: ProposalProposed             => doNothing(event)
      case event: ProposalVoted                => doNothing(event)
      case event: ProposalUnvoted              => doNothing(event)
      case event: ProposalQualified            => doNothing(event)
      case event: ProposalUnqualified          => doNothing(event)
      case event: SimilarProposalsAdded        => doNothing(event)
      case event: ProposalLocked               => doNothing(event)
      case event: ProposalPatched              => doNothing(event)
      case event: ProposalAddedToOperation     => doNothing(event)
      case event: ProposalRemovedFromOperation => doNothing(event)
      case event: ProposalAnonymized           => doNothing(event)
      case event: ProposalKeywordsSet          => doNothing(event)
    }
  }

  def handleProposalAccepted(event: ProposalAccepted): Future[Unit] = {
    if (event.sendValidationEmail) {
      sendMailPublisherService.publishAcceptProposal(event.id)
    } else {
      Future.unit
    }
  }

  def handleProposalRefused(event: ProposalRefused): Future[Unit] = {
    if (event.sendRefuseEmail) {
      sendMailPublisherService.publishRefuseProposal(event.id)
    } else {
      Future.unit
    }
  }
}

object ProposalEmailConsumerActor {
  val name: String = "proposal-emails-consumer"
  def apply(sendMailPublisherService: SendMailPublisherService): Behavior[KafkaConsumerBehavior.Protocol] =
    new ProposalEmailConsumerBehavior(sendMailPublisherService).createBehavior(name)
}
