package org.make.core.session

import java.time.ZonedDateTime

import org.make.core.proposal.{ProposalId, QualificationKey, SearchQuery, VoteKey}
import org.make.core.{MakeSerializable, RequestContext}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import spray.json.DefaultJsonProtocol._
import org.make.core.SprayJsonFormatters._
import org.make.core.user.UserId

final case class SessionAction[T](date: ZonedDateTime, actionType: String, arguments: T)

object SessionAction {
  implicit def sessionActionSessionRegisteredFormatted[T](
    implicit formatter: RootJsonFormat[T]
  ): RootJsonFormat[SessionAction[T]] =
    DefaultJsonProtocol.jsonFormat3[ZonedDateTime, String, T, SessionAction[T]](
      (date: ZonedDateTime, action: String, parameter: T) => SessionAction[T](date, action, parameter)
    )
}

sealed trait SessionHistoryEvent[T] extends MakeSerializable {
  def sessionId: SessionId
  def requestContext: RequestContext
  def action: SessionAction[T]
}

final case class SessionSearchParameters(query: SearchQuery)

object SessionSearchParameters {
  implicit val searchParametersFormatted: RootJsonFormat[SessionSearchParameters] =
    DefaultJsonProtocol.jsonFormat1(SessionSearchParameters.apply)
}

final case class SessionVote(proposalId: ProposalId, voteKey: VoteKey)

object SessionVote {
  implicit val userVoteFormatted: RootJsonFormat[SessionVote] =
    DefaultJsonProtocol.jsonFormat2(SessionVote.apply)
}

final case class SessionUnvote(proposalId: ProposalId, voteKey: VoteKey)

object SessionUnvote {
  implicit val userUnvoteFormatted: RootJsonFormat[SessionUnvote] =
    DefaultJsonProtocol.jsonFormat2(SessionUnvote.apply)
}

final case class SessionQualification(proposalId: ProposalId, qualificationKey: QualificationKey)

object SessionQualification {
  implicit val userQualificationFormatted: RootJsonFormat[SessionQualification] =
    DefaultJsonProtocol.jsonFormat2(SessionQualification.apply)
}

final case class SessionUnqualification(proposalId: ProposalId, qualificationKey: QualificationKey)

object SessionUnqualification {
  implicit val userUnqualificationFormatted: RootJsonFormat[SessionUnqualification] =
    DefaultJsonProtocol.jsonFormat2(SessionUnqualification.apply)
}

final case class LogSessionVoteEvent(sessionId: SessionId,
                                     requestContext: RequestContext,
                                     action: SessionAction[SessionVote])
    extends SessionHistoryEvent[SessionVote]

object LogSessionVoteEvent {
  val action: String = "vote"

  implicit val logSessionVoteEventFormatted: RootJsonFormat[LogSessionVoteEvent] =
    DefaultJsonProtocol.jsonFormat(LogSessionVoteEvent.apply, "sessionId", "context", "action")
}

final case class LogSessionUnvoteEvent(sessionId: SessionId,
                                       requestContext: RequestContext,
                                       action: SessionAction[SessionUnvote])
    extends SessionHistoryEvent[SessionUnvote]

object LogSessionUnvoteEvent {
  val action: String = "unvote"

  implicit val logSessionUnvoteEventFormatted: RootJsonFormat[LogSessionUnvoteEvent] =
    DefaultJsonProtocol.jsonFormat(LogSessionUnvoteEvent.apply, "sessionId", "context", "action")
}

final case class LogSessionQualificationEvent(sessionId: SessionId,
                                              requestContext: RequestContext,
                                              action: SessionAction[SessionQualification])
    extends SessionHistoryEvent[SessionQualification]

object LogSessionQualificationEvent {
  val action: String = "qualification"

  implicit val logSessionQualificationEventFormatted: RootJsonFormat[LogSessionQualificationEvent] =
    DefaultJsonProtocol.jsonFormat(LogSessionQualificationEvent.apply, "sessionId", "context", "action")
}

final case class LogSessionUnqualificationEvent(sessionId: SessionId,
                                                requestContext: RequestContext,
                                                action: SessionAction[SessionUnqualification])
    extends SessionHistoryEvent[SessionUnqualification]

object LogSessionUnqualificationEvent {
  val action: String = "unqualification"

  implicit val logSessionUnqualificationEventFormatted: RootJsonFormat[LogSessionUnqualificationEvent] =
    DefaultJsonProtocol.jsonFormat(LogSessionUnqualificationEvent.apply, "sessionId", "context", "action")
}

final case class LogSessionSearchProposalsEvent(sessionId: SessionId,
                                                requestContext: RequestContext,
                                                action: SessionAction[SessionSearchParameters])
    extends SessionHistoryEvent[SessionSearchParameters]

object LogSessionSearchProposalsEvent {
  val action: String = "search"

  implicit val logSessionSearchProposalsEventFormatted: RootJsonFormat[LogSessionSearchProposalsEvent] =
    DefaultJsonProtocol.jsonFormat(LogSessionSearchProposalsEvent.apply, "sessionId", "context", "action")

}

sealed trait SessionHistoryAction {
  def sessionId: SessionId
}

final case class GetSessionHistory(sessionId: SessionId) extends SessionHistoryAction

final case class RequestSessionVoteValues(sessionId: SessionId, proposalIds: Seq[ProposalId])
    extends SessionHistoryAction

final case class UserCreated(sessionId: SessionId, userId: UserId) extends SessionHistoryAction
final case class UserConnected(sessionId: SessionId, userId: UserId) extends SessionHistoryAction
