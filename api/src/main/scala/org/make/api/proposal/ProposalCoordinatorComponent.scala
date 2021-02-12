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

import akka.actor.ActorRef
import akka.pattern.{ask, AskTimeoutException}
import akka.util.Timeout
import grizzled.slf4j.Logging
import org.make.api.technical.{ActorTimeoutException, TimeSettings}
import org.make.core.proposal._
import org.make.core.user.UserId
import org.make.core.{RequestContext, ValidationError, ValidationFailedError}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ProposalCoordinatorComponent {
  def proposalCoordinator: ActorRef
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
  def propose(command: ProposeCommand): Future[ProposalId]
  def update(command: UpdateProposalCommand): Future[Option[Proposal]]
  def updateVotes(command: UpdateProposalVotesCommand): Future[Option[Proposal]]
  def accept(command: AcceptProposalCommand): Future[Option[Proposal]]
  def refuse(command: RefuseProposalCommand): Future[Option[Proposal]]
  def postpone(command: PostponeProposalCommand): Future[Option[Proposal]]
  def vote(command: VoteProposalCommand): Future[Option[Vote]]
  def unvote(command: UnvoteProposalCommand): Future[Option[Vote]]
  def qualification(command: QualifyVoteCommand): Future[Option[Qualification]]
  def unqualification(command: UnqualifyVoteCommand): Future[Option[Qualification]]
  def lock(command: LockProposalCommand): Future[Option[UserId]]
  def patch(command: PatchProposalCommand): Future[Option[Proposal]]
  def anonymize(command: AnonymizeProposalCommand): Unit
  def setKeywords(command: SetKeywordsCommand): Future[Option[Proposal]]
}

trait ProposalCoordinatorServiceComponent {
  def proposalCoordinatorService: ProposalCoordinatorService
}

trait DefaultProposalCoordinatorServiceComponent extends ProposalCoordinatorServiceComponent with Logging {
  self: ProposalCoordinatorComponent =>

  override lazy val proposalCoordinatorService: ProposalCoordinatorService = new DefaultProposalCoordinatorService

  class DefaultProposalCoordinatorService extends ProposalCoordinatorService {

    implicit val timeout: Timeout = TimeSettings.defaultTimeout

    def recover[T](command: Any): PartialFunction[Throwable, Future[T]] = {
      case e: AskTimeoutException => Future.failed(ActorTimeoutException(command, e))
      case other                  => Future.failed(other)
    }

    override def getProposal(proposalId: ProposalId): Future[Option[Proposal]] = {
      val command = GetProposal(proposalId, RequestContext.empty)
      (proposalCoordinator ? command).map {
        case ProposalEnveloppe(proposal) => Some(proposal)
        case ProposalNotFound            => None
      }.recoverWith(recover(command))
    }

    override def viewProposal(proposalId: ProposalId, requestContext: RequestContext): Future[Option[Proposal]] = {
      val command = ViewProposalCommand(proposalId, requestContext)
      (proposalCoordinator ? command).map {
        case ProposalEnveloppe(proposal) => Some(proposal)
        case ProposalNotFound            => None
      }.recoverWith(recover(command))
    }

    override def propose(command: ProposeCommand): Future[ProposalId] = {
      (proposalCoordinator ? command).mapTo[CreatedProposalId].map(_.proposalId).recoverWith(recover(command))
    }

    override def update(command: UpdateProposalCommand): Future[Option[Proposal]] = {
      (proposalCoordinator ? command).flatMap {
        case InvalidStateError(message) =>
          Future.failed(ValidationFailedError(errors = Seq(ValidationError("status", "invalid_state", Some(message)))))
        case UpdatedProposal(proposal) => Future.successful(Some(proposal))
        case ProposalNotFound          => Future.successful(None)
      }.recoverWith(recover(command))
    }

    override def updateVotes(command: UpdateProposalVotesCommand): Future[Option[Proposal]] = {
      (proposalCoordinator ? command).flatMap {
        case InvalidStateError(message) =>
          Future.failed(ValidationFailedError(errors = Seq(ValidationError("status", "invalid_state", Some(message)))))
        case UpdatedProposal(proposal) => Future.successful(Some(proposal))
        case ProposalNotFound          => Future.successful(None)
      }.recoverWith(recover(command))
    }

