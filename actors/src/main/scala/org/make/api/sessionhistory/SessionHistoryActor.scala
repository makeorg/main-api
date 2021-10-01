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

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.scaladsl._
import akka.persistence.typed.{PersistenceId, SnapshotAdapter}
import grizzled.slf4j.Logging
import org.make.api.extensions.MakeSettings
import org.make.api.technical.TimeSettings
import org.make.api.userhistory._
import org.make.core.proposal.{ProposalId, QualificationKey}
import org.make.core.session._
import org.make.core.technical.IdGenerator

import scala.concurrent.duration.{Deadline, FiniteDuration}

object SessionHistoryActor extends Logging {

  val JournalPluginId: String = "make-api.event-sourcing.sessions.journal"
  val SnapshotPluginId: String = "make-api.event-sourcing.sessions.snapshot"
  val QueryJournalPluginId: String = "make-api.event-sourcing.sessions.query"

  sealed trait LockVoteAction

  case object Vote extends LockVoteAction
  final case class ChangeQualifications(key: Seq[QualificationKey]) extends LockVoteAction

  class LockHandler(lockDuration: FiniteDuration) {
    private var locks: Map[ProposalId, LockVoteAction] = Map.empty
    private var deadline = Deadline.now

    def add(lock: (ProposalId, LockVoteAction)): Unit = {
      locks += lock
      deadline = Deadline.now + lockDuration
    }

    def getOrExpire(id: ProposalId): Option[LockVoteAction] = {
      if (deadline.isOverdue()) {
        locks = Map.empty
      }
      locks.get(id)
    }

    def release(id: ProposalId): Unit = {
      locks -= id
    }
  }

  def apply(
    userHistoryCoordinator: ActorRef[UserHistoryCommand],
    lockDuration: FiniteDuration,
    idGenerator: IdGenerator,
    settings: MakeSettings
  ): Behavior[SessionHistoryCommand] = {
    Behaviors.setup { implicit context =>
      val timeout = TimeSettings.defaultTimeout
      val id: SessionId = SessionId(context.self.path.name)
      val persistenceId: PersistenceId = PersistenceId.ofUniqueId(id.value)
      val lockHandler = new LockHandler(lockDuration)
      EventSourcedBehavior[SessionHistoryCommand, SessionHistoryEvent[_], State](
        persistenceId,
        emptyState = Active(SessionHistory(Nil), None),
        _.handleCommand(userHistoryCoordinator, lockHandler, settings, idGenerator)(context, timeout)(_),
        _.handleEvent(settings)(_)
      ).withJournalPluginId(JournalPluginId)
        .withSnapshotPluginId(SnapshotPluginId)
        .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 10, keepNSnapshots = 50))
        .snapshotAdapter(snapshotAdapter)
    }
  }

  val snapshotAdapter: SnapshotAdapter[State] = new SnapshotAdapter[State] {
    override def toJournal(state: State): Any = state

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    override def fromJournal(from: Any): State = {
      from match {
        case sessionHistory: SessionHistory =>
          val allEvents: Seq[SessionHistoryEvent[_]] =
            sessionHistory.events.sortWith((e1, e2) => e2.action.date.isBefore(e1.action.date))

          allEvents.collectFirst {
            case event: SessionExpired     => Expired(event.action.arguments, Some(event.action.date))
            case event: SessionTransformed => Closed(event.action.arguments, Some(event.action.date))
            case event: SessionTransforming =>
              Transforming(sessionHistory, event.requestContext, Some(event.action.date))
          }.getOrElse(Active(sessionHistory, allEvents.headOption.map(_.action.date)))
        case state: State => state
        case other        => throw new IllegalStateException(s"$other with class ${other.getClass} is not a recoverable state")
      }
    }
  }
}
