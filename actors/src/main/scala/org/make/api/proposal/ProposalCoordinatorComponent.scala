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
import akka.util.Timeout
import grizzled.slf4j.Logging
import org.make.api.technical.{ActorSystemComponent, TimeSettings}
import org.make.api.technical.BetterLoggingActors._
import org.make.core.history.HistoryActions.{VoteAndQualifications, VoteTrust}
import org.make.core.proposal._
import org.make.core.question.Question
import org.make.core.tag.TagId
import org.make.core.user.{User, UserId}
import org.make.core.{RequestContext, ValidationError, ValidationFailedError}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ProposalCoordinatorComponent {
  def proposalCoordinator: ActorRef[ProposalCommand]
}

trait ProposalCoordinatorService {

  /**
    * Retrieve a Proposal without logging it
    *
    * @param proposalId the proposal to retrieve
    * @return the given proposal if exists
    */
  def getProposal(proposalId: ProposalId): Future[Option[Proposal]]

  /**
    * Retrieves a proposal by id and log the fact that it was seen
    *
    * @param proposalId the proposal viewed by the user
    * @param requestContext the context of the request
    * @return the proposal as viewed by the user
    */
  def viewProposal(proposalId: ProposalId, requestContext: RequestContext): Future[Option[Proposal]]
  def propose(
    proposalId: ProposalId,
    requestContext: RequestContext,
    user: User,
    createdAt: ZonedDateTime,
    content: String,
    question: Question,
    initialProposal: Boolean
  ): Future[ProposalId]
  def update(
    moderator: UserId,
    proposalId: ProposalId,
    requestContext: RequestContext,
    updatedAt: ZonedDateTime,
    newContent: Option[String],
    tags: Seq[TagId],
    question: Question
  ): Future[Option[Proposal]]
  def updateVotes(
    moderator: UserId,
    proposalId: ProposalId,
    requestContext: RequestContext,
    updatedAt: ZonedDateTime,
    votes: Seq[UpdateVoteRequest]
  ): Future[Option[Proposal]]
  def accept(
    moderator: UserId,
    proposalId: ProposalId,
    requestContext: RequestContext,
    sendNotificationEmail: Boolean,
    newContent: Option[String],
    question: Question,
    tags: Seq[TagId]
  ): Future[Option[Proposal]]
  def refuse(
    moderator: UserId,
    proposalId: ProposalId,
    requestContext: RequestContext,
    sendNotificationEmail: Boolean,
    refusalReason: Option[String]
  ): Future[Option[Proposal]]
  def postpone(moderator: UserId, proposalId: ProposalId, requestContext: RequestContext): Future[Option[Proposal]]
  def vote(
    proposalId: ProposalId,
    maybeUserId: Option[UserId],
    requestContext: RequestContext,
    voteKey: VoteKey,
    maybeOrganisationId: Option[UserId],
    vote: Option[VoteAndQualifications],
    voteTrust: VoteTrust
  ): Future[Option[Vote]]
  def unvote(
    proposalId: ProposalId,
    maybeUserId: Option[UserId],
    requestContext: RequestContext,
    voteKey: VoteKey,
    maybeOrganisationId: Option[UserId],
    vote: Option[VoteAndQualifications],
    voteTrust: VoteTrust
  ): Future[Option[Vote]]
  def qualification(
    proposalId: ProposalId,
    maybeUserId: Option[UserId],
    requestContext: RequestContext,
    voteKey: VoteKey,
    qualificationKey: QualificationKey,
    vote: Option[VoteAndQualifications],
    voteTrust: VoteTrust
  ): Future[Option[Qualification]]
  def unqualification(
    proposalId: ProposalId,
    maybeUserId: Option[UserId],
    requestContext: RequestContext,
    voteKey: VoteKey,
    qualificationKey: QualificationKey,
    vote: Option[VoteAndQualifications],
    voteTrust: VoteTrust
  ): Future[Option[Qualification]]
  def lock(
    proposalId: ProposalId,
    moderatorId: UserId,
    moderatorName: Option[String],
    requestContext: RequestContext
  ): Future[Option[UserId]]
  def patch(
    proposalId: ProposalId,
    userId: UserId,
    changes: PatchProposalRequest,
    requestContext: RequestContext
  ): Future[Option[Proposal]]
  def setKeywords(
    proposalId: ProposalId,
    keywords: Seq[ProposalKeyword],
    requestContext: RequestContext
  ): Future[Option[Proposal]]
}

