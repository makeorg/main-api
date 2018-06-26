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
import akka.util.Timeout
import org.make.api.sessionhistory.SessionHistoryActor.SessionHistory
import org.make.api.technical.TimeSettings
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.ProposalId
import org.make.core.session.SessionId
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

trait SessionHistoryCoordinatorComponent {
  def sessionHistoryCoordinator: ActorRef
}

trait SessionHistoryCoordinatorService {
  def sessionHistory(sessionId: SessionId): Future[SessionHistory]
  def logHistory(command: SessionHistoryEvent[_]): Unit
  def convertSession(sessionId: SessionId, userId: UserId): Future[Unit]
  def retrieveVoteAndQualifications(request: RequestSessionVoteValues): Future[Map[ProposalId, VoteAndQualifications]]
  def retrieveVotedProposals(request: RequestSessionVotedProposals): Future[Seq[ProposalId]]
}

trait SessionHistoryCoordinatorServiceComponent {
  def sessionHistoryCoordinatorService: SessionHistoryCoordinatorService
}

trait DefaultSessionHistoryCoordinatorServiceComponent extends SessionHistoryCoordinatorServiceComponent {
  self: SessionHistoryCoordinatorComponent =>

  override def sessionHistoryCoordinatorService: SessionHistoryCoordinatorService =
    new SessionHistoryCoordinatorService {

      implicit val timeout: Timeout = TimeSettings.defaultTimeout

      override def sessionHistory(sessionId: SessionId): Future[SessionHistory] = {
        (sessionHistoryCoordinator ? GetSessionHistory(sessionId)).mapTo[SessionHistory]
      }

      override def logHistory(command: SessionHistoryEvent[_]): Unit = {
        sessionHistoryCoordinator ! command
      }

      override def retrieveVoteAndQualifications(
        request: RequestSessionVoteValues
      ): Future[Map[ProposalId, VoteAndQualifications]] = {
        (sessionHistoryCoordinator ? request).mapTo[Map[ProposalId, VoteAndQualifications]]
      }

      override def convertSession(sessionId: SessionId, userId: UserId): Future[Unit] = {
        (sessionHistoryCoordinator ? UserConnected(sessionId, userId)).map(_ => {})
      }

      override def retrieveVotedProposals(request: RequestSessionVotedProposals): Future[Seq[ProposalId]] = {
        (sessionHistoryCoordinator ? request).mapTo[Seq[ProposalId]]
      }

    }

}
