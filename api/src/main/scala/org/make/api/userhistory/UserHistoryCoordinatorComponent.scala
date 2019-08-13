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
import akka.util.Timeout
import org.make.api.technical.TimeSettings
import org.make.api.userhistory.UserHistoryActor.{ReloadState, RequestUserVotedProposals, RequestVoteValues}
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.ProposalId
import org.make.core.user._

import scala.concurrent.Future

trait UserHistoryCoordinatorComponent {
  def userHistoryCoordinator: ActorRef
}

trait UserHistoryCoordinatorService {
  def logHistory(command: UserHistoryEvent[_]): Unit
  def retrieveVoteAndQualifications(request: RequestVoteValues): Future[Map[ProposalId, VoteAndQualifications]]
  def retrieveVotedProposals(request: RequestUserVotedProposals): Future[Seq[ProposalId]]
  def reloadHistory(userId: UserId): Unit
}

trait UserHistoryCoordinatorServiceComponent {
  def userHistoryCoordinatorService: UserHistoryCoordinatorService
}

trait DefaultUserHistoryCoordinatorServiceComponent extends UserHistoryCoordinatorServiceComponent {
  self: UserHistoryCoordinatorComponent =>

  override lazy val userHistoryCoordinatorService: UserHistoryCoordinatorService =
    new DefaultUserHistoryCoordinatorService

  class DefaultUserHistoryCoordinatorService extends UserHistoryCoordinatorService {

    implicit val timeout: Timeout = TimeSettings.defaultTimeout

    override def logHistory(command: UserHistoryEvent[_]): Unit = {
      userHistoryCoordinator ! command
    }

    override def retrieveVoteAndQualifications(
      request: RequestVoteValues
    ): Future[Map[ProposalId, VoteAndQualifications]] = {
      (userHistoryCoordinator ? request).mapTo[Map[ProposalId, VoteAndQualifications]]
    }

    override def retrieveVotedProposals(request: RequestUserVotedProposals): Future[Seq[ProposalId]] = {
      (userHistoryCoordinator ? request).mapTo[Seq[ProposalId]]
    }

    override def reloadHistory(userId: UserId): Unit = {
      userHistoryCoordinator ! ReloadState(userId)
    }
  }
}
