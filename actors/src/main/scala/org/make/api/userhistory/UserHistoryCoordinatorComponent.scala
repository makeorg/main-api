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

package org.make.api.userhistory

import akka.actor.typed.ActorRef
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import org.make.api.ActorSystemTypedComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.Ack
import org.make.api.technical.BetterLoggingActors._
import org.make.api.technical.{MakePersistentActor, StreamUtils, TimeSettings}
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.user._
import org.make.core.{ValidationError, ValidationFailedError}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait UserHistoryCoordinatorComponent {
  def userHistoryCoordinator: ActorRef[UserHistoryCommand]
}

trait UserHistoryCoordinatorService {
  def logHistory(event: UserHistoryEvent[_]): Unit
  def logTransactionalHistory(event: TransactionalUserHistoryEvent[_]): Future[Unit]
  def retrieveVoteAndQualifications(
    userId: UserId,
    proposalIds: Seq[ProposalId]
  ): Future[Map[ProposalId, VoteAndQualifications]]
  def retrieveVotedProposals(request: RequestUserVotedProposals): Future[Seq[ProposalId]]
  def delete(userId: UserId): Future[Unit]
}

trait UserHistoryCoordinatorServiceComponent {
  def userHistoryCoordinatorService: UserHistoryCoordinatorService
}

trait DefaultUserHistoryCoordinatorServiceComponent extends UserHistoryCoordinatorServiceComponent {
  self: UserHistoryCoordinatorComponent with ActorSystemTypedComponent with MakeSettingsComponent =>

  override lazy val userHistoryCoordinatorService: UserHistoryCoordinatorService =
    new DefaultUserHistoryCoordinatorService

  class DefaultUserHistoryCoordinatorService extends UserHistoryCoordinatorService {

    private val proposalsPerPage: Int = makeSettings.maxHistoryProposalsPerPage
    implicit val timeout: Timeout = TimeSettings.defaultTimeout

    override def logHistory(event: UserHistoryEvent[_]): Unit = {
      userHistoryCoordinator ! UserHistoryEnvelope(event.userId, event)
    }

    override def logTransactionalHistory(event: TransactionalUserHistoryEvent[_]): Future[Unit] = {
      (userHistoryCoordinator ?? { replyTo: ActorRef[UserHistoryResponse[Ack.type]] =>
        UserHistoryTransactionalEnvelope(event.userId, event, replyTo)
      }).flatMap(_ => Future.unit)
    }

    override def retrieveVoteAndQualifications(
      userId: UserId,
      proposalIds: Seq[ProposalId]
    ): Future[Map[ProposalId, VoteAndQualifications]] = {
      (userHistoryCoordinator ?? (RequestVoteValues(userId, proposalIds, _))).flatMap(shapeResponse)
    }

    private def retrieveVotedProposalsPage(request: RequestUserVotedProposals, offset: Int): Future[Seq[ProposalId]] = {
      def requestPaginate(
        proposalsIds: Option[Seq[ProposalId]],
        replyTo: ActorRef[UserHistoryResponse[Seq[ProposalId]]]
      ) =
        RequestUserVotedProposalsPaginate(
          userId = request.userId,
          filterVotes = request.filterVotes,
          filterQualifications = request.filterQualifications,
          proposalsIds = proposalsIds,
          limit = offset + proposalsPerPage,
          skip = offset,
          replyTo
        )
      request.proposalsIds match {
        case Some(proposalsIds) if proposalsIds.size > proposalsPerPage =>
          Source(proposalsIds)
            .sliding(proposalsPerPage, proposalsPerPage)
            .mapAsync(5) { someProposalsIds =>
              (userHistoryCoordinator ?? (requestPaginate(Some(someProposalsIds), _)))
                .flatMap(shapeResponse)
            }
            .mapConcat(identity)
            .runWith(Sink.seq)
        case _ =>
          (userHistoryCoordinator ?? (requestPaginate(request.proposalsIds, _))).flatMap(shapeResponse)
      }
    }

    override def retrieveVotedProposals(request: RequestUserVotedProposals): Future[Seq[ProposalId]] = {
      StreamUtils
        .asyncPageToPageSource(retrieveVotedProposalsPage(request, _))
        .mapConcat(identity)
        .runWith(Sink.seq)
    }

    override def delete(userId: UserId): Future[Unit] = {
      (userHistoryCoordinator ?? (Stop(userId, _)))
        .flatMap(_ => MakePersistentActor.delete(userId, UserHistoryActor.JournalPluginId))
    }

    private def shapeResponse[T](response: UserHistoryResponse[T]): Future[T] = response match {
      case UserHistoryResponse(value) => Future.successful(value)
      case other =>
        Future.failed(
          ValidationFailedError(errors = Seq(ValidationError("status", "invalid_state", Some(other.toString))))
        )
    }
  }
}

final case class RequestUserVotedProposals(
  userId: UserId,
  filterVotes: Option[Seq[VoteKey]] = None,
  filterQualifications: Option[Seq[QualificationKey]] = None,
  proposalsIds: Option[Seq[ProposalId]] = None
)
