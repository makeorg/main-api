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
import grizzled.slf4j.Logging
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.technical.KafkaConsumerBehavior

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ProposalConsumerBehavior(proposalIndexerService: ProposalIndexerService)
    extends KafkaConsumerBehavior[ProposalEventWrapper]
    with Logging {

  override protected val topicKey: String = ProposalKafkaProducerBehavior.topicKey
  override val groupId = "proposal-consumer"

  implicit val timeout: Timeout = Timeout(5.seconds)

  override def handleMessage(message: ProposalEventWrapper): Future[_] = {
    message.event match {
      case event: ProposalViewed               => doNothing(event)
      case event: ReindexProposal              => onCreateOrUpdate(event)
      case event: ProposalUpdated              => onCreateOrUpdate(event)
      case event: ProposalVotesVerifiedUpdated => onCreateOrUpdate(event)
      case event: ProposalVotesUpdated         => onCreateOrUpdate(event)
      case event: ProposalProposed             => onCreateOrUpdate(event)
      case event: ProposalAccepted             => onCreateOrUpdate(event)
      case event: ProposalRefused              => onCreateOrUpdate(event)
      case event: ProposalPostponed            => onCreateOrUpdate(event)
      case event: ProposalVoted                => onCreateOrUpdate(event)
      case event: ProposalUnvoted              => onCreateOrUpdate(event)
      case event: ProposalQualified            => onCreateOrUpdate(event)
      case event: ProposalUnqualified          => onCreateOrUpdate(event)
      case event: ProposalPatched              => onCreateOrUpdate(event)
      case event: ProposalAddedToOperation     => onCreateOrUpdate(event)
      case event: ProposalRemovedFromOperation => onCreateOrUpdate(event)
      case event: ProposalLocked               => doNothing(event)
      case event: ProposalAnonymized           => onCreateOrUpdate(event)
      case event: SimilarProposalsAdded        => doNothing(event)
      case event: ProposalKeywordsSet          => onCreateOrUpdate(event)
    }

  }

  def onCreateOrUpdate(event: ProposalEvent): Future[Unit] = {
    proposalIndexerService.offer(event.id).recover {
      case ex =>
        error(s"Error presenting proposal to indexation queue: ${ex.getMessage}")
    }
  }

}

object ProposalConsumerBehavior {
  def apply(proposalIndexerService: ProposalIndexerService): Behavior[KafkaConsumerBehavior.Protocol] = {
    new ProposalConsumerBehavior(proposalIndexerService).createBehavior(name)
  }
  val name: String = "proposal-consumer"
}