    override def accept(command: AcceptProposalCommand): Future[Option[Proposal]] = {
      (proposalCoordinator ? command).flatMap {
        case InvalidStateError(message) =>
          Future.failed(ValidationFailedError(errors = Seq(ValidationError("status", "invalid_state", Some(message)))))
        case ModeratedProposal(proposal) => Future.successful(Some(proposal))
        case ProposalNotFound            => Future.successful(None)
      }.recoverWith(recover(command))
    }

    override def refuse(command: RefuseProposalCommand): Future[Option[Proposal]] = {
      (proposalCoordinator ? command).flatMap {
        case InvalidStateError(message) =>
          Future.failed(ValidationFailedError(errors = Seq(ValidationError("status", "invalid_state", Some(message)))))
        case ModeratedProposal(proposal) => Future.successful(Some(proposal))
        case ProposalNotFound            => Future.successful(None)
      }.recoverWith(recover(command))
    }

    override def postpone(command: PostponeProposalCommand): Future[Option[Proposal]] = {
      (proposalCoordinator ? command).flatMap {
        case InvalidStateError(message) =>
          Future.failed(ValidationFailedError(errors = Seq(ValidationError("status", "invalid_state", Some(message)))))
        case ModeratedProposal(proposal) => Future.successful(Some(proposal))
        case ProposalNotFound            => Future.successful(None)
      }.recoverWith(recover(command))
    }

    override def vote(command: VoteProposalCommand): Future[Option[Vote]] = {
      (proposalCoordinator ? command).flatMap {
        case ProposalVote(vote) => Future.successful(Some(vote))
        case VoteNotFound       => Future.successful(None)
        case ProposalNotFound   => Future.successful(None)
        case VoteError(error)   => Future.failed(error)
      }.recoverWith(recover(command))
    }

    override def unvote(command: UnvoteProposalCommand): Future[Option[Vote]] = {
      (proposalCoordinator ? command).flatMap {
        case ProposalVote(vote) => Future.successful(Some(vote))
        case VoteNotFound       => Future.successful(None)
        case ProposalNotFound   => Future.successful(None)
        case VoteError(error)   => Future.failed(error)
      }.recoverWith(recover(command))
    }

    override def qualification(command: QualifyVoteCommand): Future[Option[Qualification]] = {
      (proposalCoordinator ? command).flatMap {
        case ProposalQualification(qualification) => Future.successful(Some(qualification))
        case QualificationNotFound                => Future.successful(None)
        case ProposalNotFound                     => Future.successful(None)
        case QualificationError(error)            => Future.failed(error)
      }.recoverWith(recover(command))
    }

    override def unqualification(command: UnqualifyVoteCommand): Future[Option[Qualification]] = {
      (proposalCoordinator ? command).flatMap {
        case ProposalQualification(qualification) => Future.successful(Some(qualification))
        case QualificationNotFound                => Future.successful(None)
        case ProposalNotFound                     => Future.successful(None)
        case QualificationError(error)            => Future.failed(error)
      }.recoverWith(recover(command))
    }

    override def lock(command: LockProposalCommand): Future[Option[UserId]] = {
      (proposalCoordinator ? command).flatMap {
        case AlreadyLockedBy(moderatorName) =>
          Future.failed(
            ValidationFailedError(errors = Seq(ValidationError("moderatorName", "already_locked", Some(moderatorName))))
          )
        case Locked(moderatorId) => Future.successful(Some(moderatorId))
        case ProposalNotFound    => Future.successful(None)
      }.recoverWith(recover(command))
    }

    override def patch(command: PatchProposalCommand): Future[Option[Proposal]] = {
      (proposalCoordinator ? command).map {
        case UpdatedProposal(proposal) => Some(proposal)
        case ProposalNotFound          => None
      }
    }

    override def anonymize(command: AnonymizeProposalCommand): Unit = {
      proposalCoordinator ! command
    }

    override def setKeywords(command: SetKeywordsCommand): Future[Option[Proposal]] = {
      (proposalCoordinator ? command).map {
        case UpdatedProposal(proposal) => Some(proposal)
        case ProposalNotFound          => None
      }
    }
  }
}
