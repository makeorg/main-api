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
        sessionHistoryCoordinator ? command
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
