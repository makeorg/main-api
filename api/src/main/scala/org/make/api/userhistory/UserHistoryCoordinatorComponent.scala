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

trait UserHistoryService {
  def userHistory(userId: UserId): Future[UserHistory]
  def moderatorHistory(userId: UserId): Future[UserHistory]
  def logHistory(command: UserHistoryEvent[_]): Unit
}

trait UserHistoryServiceComponent {
  def userHistoryService: UserHistoryService
}

trait DefaultUserHistoryServiceComponent extends UserHistoryServiceComponent {
  self: UserHistoryCoordinatorComponent =>

  implicit val timeout: Timeout = Timeout(3.seconds)

  override def userHistoryService: UserHistoryService = new UserHistoryService {

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
