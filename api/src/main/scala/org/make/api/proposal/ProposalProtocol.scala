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

import java.time.ZonedDateTime

import akka.actor.typed.ActorRef
import org.make.api.proposal.ProposalActorResponse.Envelope
import org.make.api.proposal.ProposalActorResponse.Error._
import org.make.api.technical.{ActorCommand, ActorProtocol}
import org.make.core.RequestContext
import org.make.core.history.HistoryActions.{VoteAndQualifications, VoteTrust}
import org.make.core.proposal._
import org.make.core.question.Question
import org.make.core.tag.TagId
import org.make.core.user.{User, UserId}

sealed trait ProposalActorProtocol extends ActorProtocol

sealed trait ProposalCommand extends ActorCommand[ProposalId] with ProposalActorProtocol {
  def id: ProposalId = proposalId
  def proposalId: ProposalId
  def requestContext: RequestContext
}

final case class ProposeCommand(
  proposalId: ProposalId,
  requestContext: RequestContext,
  user: User,
  createdAt: ZonedDateTime,
  content: String,
  question: Question,
  initialProposal: Boolean,
  replyTo: ActorRef[Envelope[ProposalId]]
) extends ProposalCommand

final case class UpdateProposalCommand(
  moderator: UserId,
  proposalId: ProposalId,
  requestContext: RequestContext,
  updatedAt: ZonedDateTime,
  newContent: Option[String],
  tags: Seq[TagId],
  question: Question,
  replyTo: ActorRef[ProposalActorResponse[ModificationError, Proposal]]
) extends ProposalCommand

final case class UpdateProposalVotesCommand(
  moderator: UserId,
  proposalId: ProposalId,
  requestContext: RequestContext,
  updatedAt: ZonedDateTime,
  votes: Seq[UpdateVoteRequest],
  replyTo: ActorRef[ProposalActorResponse[ModificationError, Proposal]]
) extends ProposalCommand

final case class ViewProposalCommand(
  proposalId: ProposalId,
  requestContext: RequestContext,
  replyTo: ActorRef[ProposalActorResponse[ProposalNotFound, Proposal]]
) extends ProposalCommand

final case class GetProposal(
  proposalId: ProposalId,
  requestContext: RequestContext,
  replyTo: ActorRef[ProposalActorResponse[ProposalNotFound, Proposal]]
) extends ProposalCommand

final case class Stop(proposalId: ProposalId, requestContext: RequestContext, replyTo: ActorRef[Envelope[Unit]])
    extends ProposalCommand

final case class AcceptProposalCommand(
  moderator: UserId,
  proposalId: ProposalId,
  requestContext: RequestContext,
  sendNotificationEmail: Boolean,
  newContent: Option[String],
  question: Question,
  tags: Seq[TagId],
  replyTo: ActorRef[ProposalActorResponse[ModificationError, Proposal]]
) extends ProposalCommand

final case class RefuseProposalCommand(
  moderator: UserId,
  proposalId: ProposalId,
  requestContext: RequestContext,
  sendNotificationEmail: Boolean,
  refusalReason: Option[String],
  replyTo: ActorRef[ProposalActorResponse[ModificationError, Proposal]]
) extends ProposalCommand

final case class PostponeProposalCommand(
  moderator: UserId,
  proposalId: ProposalId,
  requestContext: RequestContext,
  replyTo: ActorRef[ProposalActorResponse[ModificationError, Proposal]]
) extends ProposalCommand

final case class VoteProposalCommand(
  proposalId: ProposalId,
  maybeUserId: Option[UserId],
  requestContext: RequestContext,
  voteKey: VoteKey,
  maybeOrganisationId: Option[UserId],
  vote: Option[VoteAndQualifications],
  voteTrust: VoteTrust,
  replyTo: ActorRef[ProposalActorResponse[VoteError, Vote]]
) extends ProposalCommand

final case class UnvoteProposalCommand(
  proposalId: ProposalId,
  maybeUserId: Option[UserId],
  requestContext: RequestContext,
  voteKey: VoteKey,
  maybeOrganisationId: Option[UserId],
  vote: Option[VoteAndQualifications],
  voteTrust: VoteTrust,
  replyTo: ActorRef[ProposalActorResponse[VoteError, Vote]]
) extends ProposalCommand

final case class QualifyVoteCommand(
  proposalId: ProposalId,
  maybeUserId: Option[UserId],
  requestContext: RequestContext,
  voteKey: VoteKey,
  qualificationKey: QualificationKey,
  vote: Option[VoteAndQualifications],
  voteTrust: VoteTrust,
  replyTo: ActorRef[ProposalActorResponse[QualificationError, Qualification]]
) extends ProposalCommand

final case class UnqualifyVoteCommand(
  proposalId: ProposalId,
  maybeUserId: Option[UserId],
  requestContext: RequestContext,
  voteKey: VoteKey,
  qualificationKey: QualificationKey,
  vote: Option[VoteAndQualifications],
  voteTrust: VoteTrust,
  replyTo: ActorRef[ProposalActorResponse[QualificationError, Qualification]]
) extends ProposalCommand

final case class LockProposalCommand(
  proposalId: ProposalId,
  moderatorId: UserId,
  moderatorName: Option[String],
  requestContext: RequestContext,
  replyTo: ActorRef[ProposalActorResponse[LockError, UserId]]
) extends ProposalCommand

final case class PatchProposalCommand(
  proposalId: ProposalId,
  userId: UserId,
  changes: PatchProposalRequest,
  requestContext: RequestContext,
  replyTo: ActorRef[ProposalActorResponse[ProposalNotFound, Proposal]]
) extends ProposalCommand

final case class SetKeywordsCommand(
  proposalId: ProposalId,
  keywords: Seq[ProposalKeyword],
  requestContext: RequestContext,
  replyTo: ActorRef[ProposalActorResponse[ProposalNotFound, Proposal]]
) extends ProposalCommand

// Responses

sealed trait ProposalActorResponse[+E, +T] extends ProposalActorProtocol {
  def flatMap[F >: E, U](f: T => ProposalActorResponse[F, U]): ProposalActorResponse[F, U]
}

object ProposalActorResponse {

  final case class Envelope[T](value: T) extends ProposalActorResponse[Nothing, T] {
    override def flatMap[E, U](f: T => ProposalActorResponse[E, U]): ProposalActorResponse[E, U] = f(value)
  }

  sealed trait Error[E <: Error[E]] extends ProposalActorResponse[E, Nothing] { self: E =>
    override def flatMap[F >: E, U](f: Nothing => ProposalActorResponse[F, U]): ProposalActorResponse[E, Nothing] = self
  }

  object Error {

    sealed trait LockError
    sealed trait ModificationError
    sealed trait VoteError extends QualificationError
    sealed trait QualificationError

    sealed trait ProposalNotFound extends Error[ProposalNotFound] with LockError with ModificationError with VoteError
    case object ProposalNotFound extends ProposalNotFound

    sealed trait VoteNotFound extends Error[VoteNotFound] with VoteError
    case object VoteNotFound extends VoteNotFound

    sealed trait QualificationNotFound extends Error[QualificationNotFound] with QualificationError
    case object QualificationNotFound extends QualificationNotFound

    final case class AlreadyLockedBy(moderatorName: String) extends Error[AlreadyLockedBy] with LockError

    final case class InvalidStateError(message: String) extends Error[InvalidStateError] with ModificationError

    final case class HistoryError(error: Throwable) extends Error[HistoryError] with VoteError

  }
}
