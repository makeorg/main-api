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
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryResponse.{Error, LockResponse}
import org.make.api.sessionhistory.SessionHistoryResponse.Error.ExpiredSession
import org.make.api.technical.BetterLoggingActors.BetterLoggingTypedActorRef
import org.make.api.technical.Futures._
import org.make.api.technical.{ActorSystemComponent, StreamUtils, TimeSettings}
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.{ProposalId, QualificationKey}
import org.make.core.session.SessionId
import org.make.core.user.UserId
import org.make.core.RequestContext

import scala.concurrent.ExecutionContext.global
import scala.concurrent.{ExecutionContext, Future}

trait SessionHistoryCoordinatorComponent {
  def sessionHistoryCoordinator: ActorRef[SessionHistoryCommand]
}

trait SessionHistoryCoordinatorService {
  def getCurrentSessionId(sessionId: SessionId, newSessionId: SessionId): Future[SessionId]
  def logTransactionalHistory[T <: LoggableHistoryEvent[_]](command: T): Future[Unit]
  def convertSession(sessionId: SessionId, userId: UserId, requestContext: RequestContext): Future[Unit]
  def retrieveVoteAndQualifications(
    sessionId: SessionId,
    proposalIds: Seq[ProposalId]
  ): Future[Map[ProposalId, VoteAndQualifications]]
  def retrieveVotedProposals(
    sessionId: SessionId,
    proposalsIds: Option[Seq[ProposalId]] = None
  ): Future[Seq[ProposalId]]
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

  type Receiver[T] = ActorRef[SessionHistoryResponse[Error.Expired, T]]
  type LockReceiver = ActorRef[SessionHistoryResponse[Error.LockError, LockResponse]]

  override lazy val sessionHistoryCoordinatorService: SessionHistoryCoordinatorService =
    new DefaultSessionHistoryCoordinatorService

  class DefaultSessionHistoryCoordinatorService extends SessionHistoryCoordinatorService {

    implicit val ctx: ExecutionContext = global
    private val proposalsPerPage: Int = makeSettings.maxHistoryProposalsPerPage
    implicit val timeout: Timeout = TimeSettings.defaultTimeout

    override def getCurrentSessionId(sessionId: SessionId, newSessionId: SessionId): Future[SessionId] = {
      (sessionHistoryCoordinator ?? (GetCurrentSession(sessionId, newSessionId, _))).map(_.value)
    }

    override def logTransactionalHistory[T <: LoggableHistoryEvent[_]](command: T): Future[Unit] = {
      def log(id: SessionId)(replyTo: Receiver[LogResult]): EventEnvelope[T] = EventEnvelope(id, command, replyTo)
      askExpired(sessionHistoryCoordinator, log)(command.sessionId).flatMap(shapeResponse).toUnit
    }

    override def retrieveVoteAndQualifications(
      sessionId: SessionId,
      proposalIds: Seq[ProposalId]
    ): Future[Map[ProposalId, VoteAndQualifications]] = {
      def votesValues(id: SessionId)(replyTo: Receiver[Map[ProposalId, VoteAndQualifications]]) =
        SessionVoteValuesCommand(id, proposalIds, replyTo)
      askExpired(sessionHistoryCoordinator, votesValues)(sessionId).flatMap(shapeResponse)
    }

    override def convertSession(sessionId: SessionId, userId: UserId, requestContext: RequestContext): Future[Unit] = {
      def userConnected(id: SessionId)(replyTo: Receiver[LogResult]) =
        UserConnected(id, userId, requestContext, replyTo)
      askExpired(sessionHistoryCoordinator, userConnected)(sessionId).flatMap(shapeResponse).toUnit
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

    private def doRequestVotedProposalsPage(request: RequestSessionVotedProposalsPaginate): Future[Seq[ProposalId]] = {
      def voted(id: SessionId)(replyTo: Receiver[Seq[ProposalId]]) =
        SessionVotedProposalsPaginateCommand(id, request.proposalsIds, request.limit, request.skip, replyTo)
      askExpired(sessionHistoryCoordinator, voted)(request.sessionId).flatMap(shapeResponse)
    }

    override def retrieveVotedProposals(
      sessionId: SessionId,
      proposalsIds: Option[Seq[ProposalId]] = None
    ): Future[Seq[ProposalId]] = {
      StreamUtils
        .asyncPageToPageSource(retrieveVotedProposalsPage(RequestSessionVotedProposals(sessionId, proposalsIds), _))
        .mapConcat(identity)
        .runWith(Sink.seq)
    }

    override def lockSessionForVote(sessionId: SessionId, proposalId: ProposalId): Future[Unit] = {
      def lock(id: SessionId)(replyTo: LockReceiver) = LockProposalForVote(id, proposalId, replyTo)
      askExpired(sessionHistoryCoordinator, lock)(sessionId).flatMap(shapeResponse).toUnit
    }

    override def lockSessionForQualification(
      sessionId: SessionId,
      proposalId: ProposalId,
      key: QualificationKey
    ): Future[Unit] = {
      def lock(id: SessionId)(replyTo: LockReceiver) = LockProposalForQualification(id, proposalId, key, replyTo)
      askExpired(sessionHistoryCoordinator, lock)(sessionId).flatMap(shapeResponse).toUnit
    }

    override def unlockSessionForVote(sessionId: SessionId, proposalId: ProposalId): Future[Unit] = {
      def release(id: SessionId)(replyTo: LockReceiver) = ReleaseProposalForVote(id, proposalId, replyTo)
      askExpired(sessionHistoryCoordinator, release)(sessionId).flatMap(shapeResponse).toUnit
    }

    override def unlockSessionForQualification(
      sessionId: SessionId,
      proposalId: ProposalId,
      key: QualificationKey
    ): Future[Unit] = {
      def release(id: SessionId)(replyTo: LockReceiver) = ReleaseProposalForQualification(id, proposalId, key, replyTo)
      askExpired(sessionHistoryCoordinator, release)(sessionId).flatMap(shapeResponse).toUnit
    }

    private def shapeResponse[E, T](response: SessionHistoryResponse[E, T]): Future[T] = response match {
      case SessionHistoryResponse.Envelope(value)                    => Future.successful(value)
      case l: SessionHistoryResponse.LockResponse                    => Future.successful(l)
      case SessionHistoryResponse.Error.LockAlreadyAcquired(message) => Future.failed(ConcurrentModification(message))
      case error: SessionHistoryResponse.Error[_] =>
        Future.failed(
          new IllegalStateException(
            s"Unknown response from session history actor: ${error.toString} of class ${error.getClass.getName}}"
          )
        )
    }

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    private def askExpired[Req, Res](ref: ActorRef[Req], replyTo: SessionId => ActorRef[Res] => Req)(
      id: SessionId
    ): Future[Res] = {
      (ref ?? replyTo(id)).flatMap {
        case ExpiredSession(newSessionId) => askExpired(ref, replyTo)(newSessionId)
        case other                        => Future.successful(other)
      }
    }

  }
}

final case class RequestSessionVotedProposals(sessionId: SessionId, proposalsIds: Option[Seq[ProposalId]] = None)
final case class RequestSessionVotedProposalsPaginate(
  sessionId: SessionId,
  proposalsIds: Option[Seq[ProposalId]] = None,
  limit: Int,
  skip: Int
)
