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

  override def userHistoryCoordinatorService: UserHistoryCoordinatorService = new UserHistoryCoordinatorService {

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