trait ProposalCoordinatorServiceComponent {
  def proposalCoordinatorService: ProposalCoordinatorService
}

trait DefaultProposalCoordinatorServiceComponent extends ProposalCoordinatorServiceComponent with Logging {
  self: ActorSystemComponent with ProposalCoordinatorComponent =>

  override lazy val proposalCoordinatorService: ProposalCoordinatorService = new DefaultProposalCoordinatorService

  class DefaultProposalCoordinatorService extends ProposalCoordinatorService {

    implicit val timeout: Timeout = TimeSettings.defaultTimeout

    override def getProposal(proposalId: ProposalId): Future[Option[Proposal]] = {
      (proposalCoordinator ?? (GetProposal(proposalId, RequestContext.empty, _))).map(asOption)
    }

    override def viewProposal(proposalId: ProposalId, requestContext: RequestContext): Future[Option[Proposal]] = {
      (proposalCoordinator ?? (ViewProposalCommand(proposalId, requestContext, _))).map(asOption)
    }

    override def propose(
      proposalId: ProposalId,
      requestContext: RequestContext,
      user: User,
      createdAt: ZonedDateTime,
      content: String,
      question: Question,
      initialProposal: Boolean
    ): Future[ProposalId] = {
      (proposalCoordinator ?? (ProposeCommand(
        proposalId,
        requestContext,
        user,
        createdAt,
        content,
        question,
        initialProposal,
        _
      ))).map(_.value)
    }

    override def update(
      moderator: UserId,
      proposalId: ProposalId,
      requestContext: RequestContext,
      updatedAt: ZonedDateTime,
      newContent: Option[String],
      tags: Seq[TagId],
      question: Question
    ): Future[Option[Proposal]] = {
      (proposalCoordinator ?? (UpdateProposalCommand(
        moderator,
        proposalId,
        requestContext,
        updatedAt,
        newContent,
        tags,
        question,
        _
      ))).flatMap(shapeResponse)
    }

    override def updateVotes(
      moderator: UserId,
      proposalId: ProposalId,
      requestContext: RequestContext,
      updatedAt: ZonedDateTime,
      votes: Seq[UpdateVoteRequest]
    ): Future[Option[Proposal]] = {
      (proposalCoordinator ?? (UpdateProposalVotesCommand(moderator, proposalId, requestContext, updatedAt, votes, _)))
        .flatMap(shapeResponse)
    }

    override def accept(
      moderator: UserId,
      proposalId: ProposalId,
      requestContext: RequestContext,
      sendNotificationEmail: Boolean,
      newContent: Option[String],
      question: Question,
      tags: Seq[TagId]
    ): Future[Option[Proposal]] = {
      (proposalCoordinator ?? (AcceptProposalCommand(
        moderator,
        proposalId,
        requestContext,
        sendNotificationEmail,
        newContent,
        question,
        tags,
        _
      ))).flatMap(shapeResponse)
    }

    override def refuse(
      moderator: UserId,
      proposalId: ProposalId,
      requestContext: RequestContext,
      sendNotificationEmail: Boolean,
      refusalReason: Option[String]
    ): Future[Option[Proposal]] = {
      (proposalCoordinator ?? (RefuseProposalCommand(
        moderator,
        proposalId,
        requestContext,
        sendNotificationEmail,
        refusalReason,
        _
      ))).flatMap(shapeResponse)
    }

    override def postpone(
      moderator: UserId,
      proposalId: ProposalId,
      requestContext: RequestContext
    ): Future[Option[Proposal]] = {
      (proposalCoordinator ?? (PostponeProposalCommand(moderator, proposalId, requestContext, _)))
        .flatMap(shapeResponse)
    }

