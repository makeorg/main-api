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

import org.make.api.userhistory._
import org.make.core.SprayJsonFormatters._
import org.make.core.history.HistoryActions.VoteTrust
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.session._
import org.make.core.user.UserId
import org.make.core.{MakeSerializable, RequestContext}
import spray.json._
import spray.json.DefaultJsonProtocol._

final case class SessionAction[T](date: ZonedDateTime, actionType: String, arguments: T)

object SessionAction {
  implicit def sessionActionSessionRegisteredFormatted[T](
    implicit formatter: JsonFormat[T]
  ): RootJsonFormat[SessionAction[T]] =
    DefaultJsonProtocol.jsonFormat3[ZonedDateTime, String, T, SessionAction[T]](
      (date: ZonedDateTime, action: String, parameter: T) => SessionAction[T](date, action, parameter)
    )
}

trait SessionRelatedEvent {
  def sessionId: SessionId
}

sealed trait SessionHistoryEvent[T] extends SessionRelatedEvent with MakeSerializable with Product {
  def requestContext: RequestContext
  def action: SessionAction[T]
}

sealed trait TransactionalSessionHistoryEvent[T] extends SessionHistoryEvent[T]

sealed trait TransferableToUser[T] extends SessionHistoryEvent[T] {

  def toUserHistoryEvent(userId: UserId): UserHistoryEvent[_]

}

object SessionHistoryEvent {
  implicit val format: RootJsonFormat[SessionHistoryEvent[_]] =
    new RootJsonFormat[SessionHistoryEvent[_]] {
      override def read(json: JsValue): SessionHistoryEvent[_] = {
        json.asJsObject.getFields("type") match {
          case Seq(JsString("SessionTransformed"))             => json.convertTo[SessionTransformed]
          case Seq(JsString("LogSessionSearchProposalsEvent")) => json.convertTo[LogSessionSearchProposalsEvent]
          case Seq(JsString("LogSessionVoteEvent"))            => json.convertTo[LogSessionVoteEvent]
          case Seq(JsString("LogSessionUnvoteEvent"))          => json.convertTo[LogSessionUnvoteEvent]
          case Seq(JsString("LogSessionQualificationEvent"))   => json.convertTo[LogSessionQualificationEvent]
          case Seq(JsString("LogSessionUnqualificationEvent")) => json.convertTo[LogSessionUnqualificationEvent]
          case Seq(JsString("LogSessionStartSequenceEvent"))   => json.convertTo[LogSessionStartSequenceEvent]
        }
      }

      override def write(obj: SessionHistoryEvent[_]): JsObject = {
        JsObject((obj match {
          case event: SessionTransformed             => event.toJson
          case event: LogSessionSearchProposalsEvent => event.toJson
          case event: LogSessionVoteEvent            => event.toJson
          case event: LogSessionUnvoteEvent          => event.toJson
          case event: LogSessionQualificationEvent   => event.toJson
          case event: LogSessionUnqualificationEvent => event.toJson
          case event: LogSessionStartSequenceEvent   => event.toJson
        }).asJsObject.fields + ("type" -> JsString(obj.productPrefix)))
      }
    }
}

final case class SessionSearchParameters(term: String)

object SessionSearchParameters {
  implicit val format: RootJsonFormat[SessionSearchParameters] =
    DefaultJsonProtocol.jsonFormat1(SessionSearchParameters.apply)
}

final case class SessionVote(proposalId: ProposalId, voteKey: VoteKey, trust: VoteTrust)

object SessionVote {
  implicit val format: RootJsonFormat[SessionVote] =
    DefaultJsonProtocol.jsonFormat3(SessionVote.apply)
}

final case class SessionUnvote(proposalId: ProposalId, voteKey: VoteKey, trust: VoteTrust)

object SessionUnvote {
  implicit val format: RootJsonFormat[SessionUnvote] =
    DefaultJsonProtocol.jsonFormat3(SessionUnvote.apply)
}

