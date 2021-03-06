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
import org.make.api.technical.KafkaConsumerBehavior.Protocol
import org.make.api.technical.{KafkaConsumerBehavior, TimeSettings}
import org.make.api.userhistory._

import scala.concurrent.Future

class ProposalUserHistoryConsumerBehavior(userHistoryCoordinatorService: UserHistoryCoordinatorService)
    extends KafkaConsumerBehavior[ProposalEventWrapper]
    with Logging {

  override protected val topicKey: String = ProposalKafkaProducerBehavior.topicKey
  override val groupId = "proposal-user-history"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: ProposalEventWrapper): Future[_] = {
    message.event match {
      case event: ProposalProposed             => handleProposalProposed(event)
      case event: ProposalAccepted             => handleProposalAccepted(event)
      case event: ProposalRefused              => handleProposalRefused(event)
      case event: ProposalPostponed            => handleProposalPostponed(event)
      case event: ProposalLocked               => handleProposalLocked(event)
      case event: ProposalViewed               => doNothing(event)
      case event: ProposalUpdated              => doNothing(event)
      case event: ProposalVotesVerifiedUpdated => doNothing(event)
      case event: ProposalVotesUpdated         => doNothing(event)
      case event: ReindexProposal              => doNothing(event)
      case event: ProposalVoted                => doNothing(event)
      case event: ProposalUnvoted              => doNothing(event)
      case event: ProposalQualified            => doNothing(event)
      case event: ProposalUnqualified          => doNothing(event)
      case event: SimilarProposalsAdded        => doNothing(event)
      case event: ProposalPatched              => doNothing(event)
      case event: ProposalAddedToOperation     => doNothing(event)
      case event: ProposalRemovedFromOperation => doNothing(event)
      case event: ProposalAnonymized           => doNothing(event)
      case event: ProposalKeywordsSet          => doNothing(event)
    }

  }

  def handleProposalProposed(event: ProposalProposed): Future[Unit] = {
    userHistoryCoordinatorService.logHistory(
      LogUserProposalEvent(
        userId = event.userId,
        requestContext = event.requestContext,
        action = UserAction(
          date = event.eventDate,
          actionType = LogUserProposalEvent.action,
          arguments = UserProposal(content = event.content, event.theme)
        )
      )
    )
    Future.unit
  }

  def handleProposalLocked(event: ProposalLocked): Future[Unit] = {
    userHistoryCoordinatorService.logHistory(
      LogLockProposalEvent(
        userId = event.moderatorId,
        moderatorName = event.moderatorName,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = ProposalLocked.actionType, arguments = event)
      )
    )
    Future.unit
  }

  def handleProposalAccepted(event: ProposalAccepted): Future[Unit] = {
    userHistoryCoordinatorService.logHistory(
      LogAcceptProposalEvent(
        userId = event.moderator,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = ProposalAccepted.actionType, arguments = event)
      )
    )
    Future.unit
  }

  def handleProposalRefused(event: ProposalRefused): Future[Unit] = {
    userHistoryCoordinatorService.logHistory(
      LogRefuseProposalEvent(
        userId = event.moderator,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = ProposalRefused.actionType, arguments = event)
      )
    )
    Future.unit
  }

  def handleProposalPostponed(event: ProposalPostponed): Future[Unit] = {
    userHistoryCoordinatorService.logHistory(
      LogPostponeProposalEvent(
        userId = event.moderator,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = ProposalPostponed.actionType, arguments = event)
      )
    )
    Future.unit
  }
}

object ProposalUserHistoryConsumerBehavior {
  val name: String = "proposal-user-history-consumer"

  def apply(userHistoryCoordinatorService: UserHistoryCoordinatorService): Behavior[Protocol] =
    new ProposalUserHistoryConsumerBehavior(userHistoryCoordinatorService).createBehavior(name)
}