    override def vote(
      proposalId: ProposalId,
      maybeUserId: Option[UserId],
      requestContext: RequestContext,
      voteKey: VoteKey,
      maybeOrganisationId: Option[UserId],
      vote: Option[VoteAndQualifications],
      voteTrust: VoteTrust
    ): Future[Option[Vote]] = {
      (proposalCoordinator ?? (VoteProposalCommand(
        proposalId,
        maybeUserId,
        requestContext,
        voteKey,
        maybeOrganisationId,
        vote,
        voteTrust,
        _
      ))).flatMap(shapeResponse)
    }

    override def unvote(
      proposalId: ProposalId,
      maybeUserId: Option[UserId],
      requestContext: RequestContext,
      voteKey: VoteKey,
      maybeOrganisationId: Option[UserId],
      vote: Option[VoteAndQualifications],
      voteTrust: VoteTrust
    ): Future[Option[Vote]] = {
      (proposalCoordinator ?? (UnvoteProposalCommand(
        proposalId,
        maybeUserId,
        requestContext,
        voteKey,
        maybeOrganisationId,
        vote,
        voteTrust,
        _
      ))).flatMap(shapeResponse)
    }

    override def qualification(
      proposalId: ProposalId,
      maybeUserId: Option[UserId],
      requestContext: RequestContext,
      voteKey: VoteKey,
      qualificationKey: QualificationKey,
      vote: Option[VoteAndQualifications],
      voteTrust: VoteTrust
    ): Future[Option[Qualification]] = {
      (proposalCoordinator ?? (QualifyVoteCommand(
        proposalId,
        maybeUserId,
        requestContext,
        voteKey,
        qualificationKey,
        vote,
        voteTrust,
        _
      ))).flatMap(shapeResponse)
    }

    override def unqualification(
      proposalId: ProposalId,
      maybeUserId: Option[UserId],
      requestContext: RequestContext,
      voteKey: VoteKey,
      qualificationKey: QualificationKey,
      vote: Option[VoteAndQualifications],
      voteTrust: VoteTrust
    ): Future[Option[Qualification]] = {
      (proposalCoordinator ?? (UnqualifyVoteCommand(
        proposalId,
        maybeUserId,
        requestContext,
        voteKey,
        qualificationKey,
        vote,
        voteTrust,
        _
      ))).flatMap(shapeResponse)
    }

    override def lock(
      proposalId: ProposalId,
      moderatorId: UserId,
      moderatorName: Option[String],
      requestContext: RequestContext
    ): Future[Option[UserId]] = {
      (proposalCoordinator ?? (LockProposalCommand(proposalId, moderatorId, moderatorName, requestContext, _)))
        .flatMap(shapeResponse)
    }

    override def patch(
      proposalId: ProposalId,
      userId: UserId,
      changes: PatchProposalRequest,
      requestContext: RequestContext
    ): Future[Option[Proposal]] = {
      (proposalCoordinator ?? (PatchProposalCommand(proposalId, userId, changes, requestContext, _))).map(asOption)
    }

    override def setKeywords(
      proposalId: ProposalId,
      keywords: Seq[ProposalKeyword],
      requestContext: RequestContext
    ): Future[Option[Proposal]] = {
      (proposalCoordinator ?? (SetKeywordsCommand(proposalId, keywords, requestContext, _))).map(asOption)
    }

    private def asOption[E, T](response: ProposalActorResponse[E, T]): Option[T] = response match {
      case ProposalActorResponse.Envelope(value) => Some(value)
      case _: ProposalActorResponse.Error[E]     => None
    }

    private def shapeResponse[E, T](response: ProposalActorResponse[E, T]): Future[Option[T]] = response match {
      case ProposalActorResponse.Error.AlreadyLockedBy(moderatorName) =>
        Future.failed(
          ValidationFailedError(errors = Seq(ValidationError("moderatorName", "already_locked", Some(moderatorName))))
        )
      case ProposalActorResponse.Error.HistoryError(error) => Future.failed(error)
      case ProposalActorResponse.Error.InvalidStateError(message) =>
        Future.failed(ValidationFailedError(errors = Seq(ValidationError("status", "invalid_state", Some(message)))))
      case other => Future.successful(asOption(other))
    }
  }
}