final case class SessionQualification(proposalId: ProposalId, qualificationKey: QualificationKey, trust: VoteTrust)

object SessionQualification {
  implicit val format: RootJsonFormat[SessionQualification] =
    DefaultJsonProtocol.jsonFormat3(SessionQualification.apply)
}

final case class SessionUnqualification(proposalId: ProposalId, qualificationKey: QualificationKey, trust: VoteTrust)

object SessionUnqualification {
  implicit val format: RootJsonFormat[SessionUnqualification] =
    DefaultJsonProtocol.jsonFormat3(SessionUnqualification.apply)
}

final case class LogSessionVoteEvent(sessionId: SessionId,
                                     requestContext: RequestContext,
                                     action: SessionAction[SessionVote])
    extends TransferableToUser[SessionVote]
    with TransactionalSessionHistoryEvent[SessionVote] {
  override def toUserHistoryEvent(userId: UserId): LogUserVoteEvent = {
    LogUserVoteEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction[UserVote](
        date = action.date,
        actionType = action.actionType,
        arguments = UserVote(
          proposalId = action.arguments.proposalId,
          voteKey = action.arguments.voteKey,
          trust = action.arguments.trust
        )
      )
    )
  }
}

object LogSessionVoteEvent {
  val action: String = "vote"

  implicit val format: RootJsonFormat[LogSessionVoteEvent] =
    DefaultJsonProtocol.jsonFormat(LogSessionVoteEvent.apply, "sessionId", "context", "action")
}

final case class LogSessionUnvoteEvent(sessionId: SessionId,
                                       requestContext: RequestContext,
                                       action: SessionAction[SessionUnvote])
    extends TransferableToUser[SessionUnvote]
    with TransactionalSessionHistoryEvent[SessionUnvote] {
  override def toUserHistoryEvent(userId: UserId): LogUserUnvoteEvent = {
    LogUserUnvoteEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction[UserUnvote](
        date = action.date,
        actionType = action.actionType,
        arguments = UserUnvote(
          proposalId = action.arguments.proposalId,
          voteKey = action.arguments.voteKey,
          trust = action.arguments.trust
        )
      )
    )
  }
}

object LogSessionUnvoteEvent {
  val action: String = "unvote"

  implicit val format: RootJsonFormat[LogSessionUnvoteEvent] =
    DefaultJsonProtocol.jsonFormat(LogSessionUnvoteEvent.apply, "sessionId", "context", "action")
}

final case class LogSessionQualificationEvent(sessionId: SessionId,
                                              requestContext: RequestContext,
                                              action: SessionAction[SessionQualification])
    extends TransferableToUser[SessionQualification]
    with TransactionalSessionHistoryEvent[SessionQualification] {
  override def toUserHistoryEvent(userId: UserId): LogUserQualificationEvent = {
    LogUserQualificationEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction[UserQualification](
        date = action.date,
        actionType = action.actionType,
        arguments = UserQualification(
          proposalId = action.arguments.proposalId,
          qualificationKey = action.arguments.qualificationKey,
          trust = action.arguments.trust
        )
      )
    )
  }
}

object LogSessionQualificationEvent {
  val action: String = "qualification"

  implicit val format: RootJsonFormat[LogSessionQualificationEvent] =
    DefaultJsonProtocol.jsonFormat(LogSessionQualificationEvent.apply, "sessionId", "context", "action")
}

final case class LogSessionUnqualificationEvent(sessionId: SessionId,
                                                requestContext: RequestContext,
                                                action: SessionAction[SessionUnqualification])
    extends TransferableToUser[SessionUnqualification]
    with TransactionalSessionHistoryEvent[SessionUnqualification] {
  override def toUserHistoryEvent(userId: UserId): LogUserUnqualificationEvent = {
    LogUserUnqualificationEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction[UserUnqualification](
        date = action.date,
        actionType = action.actionType,
        arguments = UserUnqualification(
          proposalId = action.arguments.proposalId,
          qualificationKey = action.arguments.qualificationKey,
          trust = action.arguments.trust
        )
      )
    )
  }
}

