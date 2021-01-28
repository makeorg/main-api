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

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.RichFutures._
import org.make.api.technical.{StreamUtils, TimeSettings}
import org.make.api.userhistory.UserHistoryActor.{
  ReloadState,
  RequestUserVotedProposals,
  RequestUserVotedProposalsPaginate,
  RequestVoteValues
}
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.ProposalId
import org.make.core.user._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait UserHistoryCoordinatorComponent {
  def userHistoryCoordinator: ActorRef
}

trait UserHistoryCoordinatorService {
  def logHistory(command: UserHistoryEvent[_]): Unit
  def logTransactionalHistory(command: TransactionalUserHistoryEvent[_]): Future[Unit]
  def retrieveVoteAndQualifications(request: RequestVoteValues): Future[Map[ProposalId, VoteAndQualifications]]
  def retrieveVotedProposals(request: RequestUserVotedProposals): Future[Seq[ProposalId]]
  def reloadHistory(userId: UserId): Unit
}

trait UserHistoryCoordinatorServiceComponent {
  def userHistoryCoordinatorService: UserHistoryCoordinatorService
}

trait DefaultUserHistoryCoordinatorServiceComponent extends UserHistoryCoordinatorServiceComponent {
  self: UserHistoryCoordinatorComponent with ActorSystemComponent with MakeSettingsComponent =>

  override lazy val userHistoryCoordinatorService: UserHistoryCoordinatorService =
    new DefaultUserHistoryCoordinatorService

  class DefaultUserHistoryCoordinatorService extends UserHistoryCoordinatorService {

    private val proposalsPerPage: Int = makeSettings.maxHistoryProposalsPerPage
    implicit val timeout: Timeout = TimeSettings.defaultTimeout

    override def logHistory(command: UserHistoryEvent[_]): Unit = {
      userHistoryCoordinator ! UserHistoryEnvelope(command.userId, command)
    }

    override def logTransactionalHistory(command: TransactionalUserHistoryEvent[_]): Future[Unit] = {
      (userHistoryCoordinator ? UserHistoryEnvelope(command.userId, command)).toUnit
    }

    override def retrieveVoteAndQualifications(
      request: RequestVoteValues
    ): Future[Map[ProposalId, VoteAndQualifications]] = {
      (userHistoryCoordinator ? request).mapTo[VotesValues].map(_.votesValues)
    }

    private def retrieveVotedProposalsPage(request: RequestUserVotedProposals, offset: Int): Future[Seq[ProposalId]] = {
      def requestPaginate(proposalsIds: Option[Seq[ProposalId]]) =
        RequestUserVotedProposalsPaginate(
          userId = request.userId,
          filterVotes = request.filterVotes,
          filterQualifications = request.filterQualifications,
          proposalsIds = proposalsIds,
          limit = offset + proposalsPerPage,
          skip = offset
        )
      request.proposalsIds match {
        case Some(proposalsIds) if proposalsIds.size > proposalsPerPage =>
          Source(proposalsIds)
            .sliding(proposalsPerPage, proposalsPerPage)
            .mapAsync(5) { someProposalsIds =>
              (userHistoryCoordinator ? requestPaginate(Some(someProposalsIds)))
                .mapTo[VotedProposals]
                .map(_.proposals)
            }
            .mapConcat(identity)
            .runWith(Sink.seq)
        case _ =>
          (userHistoryCoordinator ? requestPaginate(request.proposalsIds)).mapTo[VotedProposals].map(_.proposals)
      }
    }

    override def retrieveVotedProposals(request: RequestUserVotedProposals): Future[Seq[ProposalId]] = {
      StreamUtils
        .asyncPageToPageSource(retrieveVotedProposalsPage(request, _))
        .mapConcat(identity)
        .runWith(Sink.seq)
    }

    override def reloadHistory(userId: UserId): Unit = {
      userHistoryCoordinator ! ReloadState(userId)
    }
  }
}
