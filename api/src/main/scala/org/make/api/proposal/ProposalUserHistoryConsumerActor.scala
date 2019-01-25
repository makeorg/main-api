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

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.MailJetTemplateConfigurationExtension
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor, TimeSettings}
import org.make.api.userhistory._

import scala.concurrent.Future

class ProposalUserHistoryConsumerActor(userHistoryCoordinator: ActorRef)
    extends KafkaConsumerActor[ProposalEventWrapper]
    with ActorEventBusServiceComponent
    with MailJetTemplateConfigurationExtension
    with ActorLogging {

  override protected lazy val kafkaTopic: String = ProposalProducerActor.topicKey
  override protected val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]
  override val groupId = "proposal-user-history"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: ProposalEventWrapper): Future[Unit] = {
    message.event.fold(ToProposalEvent) match {
      case event: ProposalProposed             => handleProposalProposed(event)
      case event: ProposalAccepted             => handleProposalAccepted(event)
      case event: ProposalRefused              => handleProposalRefused(event)
      case event: ProposalPostponed            => handleProposalPostponed(event)
      case event: ProposalLocked               => handleProposalLocked(event)
      case event: ProposalViewed               => doNothing(event)
      case event: ProposalUpdated              => doNothing(event)
      case event: ProposalVotesVerifiedUpdated => doNothing(event)
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
    }

  }

  def handleProposalProposed(event: ProposalProposed): Future[Unit] = {
    userHistoryCoordinator ! LogUserProposalEvent(
      userId = event.userId,
      requestContext = event.requestContext,
      action = UserAction(
        date = event.eventDate,
        actionType = LogUserProposalEvent.action,
        arguments = UserProposal(content = event.content, event.theme)
      )
    )
    Future.successful {}
  }

  def handleProposalLocked(event: ProposalLocked): Future[Unit] = {
    userHistoryCoordinator ! LogLockProposalEvent(
      userId = event.moderatorId,
      moderatorName = event.moderatorName,
      requestContext = event.requestContext,
      action = UserAction(date = event.eventDate, actionType = ProposalLocked.actionType, arguments = event)
    )
    Future.successful {}
  }

  def handleProposalAccepted(event: ProposalAccepted): Future[Unit] = {
    userHistoryCoordinator ! LogAcceptProposalEvent(
      userId = event.moderator,
      requestContext = event.requestContext,
      action = UserAction(date = event.eventDate, actionType = ProposalAccepted.actionType, arguments = event)
    )
    Future.successful {}
  }

  def handleProposalRefused(event: ProposalRefused): Future[Unit] = {
    userHistoryCoordinator ! LogRefuseProposalEvent(
      userId = event.moderator,
      requestContext = event.requestContext,
      action = UserAction(date = event.eventDate, actionType = ProposalRefused.actionType, arguments = event)
    )
    Future.successful {}
  }

  def handleProposalPostponed(event: ProposalPostponed): Future[Unit] = {
    userHistoryCoordinator ! LogPostponeProposalEvent(
      userId = event.moderator,
      requestContext = event.requestContext,
      action = UserAction(date = event.eventDate, actionType = ProposalPostponed.actionType, arguments = event)
    )
    Future.successful {}
  }
}

object ProposalUserHistoryConsumerActor {
  val name: String = "proposal-events-user-history-consumer"
  def props(userHistoryCoordinator: ActorRef): Props =
    Props(new ProposalUserHistoryConsumerActor(userHistoryCoordinator))
}