object LogSessionUnqualificationEvent {
  val action: String = "unqualification"

  implicit val format: RootJsonFormat[LogSessionUnqualificationEvent] =
    DefaultJsonProtocol.jsonFormat(LogSessionUnqualificationEvent.apply, "sessionId", "context", "action")
}

final case class LogSessionSearchProposalsEvent(sessionId: SessionId,
                                                requestContext: RequestContext,
                                                action: SessionAction[SessionSearchParameters])
    extends TransferableToUser[SessionSearchParameters] {
  override def toUserHistoryEvent(userId: UserId): LogUserSearchProposalsEvent = {
    LogUserSearchProposalsEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction[UserSearchParameters](
        date = action.date,
        actionType = action.actionType,
        arguments = UserSearchParameters(term = action.arguments.term)
      )
    )
  }

}

object LogSessionSearchProposalsEvent {
  val action: String = "search"

  implicit val format: RootJsonFormat[LogSessionSearchProposalsEvent] =
    DefaultJsonProtocol.jsonFormat(LogSessionSearchProposalsEvent.apply, "sessionId", "context", "action")

}

case class SessionTransformed(sessionId: SessionId, requestContext: RequestContext, action: SessionAction[UserId])
    extends SessionHistoryEvent[UserId]

object SessionTransformed {
  implicit val format: RootJsonFormat[SessionTransformed] =
    DefaultJsonProtocol.jsonFormat(SessionTransformed.apply, "sessionId", "context", "action")
}

case class ProposalLockValue(voteKey: Option[VoteKey], qualifications: Seq[QualificationKey])

sealed trait LockVoteAction

case object Vote extends LockVoteAction
case class ChangeQualifications(key: Seq[QualificationKey]) extends LockVoteAction

case class LockProposalForVote(sessionId: SessionId, proposalId: ProposalId) extends SessionRelatedEvent
case class LockProposalForQualification(sessionId: SessionId, proposalId: ProposalId, key: QualificationKey)
    extends SessionRelatedEvent
case class ReleaseProposalForVote(sessionId: SessionId, proposalId: ProposalId) extends SessionRelatedEvent
case class ReleaseProposalForQualification(sessionId: SessionId, proposalId: ProposalId, key: QualificationKey)
    extends SessionRelatedEvent

case object LockAcquired
case object LockAlreadyAcquired

sealed trait SessionHistoryAction extends SessionRelatedEvent

final case class GetSessionHistory(sessionId: SessionId) extends SessionHistoryAction

final case class RequestSessionVoteValues(sessionId: SessionId, proposalIds: Seq[ProposalId])
    extends SessionRelatedEvent

final case class RequestSessionVotedProposals(sessionId: SessionId) extends SessionRelatedEvent
final case class UserConnected(sessionId: SessionId, userId: UserId, requestContext: RequestContext)
    extends SessionHistoryAction

final case class LogSessionStartSequenceEvent(sessionId: SessionId,
                                              requestContext: RequestContext,
                                              action: SessionAction[StartSequenceParameters])
    extends TransferableToUser[StartSequenceParameters] {
  override def toUserHistoryEvent(userId: UserId): UserHistoryEvent[_] =
    LogUserStartSequenceEvent(
      userId,
      requestContext,
      UserAction(date = action.date, actionType = LogUserStartSequenceEvent.action, arguments = action.arguments)
    )
}

object LogSessionStartSequenceEvent {
  val action: String = "start-sequence"

  implicit val logSessionStartSequenceEventFormatted: RootJsonFormat[LogSessionStartSequenceEvent] =
    DefaultJsonProtocol.jsonFormat(LogSessionStartSequenceEvent.apply, "sessionId", "context", "action")
}

final case class StopSession(sessionId: SessionId) extends SessionRelatedEvent
