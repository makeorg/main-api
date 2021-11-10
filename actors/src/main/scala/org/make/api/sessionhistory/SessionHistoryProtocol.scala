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

package org.make.api.sessionhistory

import akka.actor.typed.ActorRef
import org.make.api.sessionhistory.SessionHistoryResponse.Error.{Expired, LockError}
import org.make.api.sessionhistory.SessionHistoryResponse.{Envelope, LockResponse}
import org.make.api.technical.{ActorCommand, ActorProtocol}
import org.make.core.RequestContext
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.{ProposalId, QualificationKey}
import org.make.core.session.SessionId
import org.make.core.user.UserId

// Protocol

sealed trait SessionHistoryActorProtocol extends ActorProtocol

// Commands

sealed trait SessionHistoryCommand extends ActorCommand[SessionId] with SessionHistoryActorProtocol {
  def id: SessionId = sessionId
  def sessionId: SessionId
}

sealed trait SessionExpiredCommand[E >: Expired, T] extends SessionHistoryCommand {
  def replyTo: ActorRef[SessionHistoryResponse[E, T]]
}

final case class EventEnvelope[T <: LoggableHistoryEvent[_]](
  sessionId: SessionId,
  event: T,
  replyTo: ActorRef[SessionHistoryResponse[Expired, LogResult]]
) extends SessionExpiredCommand[Expired, LogResult]

final case class GetCurrentSession(
  sessionId: SessionId,
  newSessionId: SessionId,
  replyTo: ActorRef[Envelope[SessionId]]
) extends SessionHistoryCommand

final case class SessionVoteValuesCommand(
  sessionId: SessionId,
  proposalIds: Seq[ProposalId],
  replyTo: ActorRef[SessionHistoryResponse[Expired, Map[ProposalId, VoteAndQualifications]]]
) extends SessionExpiredCommand[Expired, Map[ProposalId, VoteAndQualifications]]

final case class SessionVotedProposalsPaginateCommand(
  sessionId: SessionId,
  proposalsIds: Option[Seq[ProposalId]] = None,
  limit: Int,
  skip: Int,
  replyTo: ActorRef[SessionHistoryResponse[Expired, Seq[ProposalId]]]
) extends SessionExpiredCommand[Expired, Seq[ProposalId]]

final case class UserConnected(
  sessionId: SessionId,
  userId: UserId,
  requestContext: RequestContext,
  replyTo: ActorRef[SessionHistoryResponse[Expired, LogResult]]
) extends SessionExpiredCommand[Expired, LogResult]

final case class LockProposalForVote(
  sessionId: SessionId,
  proposalId: ProposalId,
  replyTo: ActorRef[SessionHistoryResponse[LockError, LockResponse]]
) extends SessionExpiredCommand[LockError, LockResponse]

final case class LockProposalForQualification(
  sessionId: SessionId,
  proposalId: ProposalId,
  key: QualificationKey,
  replyTo: ActorRef[SessionHistoryResponse[LockError, LockResponse]]
) extends SessionExpiredCommand[LockError, LockResponse]

final case class ReleaseProposalForVote(
  sessionId: SessionId,
  proposalId: ProposalId,
  replyTo: ActorRef[SessionHistoryResponse[Expired, LockResponse]]
) extends SessionExpiredCommand[Expired, LockResponse]

final case class ReleaseProposalForQualification(
  sessionId: SessionId,
  proposalId: ProposalId,
  key: QualificationKey,
  replyTo: ActorRef[SessionHistoryResponse[Expired, LockResponse]]
) extends SessionExpiredCommand[Expired, LockResponse]

// Internal commands

final case class UserHistorySuccess[T](sessionId: SessionId, response: T, replyTo: ActorRef[Envelope[T]])
    extends SessionHistoryCommand

final case class UserHistoryFailure[T](
  sessionId: SessionId,
  response: T,
  exception: Throwable,
  replyTo: ActorRef[Envelope[T]]
) extends SessionHistoryCommand

final case class SessionClosed(
  sessionId: SessionId,
  userId: UserId,
  replyTo: ActorRef[SessionHistoryResponse[Expired, LogResult]]
) extends SessionHistoryCommand

// Test only

final case class GetState(sessionId: SessionId, replyTo: ActorRef[SessionHistoryResponse[Expired, State]])
    extends SessionHistoryCommand

final case class StopSessionActor(sessionId: SessionId, replyTo: ActorRef[Envelope[LogResult]])
    extends SessionHistoryCommand

// Responses

sealed trait SessionHistoryResponse[+E, +T] extends SessionHistoryActorProtocol

object SessionHistoryResponse {

  final case class Envelope[T](value: T) extends SessionHistoryResponse[Nothing, T]

  sealed trait LockResponse extends SessionHistoryResponse[Nothing, LockResponse]
  case object LockAcquired extends LockResponse
  case object LockReleased extends LockResponse

  sealed trait Error[E <: Error[E]] extends SessionHistoryResponse[E, Nothing]

  object Error {

    sealed trait LockError

    sealed trait Expired extends Error[Expired] with LockError
    final case class ExpiredSession(newSessionId: SessionId) extends Expired

    final case class LockAlreadyAcquired(message: String) extends Error[LockAlreadyAcquired] with LockError
  }
}

// Common to SessionHistory and UserHistory
sealed trait LogResult

case object Ack extends LogResult
case object Failed extends LogResult
