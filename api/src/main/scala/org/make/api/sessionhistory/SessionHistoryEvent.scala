package org.make.api.sessionhistory

import java.time.ZonedDateTime

import org.make.api.userhistory._
import org.make.core.SprayJsonFormatters._
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

sealed trait SessionHistoryEvent[T] extends MakeSerializable with Product {
  def sessionId: SessionId
  def requestContext: RequestContext
  def action: SessionAction[T]
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
        }).asJsObject.fields + ("type" -> JsString(obj.productPrefix)))
      }
    }
}

final case class SessionSearchParameters(term: String)

object SessionSearchParameters {
  implicit val format: RootJsonFormat[SessionSearchParameters] =
    DefaultJsonProtocol.jsonFormat1(SessionSearchParameters.apply)
}

final case class SessionVote(proposalId: ProposalId, voteKey: VoteKey)

object SessionVote {
  implicit val format: RootJsonFormat[SessionVote] =
    DefaultJsonProtocol.jsonFormat2(SessionVote.apply)
}

final case class SessionUnvote(proposalId: ProposalId, voteKey: VoteKey)

object SessionUnvote {
  implicit val format: RootJsonFormat[SessionUnvote] =
    DefaultJsonProtocol.jsonFormat2(SessionUnvote.apply)
}

final case class SessionQualification(proposalId: ProposalId, qualificationKey: QualificationKey)

object SessionQualification {
  implicit val format: RootJsonFormat[SessionQualification] =
    DefaultJsonProtocol.jsonFormat2(SessionQualification.apply)
}

final case class SessionUnqualification(proposalId: ProposalId, qualificationKey: QualificationKey)

object SessionUnqualification {
  implicit val format: RootJsonFormat[SessionUnqualification] =
    DefaultJsonProtocol.jsonFormat2(SessionUnqualification.apply)
}

final case class LogSessionVoteEvent(sessionId: SessionId,
                                     requestContext: RequestContext,
                                     action: SessionAction[SessionVote])
    extends SessionHistoryEvent[SessionVote] {
  def toUserHistoryEvent(userId: UserId): LogUserVoteEvent = {
    LogUserVoteEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction[UserVote](
        date = action.date,
        actionType = action.actionType,
        arguments = UserVote(proposalId = action.arguments.proposalId, voteKey = action.arguments.voteKey)
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
    extends SessionHistoryEvent[SessionUnvote] {
  def toUserHistoryEvent(userId: UserId): LogUserUnvoteEvent = {
    LogUserUnvoteEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction[UserUnvote](
        date = action.date,
        actionType = action.actionType,
        arguments = UserUnvote(proposalId = action.arguments.proposalId, voteKey = action.arguments.voteKey)
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
    extends SessionHistoryEvent[SessionQualification] {
  def toUserHistoryEvent(userId: UserId): LogUserQualificationEvent = {
    LogUserQualificationEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction[UserQualification](
        date = action.date,
        actionType = action.actionType,
        arguments = UserQualification(
          proposalId = action.arguments.proposalId,
          qualificationKey = action.arguments.qualificationKey
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
    extends SessionHistoryEvent[SessionUnqualification] {
  def toUserHistoryEvent(userId: UserId): LogUserUnqualificationEvent = {
    LogUserUnqualificationEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction[UserUnqualification](
        date = action.date,
        actionType = action.actionType,
        arguments = UserUnqualification(
          proposalId = action.arguments.proposalId,
          qualificationKey = action.arguments.qualificationKey
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
    extends SessionHistoryEvent[SessionSearchParameters] {
  def toUserHistoryEvent(userId: UserId): LogUserSearchProposalsEvent = {
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

sealed trait SessionHistoryAction {
  def sessionId: SessionId
}

final case class GetSessionHistory(sessionId: SessionId) extends SessionHistoryAction

final case class RequestSessionVoteValues(sessionId: SessionId, proposalIds: Seq[ProposalId])
    extends SessionHistoryAction

final case class UserCreated(sessionId: SessionId, userId: UserId) extends SessionHistoryAction
final case class UserConnected(sessionId: SessionId, userId: UserId) extends SessionHistoryAction
