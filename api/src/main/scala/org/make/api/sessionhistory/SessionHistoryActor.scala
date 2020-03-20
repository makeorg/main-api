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

import akka.actor.{ActorRef, PoisonPill}
import akka.pattern.ask
import akka.util.Timeout
import org.make.api.extensions.MakeSettingsExtension
import org.make.api.sessionhistory.SessionHistoryActor.{SessionClosed, SessionHistory}
import org.make.api.technical.MakePersistentActor
import org.make.api.technical.MakePersistentActor.Snapshot
import org.make.api.userhistory.UserHistoryActor._
import org.make.api.userhistory.{UserConnectedEvent, UserHistoryEvent}
import org.make.core.history.HistoryActions._
import org.make.core.proposal.{ProposalId, QualificationKey}
import org.make.core.session._
import org.make.core.user.UserId
import org.make.core.{DateHelper, MakeSerializable, RequestContext}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.util.{Failure, Success}

class SessionHistoryActor(userHistoryCoordinator: ActorRef, lockDuration: FiniteDuration)
    extends MakePersistentActor(classOf[SessionHistory], classOf[SessionHistoryEvent[_]])
    with MakeSettingsExtension {

  implicit val timeout: Timeout = defaultTimeout
  private val maxEvents: Int = settings.maxUserHistoryEvents
  private var locks: Map[ProposalId, LockVoteAction] = Map.empty
  private var deadline = Deadline.now

  def sessionId: SessionId = SessionId(self.path.name)

  private def removeLocksIfExpired(): Unit = {
    if (deadline.isOverdue()) {
      locks = Map.empty
    }
  }

  private def prorogateDeadline(): Unit = {
    deadline = Deadline.now + lockDuration
  }

  override def receiveCommand: Receive = {
    case GetSessionHistory(_) => sender() ! state.getOrElse(SessionHistory(Nil))
    case command: TransactionalSessionHistoryEvent[_] =>
      persistEvent(command) { _ =>
        sender() ! LogAcknowledged
      }
    case command: TransferableToUser[_] =>
      persistEvent(command) { _ =>
        ()
      }
    case RequestSessionVoteValues(_, proposalIds)      => retrieveVoteValues(proposalIds)
    case RequestSessionVotedProposals(_, proposalsIds) => retrieveVotedProposals(proposalsIds, Int.MaxValue, 0)
    case RequestSessionVotedProposalsPaginate(_, proposalsIds, limit, skip) =>
      retrieveVotedProposals(proposalsIds, limit, skip)
    case UserConnected(_, userId, requestContext) => transformSession(userId, requestContext)
    case Snapshot                                 => saveSnapshot()
    case StopSession(_)                           => self ! PoisonPill
    case LockProposalForVote(_, proposalId)       => acquireLockForVotesIfPossible(proposalId)
    case LockProposalForQualification(_, proposalId, qualification) =>
      acquireLockForQualificationIfPossible(proposalId, qualification)
    case ReleaseProposalForVote(_, proposalId) => locks -= proposalId
    case ReleaseProposalForQualification(_, proposalId, qualification) =>
      releaseLockForQualification(proposalId, qualification)
  }

  private def releaseLockForQualification(proposalId: ProposalId, key: QualificationKey): Unit = {
    locks.get(proposalId) match {
      case Some(ChangeQualifications(keys)) =>
        val remainingLocks = keys.filter(_ != key)
        if (remainingLocks.isEmpty) {
          locks -= proposalId
        } else {
          locks += proposalId -> ChangeQualifications(remainingLocks)
        }
      case _ =>
    }
  }

  private def acquireLockForVotesIfPossible(proposalId: ProposalId): Unit = {
    removeLocksIfExpired()
    locks.get(proposalId) match {
      case None =>
        locks += proposalId -> Vote
        prorogateDeadline()
        sender() ! LockAcquired
      case Some(_) =>
        log.warning("Trying to vote on a locked proposal {} for session {}", proposalId.value, persistenceId)
        sender() ! LockAlreadyAcquired
    }
  }

  private def acquireLockForQualificationIfPossible(proposalId: ProposalId, key: QualificationKey): Unit = {
    removeLocksIfExpired()
    locks.get(proposalId) match {
      case None =>
        locks += proposalId -> ChangeQualifications(Seq(key))
        prorogateDeadline()
        sender() ! LockAcquired
      case Some(Vote) =>
        log.warning("Trying to qualify on a locked proposal {} for session {} (vote)", proposalId.value, persistenceId)
        sender() ! LockAlreadyAcquired
      case Some(ChangeQualifications(keys)) if keys.contains(key) =>
        log.warning(
          "Trying to qualify on a locked proposal {} for session {} (same qualification)",
          proposalId.value,
          persistenceId
        )
        sender() ! LockAlreadyAcquired
      case Some(ChangeQualifications(keys)) =>
        locks += proposalId -> ChangeQualifications(keys ++ Seq(key))
        prorogateDeadline()
        sender() ! LockAcquired
    }
  }

  override def onRecoveryCompleted(): Unit = {
    val transformation =
      state.toList.flatMap(_.events).find(_.isInstanceOf[SessionTransformed]).map(_.asInstanceOf[SessionTransformed])

    transformation.foreach { event =>
      context.become(closed(event.action.arguments))
    }
  }

  private def transformSession(userId: UserId, requestContext: RequestContext): Unit = {
    val originalSender = sender()
    log.debug(
      "Transforming session {} to user {} with events {}",
      persistenceId,
      userId.value,
      state.toSeq.flatMap(_.events).map(_.toString).mkString(", ")
    )

    val events: Seq[UserHistoryEvent[_]] = state.toSeq.flatMap(_.events).sortBy(_.action.date.toString).flatMap {
      case event: TransferableToUser[_] => Seq[UserHistoryEvent[_]](event.toUserHistoryEvent(userId))
      case _                            => Seq.empty[UserHistoryEvent[_]]
    }

    (userHistoryCoordinator ? InjectSessionEvents(userId, events)).onComplete {
      case Success(_) => self ! SessionClosed(originalSender)
      case Failure(e) =>
        // TODO: handle it gracefully
        log.error(e, "error while transforming session")
        self ! SessionClosed(originalSender)
    }

    context.become(transforming(userId, Seq.empty, requestContext))
  }

  def closed(userId: UserId): Receive = {
    case GetSessionHistory(_) => sender() ! state.getOrElse(SessionHistory(Nil))
    case UserConnected(_, newUserId, _) =>
      if (newUserId != userId) {
        log.warning("Session {} has moved from user {} to user {}", persistenceId, userId.value, newUserId.value)
        context.become(closed(newUserId))
      }
      sender() ! LogAcknowledged
    case command: TransferableToUser[_] => userHistoryCoordinator.forward(command.toUserHistoryEvent(userId))
    case RequestSessionVotedProposals(_, proposalsIds) =>
      userHistoryCoordinator.forward(RequestUserVotedProposals(userId = userId, proposalsIds = proposalsIds))
    case RequestSessionVotedProposalsPaginate(_, proposalsIds, limit, skip) =>
      userHistoryCoordinator.forward(
        RequestUserVotedProposalsPaginate(userId = userId, proposalsIds = proposalsIds, limit = limit, skip = skip)
      )
    case RequestSessionVoteValues(_, proposalIds) =>
      userHistoryCoordinator.forward(RequestVoteValues(userId, proposalIds))
    case command: SessionHistoryAction =>
      log.warning("closed session {} with userId {} received command {}", persistenceId, userId.value, command.toString)
    case Snapshot                           => saveSnapshot()
    case StopSession(_)                     => self ! PoisonPill
    case LockProposalForVote(_, proposalId) => acquireLockForVotesIfPossible(proposalId)
    case LockProposalForQualification(_, proposalId, qualification) =>
      acquireLockForQualificationIfPossible(proposalId, qualification)
    case ReleaseProposalForVote(_, proposalId) => locks -= proposalId
    case ReleaseProposalForQualification(_, proposalId, qualification) =>
      releaseLockForQualification(proposalId, qualification)
  }

  def transforming(userId: UserId, pendingEvents: Seq[(ActorRef, Any)], requestContext: RequestContext): Receive = {
    case SessionClosed(originalSender) =>
      context.system.eventStream.publish(
        UserConnectedEvent(
          connectedUserId = Some(userId),
          eventDate = DateHelper.now(),
          userId = userId,
          requestContext = requestContext
        )
      )
      persistEvent(
        SessionTransformed(
          sessionId = sessionId,
          requestContext = RequestContext.empty,
          action = SessionAction(date = DateHelper.now(), actionType = "transformSession", arguments = userId)
        )
      ) { event =>
        context.become(closed(event.action.arguments))
        originalSender ! event
        self ! Snapshot

        pendingEvents.foreach {
          case (messageSender, e) => self.tell(e, messageSender)
        }
      }
    case Snapshot                           => saveSnapshot()
    case LockProposalForVote(_, proposalId) => acquireLockForVotesIfPossible(proposalId)
    case LockProposalForQualification(_, proposalId, qualification) =>
      acquireLockForQualificationIfPossible(proposalId, qualification)
    case ReleaseProposalForVote(_, proposalId) => locks -= proposalId
    case ReleaseProposalForQualification(_, proposalId, qualification) =>
      releaseLockForQualification(proposalId, qualification)
    case event => context.become(transforming(userId, pendingEvents :+ sender() -> event, requestContext))
  }

  private def retrieveVotedProposals(proposalsIds: Option[Seq[ProposalId]], limit: Int, skip: Int): Unit = {
    val votedProposals: Seq[ProposalId] = voteByProposalId(voteActions()).toSeq.sortBy {
      case (_, votesAndQualifications) => votesAndQualifications.date
    }.collect {
      case (proposalId, _) if proposalsIds.forall(_.contains(proposalId)) => proposalId
    }.slice(skip, limit)

    sender() ! votedProposals
  }

  private def retrieveVoteValues(proposalIds: Seq[ProposalId]): Unit = {
    val voteRelatedActions: Seq[VoteRelatedAction] = actions(proposalIds)
    val voteAndQualifications: Map[ProposalId, VoteAndQualifications] = voteByProposalId(voteRelatedActions).map {
      case (proposalId, voteAction) =>
        proposalId -> VoteAndQualifications(
          voteAction.key,
          qualifications(voteRelatedActions).getOrElse(proposalId, Map.empty),
          voteAction.date,
          voteAction.trust
        )
    }

    sender() ! voteAndQualifications
  }

  private def actions(proposalIds: Seq[ProposalId]): Seq[VoteRelatedAction] = state.toSeq.flatMap(_.events).flatMap {
    case LogSessionVoteEvent(_, _, SessionAction(date, _, SessionVote(proposalId, voteKey, trust)))
        if proposalIds.contains(proposalId) =>
      Some(VoteAction(proposalId, date, voteKey, trust))
    case LogSessionUnvoteEvent(_, _, SessionAction(date, _, SessionUnvote(proposalId, voteKey, trust)))
        if proposalIds.contains(proposalId) =>
      Some(UnvoteAction(proposalId, date, voteKey, trust))
    case LogSessionQualificationEvent(
        _,
        _,
        SessionAction(date, _, SessionQualification(proposalId, qualificationKey, trust))
        ) if proposalIds.contains(proposalId) =>
      Some(QualificationAction(proposalId, date, qualificationKey, trust))
    case LogSessionUnqualificationEvent(
        _,
        _,
        SessionAction(date, _, SessionUnqualification(proposalId, qualificationKey, trust))
        ) if proposalIds.contains(proposalId) =>
      Some(UnqualificationAction(proposalId, date, qualificationKey, trust))
    case _ => None
  }

  private def voteActions(): Seq[VoteRelatedAction] = state.toSeq.flatMap(_.events).flatMap {
    case LogSessionVoteEvent(_, _, SessionAction(date, _, SessionVote(proposalId, voteKey, trust))) =>
      Some(VoteAction(proposalId, date, voteKey, trust))
    case LogSessionUnvoteEvent(_, _, SessionAction(date, _, SessionUnvote(proposalId, voteKey, trust))) =>
      Some(UnvoteAction(proposalId, date, voteKey, trust))
    case LogSessionQualificationEvent(
        _,
        _,
        SessionAction(date, _, SessionQualification(proposalId, qualificationKey, trust))
        ) =>
      Some(QualificationAction(proposalId, date, qualificationKey, trust))
    case LogSessionUnqualificationEvent(
        _,
        _,
        SessionAction(date, _, SessionUnqualification(proposalId, qualificationKey, trust))
        ) =>
      Some(UnqualificationAction(proposalId, date, qualificationKey, trust))
    case _ => None
  }

  private def voteByProposalId(actions: Seq[VoteRelatedAction]): Map[ProposalId, VoteAction] = {
    actions.filter {
      case _: GenericVoteAction => true
      case _                    => false
    }.groupBy(_.proposalId)
      .map {
        case (proposalId, voteActions) =>
          proposalId -> voteActions.maxBy(_.date.toString).asInstanceOf[GenericVoteAction]
      }
      .filter {
        case (_, value) => value.isInstanceOf[VoteAction]
      }
      .map {
        case (k, value) => k -> value.asInstanceOf[VoteAction]
      }
  }

  private def qualifications(actions: Seq[VoteRelatedAction]): Map[ProposalId, Map[QualificationKey, VoteTrust]] = {
    actions.filter {
      case _: GenericQualificationAction => true
      case _                             => false
    }.groupBy { action =>
      val qualificationAction = action.asInstanceOf[GenericQualificationAction]
      (qualificationAction.proposalId, qualificationAction.key)
    }.map {
      case (groupKey, qualificationAction) => groupKey -> qualificationAction.maxBy(_.date.toString)
    }.filter {
      case (_, v) => v.isInstanceOf[QualificationAction]
    }.toList.map {
      case ((proposalId, key), action) => proposalId -> (key -> action.trust)
    }.groupBy {
      case (proposalId, _) => proposalId
    }.map {
      case (proposalId, qualificationList) =>
        proposalId -> qualificationList.map {
          case (_, key) => key
        }.toMap
    }
  }

  override def persistenceId: String = sessionId.value

  private def persistEvent[Event <: SessionHistoryEvent[_]](event: Event)(andThen: Event => Unit): Unit = {
    if (state.isEmpty) {
      state = Some(SessionHistory(Nil))
    }
    persist(event) { e: Event =>
      state = applyEvent(e)
      andThen(e)
    }
  }

  override val applyEvent: PartialFunction[SessionHistoryEvent[_], Option[SessionHistory]] = {
    case transformed: SessionTransformed =>
      state.map(_.copy(events = List(transformed))).orElse(Some(SessionHistory(List(transformed))))
    case event =>
      state.map { s =>
        s.copy(events = (event :: s.events).take(maxEvents))
      }.orElse(Some(SessionHistory(List(event))))
  }
}

object SessionHistoryActor {
  case class SessionHistory(events: List[SessionHistoryEvent[_]]) extends MakeSerializable

  object SessionHistory {
    implicit val persister: RootJsonFormat[SessionHistory] = DefaultJsonProtocol.jsonFormat1(SessionHistory.apply)
  }

  final case class SessionClosed(sender: ActorRef)

}
