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

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorLogging
import akka.persistence.SnapshotOffer
import org.make.api.technical.MakePersistentActor.Snapshot
import org.make.api.technical.{ActorReadJournalComponent, MakePersistentActor}
import org.make.api.userhistory.UserHistoryActor._
import org.make.core.MakeSerializable
import org.make.core.history.HistoryActions._
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.user._
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

class UserHistoryActor
    extends MakePersistentActor(classOf[UserVotesAndQualifications], classOf[UserHistoryEvent[_]])
    with ActorLogging
    with ActorReadJournalComponent {

  def userId: UserId = UserId(self.path.name)

  override protected def unhandledRecover: Receive = {
    case SnapshotOffer(_, history: UserHistory) =>
      history.events.sortBy(_.action.date.toEpochSecond).foreach { event =>
        state = applyEvent(event)
      }
  }

  override def receiveCommand: Receive = {
    case _: LogLockProposalEvent =>
    case command: InjectSessionEvents =>
      persistEvents(command.events) {
        sender() ! SessionEventsInjected
      }
    case RequestVoteValues(_, values) => retrieveVoteValues(values)
    case RequestUserVotedProposals(_) => retrieveUserVotedProposals()
    case Snapshot                     => saveSnapshot()
    case command: TransactionalUserHistoryEvent[_] =>
      persistEvent(command) { _ =>
        sender() ! LogAcknowledged
      }
    case command: UserHistoryEvent[_] =>
      persistEvent(command) { _ =>
        ()
      }
  }

  override def onRecoveryCompleted(): Unit = {}

  override def persistenceId: String = userId.value

  private def persistEvent[Event <: UserHistoryEvent[_]](event: Event)(andThen: Event => Unit): Unit = {
    persist(event) { e: Event =>
      log.debug("Persisted event {} for user {}", e.toString, persistenceId)
      newEventAdded(e)
      andThen(e)
    }
  }

  def persistEvents(events: Seq[UserHistoryEvent[_]])(andThen: => Unit): Unit = {

    if (events.nonEmpty) {
      val counter = new AtomicInteger()
      persistAll(collection.immutable.Seq(events: _*)) { e: UserHistoryEvent[_] =>
        log.debug("Persisted event {} for user {}", e.toString, persistenceId)
        newEventAdded(e)
        if (counter.incrementAndGet() == events.size) {
          andThen
        }
      }
    } else {
      andThen
    }
  }

  override val applyEvent: PartialFunction[UserHistoryEvent[_], Option[UserVotesAndQualifications]] = {
    case LogUserVoteEvent(_, _, UserAction(_, _, UserVote(proposalId, voteKey))) =>
      applyVoteEvent(proposalId, voteKey)
    case LogUserUnvoteEvent(_, _, UserAction(_, _, UserUnvote(proposalId, voteKey))) =>
      applyUnvoteEvent(proposalId, voteKey)
    case LogUserQualificationEvent(_, _, UserAction(_, _, UserQualification(proposalId, qualificationKey))) =>
      applyQualificationEvent(proposalId, qualificationKey)
    case LogUserUnqualificationEvent(_, _, UserAction(_, _, UserUnqualification(proposalId, qualificationKey))) =>
      applyUnqualificationEvent(proposalId, qualificationKey)
    case _ => state
  }

  def applyVoteEvent(proposalId: ProposalId, voteKey: VoteKey): Option[UserVotesAndQualifications] = {
    var nextState = state.getOrElse(UserVotesAndQualifications(Map.empty))
    if (nextState.votesAndQualifications.contains(proposalId)) {
      nextState = nextState.copy(votesAndQualifications = nextState.votesAndQualifications - proposalId)
    }
    nextState = nextState.copy(
      votesAndQualifications = nextState.votesAndQualifications + (proposalId -> VoteAndQualifications(
        voteKey,
        Seq.empty
      ))
    )
    Some(nextState)
  }

  def applyQualificationEvent(proposalId: ProposalId,
                              qualificationKey: QualificationKey): Option[UserVotesAndQualifications] = {

    var nextState = state.getOrElse(UserVotesAndQualifications(Map.empty))
    val newVoteAndQualifications = nextState.votesAndQualifications.get(proposalId).map { voteAndQualifications =>
      voteAndQualifications.copy(qualificationKeys = voteAndQualifications.qualificationKeys :+ qualificationKey)
    }
    newVoteAndQualifications.foreach { voteAndQualifications =>
      nextState = nextState.copy(
        votesAndQualifications = nextState.votesAndQualifications + (proposalId -> voteAndQualifications)
      )
    }
    Some(nextState)
  }

  def applyUnqualificationEvent(proposalId: ProposalId,
                                qualificationKey: QualificationKey): Option[UserVotesAndQualifications] = {

    var nextState = state.getOrElse(UserVotesAndQualifications(Map.empty))
    val newVoteAndQualifications = nextState.votesAndQualifications.get(proposalId).map { voteAndQualifications =>
      voteAndQualifications
        .copy(qualificationKeys = voteAndQualifications.qualificationKeys.filter(_ != qualificationKey))
    }
    newVoteAndQualifications.foreach { voteAndQualifications =>
      nextState = nextState.copy(
        votesAndQualifications = nextState.votesAndQualifications + (proposalId -> voteAndQualifications)
      )
    }
    Some(nextState)
  }

  def applyUnvoteEvent(proposalId: ProposalId, voteKey: VoteKey): Option[UserVotesAndQualifications] = {
    var nextState = state.getOrElse(UserVotesAndQualifications(Map.empty))
    if (nextState.votesAndQualifications.get(proposalId).exists(_.voteKey == voteKey)) {
      nextState = nextState.copy(votesAndQualifications = nextState.votesAndQualifications - proposalId)
    }
    Some(nextState)
  }

  private def retrieveVoteValues(proposalIds: Seq[ProposalId]): Unit = {
    sender() ! state
      .map(_.votesAndQualifications.filter { case (proposalId, _) => proposalIds.contains(proposalId) })
      .getOrElse(Map.empty)
  }

  private def retrieveUserVotedProposals(): Unit = {
    sender() ! state.map(_.votesAndQualifications).getOrElse(Map.empty).keys.toSeq
  }

}

object UserHistoryActor {
  final case class UserHistory(events: List[UserHistoryEvent[_]]) extends MakeSerializable

  object UserHistory {
    implicit val formatter: RootJsonFormat[UserHistory] = DefaultJsonProtocol.jsonFormat1(UserHistory.apply)
  }

  final case class UserVotesAndQualifications(votesAndQualifications: Map[ProposalId, VoteAndQualifications])
      extends MakeSerializable

  object UserVotesAndQualifications {
    implicit val formatter: RootJsonFormat[UserVotesAndQualifications] =
      DefaultJsonProtocol.jsonFormat1(UserVotesAndQualifications.apply)
  }

  final case class RequestVoteValues(userId: UserId, proposalIds: Seq[ProposalId]) extends UserRelatedEvent
  final case class RequestUserVotedProposals(userId: UserId) extends UserRelatedEvent
  final case class InjectSessionEvents(userId: UserId, events: Seq[UserHistoryEvent[_]]) extends UserRelatedEvent

  case class ReloadState(userId: UserId) extends UserRelatedEvent

  case object SessionEventsInjected

  case object LogAcknowledged
}
