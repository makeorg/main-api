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

import java.time.ZonedDateTime

import akka.actor.{ActorRef, PoisonPill}
import akka.pattern.ask
import akka.remote.Ack
import akka.util.Timeout
import org.make.api.extensions.MakeSettingsExtension
import org.make.api.sessionhistory.SessionHistoryActor._
import org.make.api.technical.MakePersistentActor
import org.make.api.technical.MakePersistentActor.Snapshot
import org.make.api.userhistory.UserHistoryActor.{
  InjectSessionEvents,
  LogAcknowledged,
  RequestUserVotedProposals,
  RequestVoteValues,
  _
}
import org.make.api.userhistory._
import org.make.core.history.HistoryActions._
import org.make.core.proposal.{ProposalId, QualificationKey}
import org.make.core.session._
import org.make.core.user.UserId
import org.make.core.{DateHelper, MakeSerializable, RequestContext}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

class SessionHistoryActor(userHistoryCoordinator: ActorRef, lockDuration: FiniteDuration)
    extends MakePersistentActor(classOf[SessionHistory], classOf[SessionHistoryEvent[_]])
    with MakeSettingsExtension {

  implicit val timeout: Timeout = defaultTimeout
  private val maxEvents: Int = settings.maxUserHistoryEvents
  private var locks: Map[ProposalId, LockVoteAction] = Map.empty
  private var deadline = Deadline.now
  private var lastEventDate: Option[ZonedDateTime] = None

  def stopSessionHistoryActor(): Unit = {
    persist(
      SaveLastEventDate(
        sessionId,
        RequestContext.empty,
        action = SessionAction(date = DateHelper.now(), actionType = "saveLastEventDate", arguments = lastEventDate)
      )
    ) { _ =>
      context.stop(self)
    }
  }

  private def isSessionExpired: Boolean =
    lastEventDate.exists(_.isBefore(DateHelper.now().minusMinutes(settings.SessionCookie.lifetime.toMinutes)))

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
    case GetSessionHistory(_)               => sender() ! state.getOrElse(SessionHistory(Nil))
    case GetCurrentSession(_, newSessionId) => retrieveOrExpireSession(newSessionId)
    case SessionHistoryEnvelope(_, command: TransactionalSessionHistoryEvent[_]) =>
      persistEvent(command) { _ =>
        sender() ! LogAcknowledged
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
    case ReleaseProposalForVote(_, proposalId) =>
      locks -= proposalId
      sender() ! LockReleased
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
    sender() ! LockReleased
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

  private def retrieveOrExpireSession(newSessionId: SessionId): Unit = {
    if (isSessionExpired) {
      persistEvent(
        SessionExpired(
          sessionId = sessionId,
          requestContext = RequestContext.empty,
          action = SessionAction(date = DateHelper.now(), actionType = "transformSession", arguments = newSessionId)
        )
      ) { event =>
        sender() ! CurrentSessionId(newSessionId)
        context.become(expired(event.action.arguments))
      }
    } else {
      lastEventDate = Some(DateHelper.now())
      sender() ! CurrentSessionId(sessionId)
    }
  }

  override def onRecoveryCompleted(): Unit = {
    def findEvent[T <: SessionHistoryEvent[_]](
      eventsList: Seq[SessionHistoryEvent[_]]
    )(implicit tag: ClassTag[T]): Option[T] = {
      eventsList.collectFirst {
        case event: T => event
      }
    }

    val allEvents: Seq[SessionHistoryEvent[_]] = state.toList.flatMap(_.events).sortBy(_.action.date)
    val saveLastDateEvent: Option[SaveLastEventDate] = findEvent[SaveLastEventDate](allEvents.reverse)

    lastEventDate = lastEventDate
      .orElse(saveLastDateEvent.flatMap(_.action.arguments))
      .orElse(allEvents.lastOption.map(_.action.date))

    val transformation: Option[SessionTransformed] = findEvent[SessionTransformed](allEvents)

    val expiredEvent: Option[SessionExpired] = findEvent[SessionExpired](allEvents)

    (expiredEvent, transformation) match {
      case (Some(event), _) => context.become(expired(event.action.arguments))
      case (_, Some(event)) => context.become(closed(event.action.arguments))
      case _                =>
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
    case GetSessionHistory(_)               => sender() ! state.getOrElse(SessionHistory(Nil))
    case GetCurrentSession(_, newSessionId) => retrieveOrExpireSession(newSessionId)
    case UserConnected(_, newUserId, _) =>
      if (newUserId != userId) {
        log.warning("Session {} has moved from user {} to user {}", persistenceId, userId.value, newUserId.value)
        context.become(closed(newUserId))
      }
      sender() ! Ack
    case SessionHistoryEnvelope(_, command: TransferableToUser[_]) =>
      userHistoryCoordinator.forward(UserHistoryEnvelope(userId, command.toUserHistoryEvent(userId)))
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
    case ReleaseProposalForVote(_, proposalId) =>
      locks -= proposalId
      sender() ! LockReleased
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

  def expired(newSessionId: SessionId): Receive = {
    case GetCurrentSession(_, _) => sender() ! CurrentSessionId(newSessionId)
    case _: SessionRelatedEvent  => sender() ! SessionIsExpired(newSessionId)
    case Snapshot                => saveSnapshot()
  }

  private def retrieveVotedProposals(proposalsIds: Option[Seq[ProposalId]], limit: Int, skip: Int): Unit = {
    val votedProposals: Seq[ProposalId] = voteByProposalId(voteActions()).toSeq.sortBy {
      case (_, votesAndQualifications) => votesAndQualifications.date
    }.collect {
      case (proposalId, _) if proposalsIds.forall(_.contains(proposalId)) => proposalId
    }.slice(skip, limit)

    sender() ! SessionVotedProposals(votedProposals)
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

    sender() ! SessionVotesValues(voteAndQualifications)
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
      lastEventDate = Some(transformed.action.date)
      state.map(_.copy(events = List(transformed))).orElse(Some(SessionHistory(List(transformed))))
    case event: SaveLastEventDate =>
      lastEventDate = event.action.arguments
      state.map { s =>
        s.copy(events = (event :: s.events).take(maxEvents))
      }.orElse(Some(SessionHistory(List(event))))
    case event: SessionHistoryEvent[_] =>
      lastEventDate = Some(event.action.date)
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

  final case class SessionClosed(sender: ActorRef) extends SessionHistoryActorProtocol

  final case class SessionVotedProposals(proposals: Seq[ProposalId])
      extends SessionHistoryActorProtocol
      with VotedProposals
  final case class SessionVotesValues(votesValues: Map[ProposalId, VoteAndQualifications])
      extends SessionHistoryActorProtocol
      with VotesValues

  final case class CurrentSessionId(sessionId: SessionId) extends SessionHistoryActorProtocol
}
