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

import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Stash}
import akka.pattern.pipe
import akka.persistence.SnapshotOffer
import akka.persistence.query.EventEnvelope
import org.make.api.technical.MakePersistentActor.Snapshot
import org.make.api.technical.{ActorReadJournalComponent, MakePersistentActor}
import org.make.api.userhistory.UserHistoryActor._
import org.make.core.MakeSerializable
import org.make.core.history.HistoryActions._
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.user._
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserHistoryActor
    extends MakePersistentActor(classOf[UserVotesAndQualifications], classOf[UserHistoryEvent[_]])
    with ActorLogging
    with ActorReadJournalComponent
    with Stash {

  def userId: UserId = UserId(self.path.name)
  override def persistenceId: String = userId.value

  // Have an initial state to allow snapshotting, even if the user has no vote-related event
  state = Some(UserVotesAndQualifications(Map.empty))

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
    case RequestUserVotedProposals(_, filterVotes, filterQualifications, proposalsIds) =>
      retrieveUserVotedProposals(filterVotes, filterQualifications, proposalsIds, Int.MaxValue, 0)
    case RequestUserVotedProposalsPaginate(_, filterVotes, filterQualifications, proposalsIds, limit, skip) =>
      retrieveUserVotedProposals(filterVotes, filterQualifications, proposalsIds, limit, skip)
    case Snapshot => saveSnapshot()
    //TODO: remove
    case SnapshotUser(_) => saveSnapshot()
    case UserHistoryEnvelope(_, command: TransactionalUserHistoryEvent[_]) =>
      persistEvent(command) { _ =>
        sender() ! LogAcknowledged
      }
    case UserHistoryEnvelope(_, command: UserHistoryEvent[_]) =>
      persistEvent(command) { _ =>
        ()
      }
    case _: ReloadState => reloadState()
  }

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
      persistAll(events) { e: UserHistoryEvent[_] =>
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

  override def applyEvent: PartialFunction[UserHistoryEvent[_], Option[UserVotesAndQualifications]] =
    applyEventOnState(this.state)

  def applyEventOnState(
    userHistoryState: Option[UserVotesAndQualifications]
  ): PartialFunction[UserHistoryEvent[_], Option[UserVotesAndQualifications]] = {
    case LogUserVoteEvent(_, _, UserAction(date, _, UserVote(proposalId, voteKey, voteTrust))) =>
      applyVoteEvent(userHistoryState, proposalId, voteKey, date, voteTrust)
    case LogUserUnvoteEvent(_, _, UserAction(_, _, UserUnvote(proposalId, voteKey, _))) =>
      applyUnvoteEvent(userHistoryState, proposalId, voteKey)
    case LogUserQualificationEvent(
        _,
        _,
        UserAction(_, _, UserQualification(proposalId, qualificationKey, voteTrust))
        ) =>
      applyQualificationEvent(userHistoryState, proposalId, qualificationKey, voteTrust)
    case LogUserUnqualificationEvent(_, _, UserAction(_, _, UserUnqualification(proposalId, qualificationKey, _))) =>
      applyUnqualificationEvent(userHistoryState, proposalId, qualificationKey)
    case _ => userHistoryState
  }

  def applyVoteEvent(
    userHistoryState: Option[UserVotesAndQualifications],
    proposalId: ProposalId,
    voteKey: VoteKey,
    voteDate: ZonedDateTime,
    voteTrust: VoteTrust
  ): Option[UserVotesAndQualifications] = {
    var nextState = userHistoryState.getOrElse(UserVotesAndQualifications(Map.empty))
    if (nextState.votesAndQualifications.contains(proposalId)) {
      nextState = nextState.copy(votesAndQualifications = nextState.votesAndQualifications - proposalId)
    }
    nextState =
      nextState.copy(votesAndQualifications = nextState.votesAndQualifications + (proposalId -> VoteAndQualifications(
        voteKey,
        Map.empty,
        voteDate,
        voteTrust
      ))
      )
    Some(nextState)
  }

  def applyQualificationEvent(
    userHistoryState: Option[UserVotesAndQualifications],
    proposalId: ProposalId,
    qualificationKey: QualificationKey,
    voteTrust: VoteTrust
  ): Option[UserVotesAndQualifications] = {

    var nextState = userHistoryState.getOrElse(UserVotesAndQualifications(Map.empty))
    val newVoteAndQualifications = nextState.votesAndQualifications.get(proposalId).map { voteAndQualifications =>
      voteAndQualifications
        .copy(qualificationKeys = voteAndQualifications.qualificationKeys + (qualificationKey -> voteTrust))
    }
    newVoteAndQualifications.foreach { voteAndQualifications =>
      nextState =
        nextState.copy(votesAndQualifications = nextState.votesAndQualifications + (proposalId -> voteAndQualifications)
        )
    }
    Some(nextState)
  }

  def applyUnqualificationEvent(
    userHistoryState: Option[UserVotesAndQualifications],
    proposalId: ProposalId,
    qualificationKey: QualificationKey
  ): Option[UserVotesAndQualifications] = {

    var nextState = userHistoryState.getOrElse(UserVotesAndQualifications(Map.empty))
    val newVoteAndQualifications = nextState.votesAndQualifications.get(proposalId).map { voteAndQualifications =>
      voteAndQualifications
        .copy(qualificationKeys = voteAndQualifications.qualificationKeys.filter {
          case (key, _) => key != qualificationKey
        })
    }
    newVoteAndQualifications.foreach { voteAndQualifications =>
      nextState =
        nextState.copy(votesAndQualifications = nextState.votesAndQualifications + (proposalId -> voteAndQualifications)
        )
    }
    Some(nextState)
  }

  def applyUnvoteEvent(
    userHistoryState: Option[UserVotesAndQualifications],
    proposalId: ProposalId,
    voteKey: VoteKey
  ): Option[UserVotesAndQualifications] = {
    var nextState = userHistoryState.getOrElse(UserVotesAndQualifications(Map.empty))
    if (nextState.votesAndQualifications.get(proposalId).exists(_.voteKey == voteKey)) {
      nextState = nextState.copy(votesAndQualifications = nextState.votesAndQualifications - proposalId)
    }
    Some(nextState)
  }

  def applyEventFromState(
    currentState: Option[UserVotesAndQualifications],
    event: Option[UserHistoryEvent[_]]
  ): Option[UserVotesAndQualifications] = {
    event.map(e => applyEventOnState(currentState)(e)).getOrElse(currentState)
  }

  def reloadState(): Unit = {
    implicit val actorSystem: ActorSystem = context.system
    val newState: Option[UserVotesAndQualifications] = None
    userJournal
      .currentEventsByPersistenceId(this.persistenceId, 0, Long.MaxValue)
      .map {
        case EventEnvelope(_, _, _, event: UserHistoryEvent[_]) => Some(event)
        case _                                                  => None
      }
      .runFold(newState)(applyEventFromState)
      .map(s => MigrationCompletedWithNewState(s))
      .recoverWith {
        case e => Future.successful(MigrationFailure(e))
      }
      .pipeTo(self)

    context.become(migrating(Seq.empty))
  }

  def migrating(pendingEvents: Seq[(ActorRef, Any)]): Receive = {
    case MigrationCompletedWithNewState(newState) =>
      context.become(receiveCommand)
      state = newState
      self ! Snapshot
      pendingEvents.foreach {
        case (messageSender, e) => self.tell(e, messageSender)
      }
    case MigrationFailure(e) => log.error(e, e.getMessage)
    case _: ReloadState      =>
    case event               => context.become(migrating(pendingEvents :+ sender() -> event))
  }

  private def retrieveVoteValues(proposalIds: Seq[ProposalId]): Unit = {
    val votesValues: Map[ProposalId, VoteAndQualifications] = state
      .map(_.votesAndQualifications.filter {
        case (proposalId, _) => proposalIds.contains(proposalId)
      })
      .getOrElse(Map.empty)

    sender() ! UserVotesValues(votesValues)
  }

  private def retrieveUserVotedProposals(
    filterVotes: Option[Seq[VoteKey]],
    filterQualifications: Option[Seq[QualificationKey]],
    proposalsIds: Option[Seq[ProposalId]],
    limit: Int,
    skip: Int
  ): Unit = {
    val votedProposals: Seq[ProposalId] = state
      .map(_.votesAndQualifications.filter {
        case (proposalId, votesAndQualifications) =>
          proposalsIds.forall(_.contains(proposalId)) &&
            filterVotes.forall(_.contains(votesAndQualifications.voteKey)) &&
            (filterQualifications.isEmpty || filterQualifications.exists { qualifications =>
              votesAndQualifications.qualificationKeys.exists { case (value, _) => qualifications.contains(value) }
            }) && votesAndQualifications.trust.isTrusted
      })
      .getOrElse(Map.empty)
      .toSeq
      .sortBy { case (_, votesAndQualifications) => votesAndQualifications.date }
      .map { case (proposalId, _) => proposalId }
      .slice(skip, limit)

    sender() ! UserVotedProposals(votedProposals)
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
  final case class RequestUserVotedProposals(
    userId: UserId,
    filterVotes: Option[Seq[VoteKey]] = None,
    filterQualifications: Option[Seq[QualificationKey]] = None,
    proposalsIds: Option[Seq[ProposalId]] = None
  ) extends UserRelatedEvent
  final case class RequestUserVotedProposalsPaginate(
    userId: UserId,
    filterVotes: Option[Seq[VoteKey]] = None,
    filterQualifications: Option[Seq[QualificationKey]] = None,
    proposalsIds: Option[Seq[ProposalId]] = None,
    limit: Int,
    skip: Int
  ) extends UserRelatedEvent
  final case class InjectSessionEvents(userId: UserId, events: Seq[UserHistoryEvent[_]]) extends UserRelatedEvent

  case class ReloadState(userId: UserId) extends UserRelatedEvent

  case object SessionEventsInjected extends UserHistoryActorProtocol

  case object LogAcknowledged extends UserHistoryActorProtocol

  case class MigrationCompletedWithNewState(newState: Option[UserVotesAndQualifications])
      extends UserHistoryActorProtocol
  case class MigrationFailure(e: Throwable) extends UserHistoryActorProtocol

  final case class UserVotedProposals(proposals: Seq[ProposalId]) extends UserHistoryActorProtocol with VotedProposals
  final case class UserVotesValues(votesValues: Map[ProposalId, VoteAndQualifications])
      extends UserHistoryActorProtocol
      with VotesValues
}
