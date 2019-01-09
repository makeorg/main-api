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
import org.make.api.userhistory.UserHistoryActor.{
  InjectSessionEvents,
  LogAcknowledged,
  RequestUserVotedProposals,
  RequestVoteValues
}
import org.make.api.userhistory.UserHistoryEvent
import org.make.core.history.HistoryActions._
import org.make.core.proposal.{ProposalId, QualificationKey}
import org.make.core.session._
import org.make.core.user.UserId
import org.make.core.{DateHelper, MakeSerializable, RequestContext}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class SessionHistoryActor(userHistoryCoordinator: ActorRef)
    extends MakePersistentActor(classOf[SessionHistory], classOf[SessionHistoryEvent[_]])
    with MakeSettingsExtension {

  implicit val timeout: Timeout = defaultTimeout
  private val maxEvents: Int = settings.maxUserHistoryEvents

  def sessionId: SessionId = SessionId(self.path.name)

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
    case RequestSessionVoteValues(_, proposalIds) => retrieveVoteValues(proposalIds)
    case RequestSessionVotedProposals(_)          => retrieveVotedProposals()
    case UserConnected(_, userId)                 => transformSession(userId)
    case Snapshot                                 => saveSnapshot()
    case StopSession(_)                           => self ! PoisonPill
  }

  override def onRecoveryCompleted(): Unit = {
    val transformation =
      state.toList.flatMap(_.events).find(_.isInstanceOf[SessionTransformed]).map(_.asInstanceOf[SessionTransformed])

    transformation.foreach { event =>
      context.become(closed(event.action.arguments))
    }
  }

  private def transformSession(userId: UserId): Unit = {
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

    context.become(transforming(userId, Seq.empty))
  }

  def closed(userId: UserId): Receive = {
    case GetSessionHistory(_) => sender() ! state.getOrElse(SessionHistory(Nil))
    case UserConnected(_, newUserId) =>
      if (newUserId != userId) {
        context.become(closed(newUserId))
      }
      sender() ! LogAcknowledged
    case command: TransferableToUser[_]  => userHistoryCoordinator.forward(command.toUserHistoryEvent(userId))
    case RequestSessionVotedProposals(_) => userHistoryCoordinator.forward(RequestUserVotedProposals(userId))
    case RequestSessionVoteValues(_, proposalIds) =>
      userHistoryCoordinator.forward(RequestVoteValues(userId, proposalIds))
    case command: SessionHistoryAction =>
      log.warning("closed session {} with userId {} received command {}", persistenceId, userId.value, command.toString)
    case Snapshot       => saveSnapshot()
    case StopSession(_) => self ! PoisonPill
  }

  def transforming(userId: UserId, pendingEvents: Seq[(ActorRef, Any)]): Receive = {
    case SessionClosed(originalSender) =>
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
    case Snapshot => saveSnapshot()
    case event    => context.become(transforming(userId, pendingEvents :+ sender() -> event))
  }

  private def retrieveVotedProposals(): Unit = {
    sender() ! voteByProposalId(voteActions()).keys.toSeq
  }

  private def retrieveVoteValues(proposalIds: Seq[ProposalId]): Unit = {
    val voteRelatedActions: Seq[VoteRelatedAction] = actions(proposalIds)

    val voteAndQualifications: Map[ProposalId, VoteAndQualifications] = voteByProposalId(voteRelatedActions).map {
      case (proposalId, voteAction) =>
        proposalId -> VoteAndQualifications(
          voteAction.key,
          qualifications(voteRelatedActions).getOrElse(proposalId, Seq.empty).sortBy(_.shortName),
          voteAction.date
        )
    }
    sender() ! voteAndQualifications
  }

  private def actions(proposalIds: Seq[ProposalId]): Seq[VoteRelatedAction] = state.toSeq.flatMap(_.events).flatMap {
    case LogSessionVoteEvent(_, _, SessionAction(date, _, SessionVote(proposalId, voteKey)))
        if proposalIds.contains(proposalId) =>
      Some(VoteAction(proposalId, date, voteKey))
    case LogSessionUnvoteEvent(_, _, SessionAction(date, _, SessionUnvote(proposalId, voteKey)))
        if proposalIds.contains(proposalId) =>
      Some(UnvoteAction(proposalId, date, voteKey))
    case LogSessionQualificationEvent(_, _, SessionAction(date, _, SessionQualification(proposalId, qualificationKey)))
        if proposalIds.contains(proposalId) =>
      Some(QualificationAction(proposalId, date, qualificationKey))
    case LogSessionUnqualificationEvent(
        _,
        _,
        SessionAction(date, _, SessionUnqualification(proposalId, qualificationKey))
        ) if proposalIds.contains(proposalId) =>
      Some(UnqualificationAction(proposalId, date, qualificationKey))
    case _ => None
  }

  private def voteActions(): Seq[VoteRelatedAction] = state.toSeq.flatMap(_.events).flatMap {
    case LogSessionVoteEvent(_, _, SessionAction(date, _, SessionVote(proposalId, voteKey))) =>
      Some(VoteAction(proposalId, date, voteKey))
    case LogSessionUnvoteEvent(_, _, SessionAction(date, _, SessionUnvote(proposalId, voteKey))) =>
      Some(UnvoteAction(proposalId, date, voteKey))
    case LogSessionQualificationEvent(
        _,
        _,
        SessionAction(date, _, SessionQualification(proposalId, qualificationKey))
        ) =>
      Some(QualificationAction(proposalId, date, qualificationKey))
    case LogSessionUnqualificationEvent(
        _,
        _,
        SessionAction(date, _, SessionUnqualification(proposalId, qualificationKey))
        ) =>
      Some(UnqualificationAction(proposalId, date, qualificationKey))
    case _ => None
  }

  private def voteByProposalId(actions: Seq[VoteRelatedAction]) = {
    actions.filter {
      case _: GenericVoteAction => true
      case _                    => false
    }.groupBy(_.proposalId)
      .map {
        case (proposalId, voteActions) => proposalId -> voteActions.maxBy(_.date.toString)
      }
      .filter {
        case (_, _: VoteAction) => true
        case _                  => false
      }
      .map {
        case (proposalId, action) => proposalId -> action.asInstanceOf[VoteAction]
      }
  }

  private def qualifications(actions: Seq[VoteRelatedAction]): Map[ProposalId, Seq[QualificationKey]] = {
    actions.filter {
      case _: GenericQualificationAction => true
      case _                             => false
    }.groupBy(action => (action.proposalId, action.asInstanceOf[GenericQualificationAction].key))
      .map {
        case (groupKey, qualificationAction) => groupKey -> qualificationAction.maxBy(_.date.toString)
      }
      .filter {
        case (_, _: QualificationAction) => true
        case _                           => false
      }
      .toList
      .map {
        case ((proposalId, key), _) => proposalId -> key
      }
      .groupBy {
        case (proposalId, _) => proposalId
      }
      .map {
        case (proposalId, qualificationList) =>
          proposalId -> qualificationList.map {
            case (_, key) => key
          }
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
