package org.make.api.sessionhistory

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import org.make.api.sessionhistory.SessionHistoryActor.SessionHistory
import org.make.core.session._

class SessionHistoryActor extends PersistentActor with ActorLogging {

  def sessionId: SessionId = SessionId(self.path.name)

  private var state: SessionHistory = SessionHistory(Nil)

  override def receiveRecover: Receive = {
    case event: SessionHistoryEvent[_]              => state = applyEvent(event)
    case SnapshotOffer(_, snapshot: SessionHistory) => state = snapshot
    case _: RecoveryCompleted                       =>
  }

  override def receiveCommand: Receive = {
    case GetSessionHistory(_)                    => sender() ! state
    case command: LogSessionVoteEvent            => persistEvent(command)
    case command: LogSessionUnvoteEvent          => persistEvent(command)
    case command: LogSessionQualificationEvent   => persistEvent(command)
    case command: LogSessionUnqualificationEvent => persistEvent(command)
    case command: LogSessionSearchProposalsEvent => persistEvent(command)
  }

  override def persistenceId: String = sessionId.value

  private def persistEvent(event: SessionHistoryEvent[_]): Unit = {
    persist(event) { e: SessionHistoryEvent[_] =>
      state = applyEvent(e)
    }
  }

  private def applyEvent(event: SessionHistoryEvent[_]) = {
    state.copy(events = event :: state.events)
  }
}

object SessionHistoryActor {
  case class SessionHistory(events: List[SessionHistoryEvent[_]])
}
