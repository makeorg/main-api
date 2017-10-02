package org.make.api.sessionhistory

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.make.api.sessionhistory.SessionHistoryActor.SessionHistory
import org.make.core.session.{GetSessionHistory, SessionHistoryEvent, SessionId}

import scala.concurrent.Future
import scala.concurrent.duration._

trait SessionHistoryCoordinatorComponent {
  def sessionHistoryCoordinator: ActorRef
}

trait SessionHistoryCoordinatorService {
  def sessionHistory(sessionId: SessionId): Future[SessionHistory]
  def logHistory(command: SessionHistoryEvent[_]): Unit
}

trait SessionHistoryCoordinatorServiceComponent {
  def sessionHistoryCoordinatorService: SessionHistoryCoordinatorService
}

trait DefaultSessionHistoryCoordinatorServiceComponent extends SessionHistoryCoordinatorServiceComponent {
  self: SessionHistoryCoordinatorComponent =>

  override def sessionHistoryCoordinatorService: SessionHistoryCoordinatorService =
    new SessionHistoryCoordinatorService {

      implicit val timeout: Timeout = Timeout(3.seconds)

      override def sessionHistory(sessionId: SessionId): Future[SessionHistory] = {
        (sessionHistoryCoordinator ? GetSessionHistory(sessionId)).mapTo[SessionHistory]
      }

      override def logHistory(command: SessionHistoryEvent[_]): Unit = {
        sessionHistoryCoordinator ! command
      }
    }
}
