package org.make.api.userhistory

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import org.make.api.userhistory.UserHistoryActor._
import org.make.core.user._

class UserHistoryActor extends PersistentActor with ActorLogging {

  def userId: UserId = UserId(self.path.name)

  private var state: UserHistory = UserHistory(Nil)

  override def receiveRecover: Receive = {
    case event: UserHistoryEvent[_]              => state = applyEvent(event)
    case SnapshotOffer(_, snapshot: UserHistory) => state = snapshot
    case _: RecoveryCompleted                    =>
  }

  override def receiveCommand: Receive = {
    case GetUserHistory(_)                => sender() ! state
    case command: LogSearchProposalsEvent => persistAndPublishEvent(command)
    case command: LogAcceptProposalEvent  => persistAndPublishEvent(command)
    case command: LogRefuseProposalEvent  => persistAndPublishEvent(command)
    case command: LogRegisterCitizenEvent => persistAndPublishEvent(command)
    case command: LogUserProposalEvent    => persistAndPublishEvent(command)
  }

  override def persistenceId: String = userId.value

  private def persistAndPublishEvent(event: UserHistoryEvent[_]): Unit = {
    persist(event) { e: UserHistoryEvent[_] =>
      state = applyEvent(e)
    }
  }

  private def applyEvent(event: UserHistoryEvent[_]) = {
    state.copy(events = event :: state.events)
  }
}

object UserHistoryActor {
  case class UserHistory(events: List[UserHistoryEvent[_]])
}
