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

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryActor.{CurrentSessionId, SessionHistory}
import org.make.api.technical.Futures._
import org.make.api.technical.{StreamUtils, TimeSettings}
import org.make.api.userhistory.{VotedProposals, VotesValues}
import org.make.core.RequestContext
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.{ProposalId, QualificationKey}
import org.make.core.session.SessionId
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

trait SessionHistoryCoordinatorComponent {
  def sessionHistoryCoordinator: ActorRef
}

trait SessionHistoryCoordinatorService {
  def sessionHistory(sessionId: SessionId): Future[SessionHistory]
  def getCurrentSessionId(sessionId: SessionId, newSessionId: SessionId): Future[SessionId]
  def logTransactionalHistory(command: TransactionalSessionHistoryEvent[_]): Future[Unit]
  def convertSession(sessionId: SessionId, userId: UserId, requestContext: RequestContext): Future[Unit]
  def retrieveVoteAndQualifications(request: RequestSessionVoteValues): Future[Map[ProposalId, VoteAndQualifications]]
  def retrieveVotedProposals(request: RequestSessionVotedProposals): Future[Seq[ProposalId]]
  def lockSessionForVote(sessionId: SessionId, proposalId: ProposalId): Future[Unit]
  def lockSessionForQualification(sessionId: SessionId, proposalId: ProposalId, key: QualificationKey): Future[Unit]
  def unlockSessionForVote(sessionId: SessionId, proposalId: ProposalId): Future[Unit]
  def unlockSessionForQualification(sessionId: SessionId, proposalId: ProposalId, key: QualificationKey): Future[Unit]
}

trait SessionHistoryCoordinatorServiceComponent {
  def sessionHistoryCoordinatorService: SessionHistoryCoordinatorService
}

final case class ConcurrentModification(message: String) extends Exception(message)

trait DefaultSessionHistoryCoordinatorServiceComponent extends SessionHistoryCoordinatorServiceComponent {
  self: SessionHistoryCoordinatorComponent with ActorSystemComponent with MakeSettingsComponent =>

  override lazy val sessionHistoryCoordinatorService: SessionHistoryCoordinatorService =
    new DefaultSessionHistoryCoordinatorService

  class DefaultSessionHistoryCoordinatorService extends SessionHistoryCoordinatorService {

    private val proposalsPerPage: Int = makeSettings.maxHistoryProposalsPerPage
    implicit val timeout: Timeout = TimeSettings.defaultTimeout

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    override def sessionHistory(sessionId: SessionId): Future[SessionHistory] = {
      (sessionHistoryCoordinator ? GetSessionHistory(sessionId)).flatMap {
        case SessionIsExpired(newSessionId) => sessionHistory(newSessionId)
        case response                       => Future.successful(response).mapTo[SessionHistory]
      }
    }

    override def getCurrentSessionId(sessionId: SessionId, newSessionId: SessionId): Future[SessionId] = {
      (sessionHistoryCoordinator ? GetCurrentSession(sessionId, newSessionId)).mapTo[CurrentSessionId].map(_.sessionId)
    }

    override def logTransactionalHistory(command: TransactionalSessionHistoryEvent[_]): Future[Unit] = {
      (sessionHistoryCoordinator ? SessionHistoryEnvelope(command.sessionId, command)).flatMap {
        case SessionIsExpired(newSessionId) =>
          (sessionHistoryCoordinator ? SessionHistoryEnvelope(newSessionId, command)).toUnit
        case _ => Future.unit
      }
    }

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    override def retrieveVoteAndQualifications(
      request: RequestSessionVoteValues
    ): Future[Map[ProposalId, VoteAndQualifications]] = {
      (sessionHistoryCoordinator ? request).flatMap {
        case SessionIsExpired(newSessionId) => retrieveVoteAndQualifications(request.copy(sessionId = newSessionId))
        case response                       => Future.successful(response).mapTo[VotesValues].map(_.votesValues)
      }
    }

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    override def convertSession(sessionId: SessionId, userId: UserId, requestContext: RequestContext): Future[Unit] = {
      (sessionHistoryCoordinator ? UserConnected(sessionId, userId, requestContext)).flatMap {
        case SessionIsExpired(newSessionId) => convertSession(newSessionId, userId, requestContext)
        case _                              => Future.unit
      }
    }

