package org.make.api.userhistory

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.make.api.userhistory.UserHistoryActor.UserHistory
import org.make.core.user._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

trait UserHistoryCoordinatorComponent {
  def userHistoryCoordinator: ActorRef
}

trait UserHistoryCoordinatorService {
  def userHistory(userId: UserId): Future[UserHistory]
  def moderatorHistory(userId: UserId): Future[UserHistory]
  def logHistory(command: UserHistoryEvent[_]): Unit
}

trait UserHistoryCoordinatorServiceComponent {
  def userHistoryCoordinatorService: UserHistoryCoordinatorService
}

trait DefaultUserHistoryCoordinatorServiceComponent extends UserHistoryCoordinatorServiceComponent {
  self: UserHistoryCoordinatorComponent =>

  override def userHistoryCoordinatorService: UserHistoryCoordinatorService = new UserHistoryCoordinatorService {

    implicit val timeout: Timeout = Timeout(3.seconds)

    override def userHistory(userId: UserId): Future[UserHistory] = {
      (userHistoryCoordinator ? GetUserHistory(userId)).mapTo[UserHistory].map { history =>
        history.copy(events = history.events.filter(_.protagonist == Citizen))
      }
    }

    override def moderatorHistory(userId: UserId): Future[UserHistory] = {
      (userHistoryCoordinator ? GetUserHistory(userId)).mapTo[UserHistory].map { history =>
        history.copy(events = history.events.filter(_.protagonist == Moderator))
      }
    }

    override def logHistory(command: UserHistoryEvent[_]): Unit = {
      userHistoryCoordinator ! command
    }

  }
}