    private def retrieveVotedProposalsPage(
      request: RequestSessionVotedProposals,
      offset: Int
    ): Future[Seq[ProposalId]] = {
      def requestPaginate(proposalsIds: Option[Seq[ProposalId]]) =
        RequestSessionVotedProposalsPaginate(
          sessionId = request.sessionId,
          proposalsIds = proposalsIds,
          limit = offset + proposalsPerPage,
          skip = offset
        )
      request.proposalsIds match {
        case Some(proposalsIds) if proposalsIds.size > proposalsPerPage =>
          Source(proposalsIds)
            .sliding(proposalsPerPage, proposalsPerPage)
            .mapAsync(5) { someProposalsIds =>
              doRequestVotedProposalsPage(requestPaginate(Some(someProposalsIds)))
            }
            .mapConcat(identity)
            .runWith(Sink.seq)
        case _ =>
          doRequestVotedProposalsPage(requestPaginate(request.proposalsIds))
      }
    }

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    private def doRequestVotedProposalsPage(request: RequestSessionVotedProposalsPaginate): Future[Seq[ProposalId]] = {
      (sessionHistoryCoordinator ? request).flatMap {
        case SessionIsExpired(newSessionId) => doRequestVotedProposalsPage(request.copy(sessionId = newSessionId))
        case response: VotedProposals       => Future.successful(response.proposals)
        case other =>
          Future.failed(
            new IllegalStateException(
              s"Unknown response from session history actor: ${other.toString} of class ${other.getClass.getName}}"
            )
          )
      }
    }

    override def retrieveVotedProposals(request: RequestSessionVotedProposals): Future[Seq[ProposalId]] = {
      StreamUtils
        .asyncPageToPageSource(retrieveVotedProposalsPage(request, _))
        .mapConcat(identity)
        .runWith(Sink.seq)
    }

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    override def lockSessionForVote(sessionId: SessionId, proposalId: ProposalId): Future[Unit] = {
      (sessionHistoryCoordinator ? LockProposalForVote(sessionId, proposalId)).flatMap {
        case SessionIsExpired(newSessionId) => lockSessionForVote(newSessionId, proposalId)
        case LockAcquired                   => Future.unit
        case LockAlreadyAcquired =>
          Future.failed(ConcurrentModification("A vote is already pending for this proposal"))
      }
    }

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    override def lockSessionForQualification(
      sessionId: SessionId,
      proposalId: ProposalId,
      key: QualificationKey
    ): Future[Unit] = {
      (sessionHistoryCoordinator ? LockProposalForQualification(sessionId, proposalId, key)).flatMap {
        case SessionIsExpired(newSessionId) => lockSessionForQualification(newSessionId, proposalId, key)
        case LockAcquired                   => Future.unit
        case LockAlreadyAcquired =>
          Future.failed(ConcurrentModification("A qualification is already pending for this proposal"))
      }
    }

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    override def unlockSessionForVote(sessionId: SessionId, proposalId: ProposalId): Future[Unit] = {
      (sessionHistoryCoordinator ? ReleaseProposalForVote(sessionId, proposalId)).flatMap {
        case SessionIsExpired(newSessionId) => unlockSessionForVote(newSessionId, proposalId)
        case _                              => Future.unit
      }
    }

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    override def unlockSessionForQualification(
      sessionId: SessionId,
      proposalId: ProposalId,
      key: QualificationKey
    ): Future[Unit] = {
      (sessionHistoryCoordinator ? ReleaseProposalForQualification(sessionId, proposalId, key)).flatMap {
        case SessionIsExpired(newSessionId) => unlockSessionForQualification(newSessionId, proposalId, key)
        case _                              => Future.unit
      }
    }
  }

}
