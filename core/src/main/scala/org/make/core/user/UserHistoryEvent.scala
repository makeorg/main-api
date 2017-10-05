package org.make.core.user

import java.time.{LocalDate, ZonedDateTime}

import org.make.core.proposal.ProposalEvent.{ProposalAccepted, ProposalRefused}
import org.make.core.proposal.{ProposalId, QualificationKey, SearchQuery, VoteKey}
import org.make.core.reference.ThemeId
import org.make.core.{MakeSerializable, RequestContext}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import spray.json.DefaultJsonProtocol._
import org.make.core.SprayJsonFormatters._

final case class UserAction[T](date: ZonedDateTime, actionType: String, arguments: T)

object UserAction {
  implicit def userActionUserRegisteredFormatted[T](
    implicit formatter: RootJsonFormat[T]
  ): RootJsonFormat[UserAction[T]] =
    DefaultJsonProtocol.jsonFormat3[ZonedDateTime, String, T, UserAction[T]](
      (date: ZonedDateTime, action: String, parameter: T) => UserAction[T](date, action, parameter)
    )

}

sealed trait Protagonist

case object Moderator extends Protagonist
case object Citizen extends Protagonist

sealed trait UserHistoryEvent[T] extends MakeSerializable {
  def userId: UserId
  def requestContext: RequestContext
  def action: UserAction[T]
  def protagonist: Protagonist
}

final case class UserSearchParameters(query: SearchQuery)

object UserSearchParameters {
  implicit val searchParametersFormatted: RootJsonFormat[UserSearchParameters] =
    DefaultJsonProtocol.jsonFormat1(UserSearchParameters.apply)

}

final case class UserRegistered(email: String,
                                dateOfBirth: Option[LocalDate],
                                firstName: Option[String],
                                lastName: Option[String],
                                profession: Option[String],
                                postalCode: Option[String])

object UserRegistered {
  implicit val userRegisteredFormatted: RootJsonFormat[UserRegistered] =
    DefaultJsonProtocol.jsonFormat6(UserRegistered.apply)

}

final case class UserProposal(content: String, theme: Option[ThemeId])

object UserProposal {
  implicit val userProposalFormatted: RootJsonFormat[UserProposal] =
    DefaultJsonProtocol.jsonFormat2(UserProposal.apply)

}

final case class UserVote(proposalId: ProposalId, voteKey: VoteKey)

object UserVote {
  implicit val userVoteFormatted: RootJsonFormat[UserVote] =
    DefaultJsonProtocol.jsonFormat2(UserVote.apply)

}

final case class UserUnvote(proposalId: ProposalId, voteKey: VoteKey)

object UserUnvote {
  implicit val userUnvoteFormatted: RootJsonFormat[UserUnvote] =
    DefaultJsonProtocol.jsonFormat2(UserUnvote.apply)

}

final case class UserQualification(proposalId: ProposalId, qualificationKey: QualificationKey)

object UserQualification {
  implicit val userQualificationFormatted: RootJsonFormat[UserQualification] =
    DefaultJsonProtocol.jsonFormat2(UserQualification.apply)

}

final case class UserUnqualification(proposalId: ProposalId, qualificationKey: QualificationKey)

object UserUnqualification {
  implicit val userQualifFormatted: RootJsonFormat[UserUnqualification] =
    DefaultJsonProtocol.jsonFormat2(UserUnqualification.apply)

}

final case class LogUserSearchProposalsEvent(userId: UserId,
                                             requestContext: RequestContext,
                                             action: UserAction[UserSearchParameters])
    extends UserHistoryEvent[UserSearchParameters] {
  override val protagonist: Protagonist = Citizen
}

// User actions
object LogUserSearchProposalsEvent {
  val action: String = "search"

  implicit val logUserSearchProposalsEventFormatted: RootJsonFormat[LogUserSearchProposalsEvent] =
    DefaultJsonProtocol.jsonFormat(LogUserSearchProposalsEvent.apply, "userId", "context", "action")

}

final case class LogGetProposalDuplicatesEvent(userId: UserId,
                                               requestContext: RequestContext,
                                               action: UserAction[ProposalId])
    extends UserHistoryEvent[ProposalId] {
  override val protagonist: Protagonist = Moderator
}

object LogGetProposalDuplicatesEvent {
  val action: String = "duplicates"
}

final case class LogAcceptProposalEvent(userId: UserId,
                                        requestContext: RequestContext,
                                        action: UserAction[ProposalAccepted])
    extends UserHistoryEvent[ProposalAccepted] {
  override val protagonist: Protagonist = Moderator
}

final case class LogRefuseProposalEvent(userId: UserId,
                                        requestContext: RequestContext,
                                        action: UserAction[ProposalRefused])
    extends UserHistoryEvent[ProposalRefused] {
  override val protagonist: Protagonist = Moderator
}

object LogRegisterCitizenEvent {
  val action = "register"

  implicit val logRegisterCitizenEventFormatted: RootJsonFormat[LogRegisterCitizenEvent] =
    DefaultJsonProtocol.jsonFormat(LogRegisterCitizenEvent.apply, "userId", "context", "action")

}

final case class LogUserProposalEvent(userId: UserId, requestContext: RequestContext, action: UserAction[UserProposal])
    extends UserHistoryEvent[UserProposal] {
  override val protagonist: Protagonist = Citizen
}

object LogUserProposalEvent {
  val action: String = "propose"
  implicit val logUserProposalEventFormatted: RootJsonFormat[LogUserProposalEvent] =
    DefaultJsonProtocol.jsonFormat(LogUserProposalEvent.apply, "userId", "context", "action")

}

final case class LogUserVoteEvent(userId: UserId, requestContext: RequestContext, action: UserAction[UserVote])
    extends UserHistoryEvent[UserVote] {
  override val protagonist: Protagonist = Citizen
}

object LogUserVoteEvent {
  val action: String = "vote"

  implicit val logUserVoteEventFormatted: RootJsonFormat[LogUserVoteEvent] =
    DefaultJsonProtocol.jsonFormat(LogUserVoteEvent.apply, "userId", "context", "action")

}

final case class LogUserUnvoteEvent(userId: UserId, requestContext: RequestContext, action: UserAction[UserUnvote])
    extends UserHistoryEvent[UserUnvote] {
  override val protagonist: Protagonist = Citizen
}

object LogUserUnvoteEvent {
  val action: String = "unvote"

  implicit val logUserUnvoteEventFormatted: RootJsonFormat[LogUserUnvoteEvent] =
    DefaultJsonProtocol.jsonFormat(LogUserUnvoteEvent.apply, "userId", "context", "action")

}

final case class LogUserQualificationEvent(userId: UserId,
                                           requestContext: RequestContext,
                                           action: UserAction[UserQualification])
    extends UserHistoryEvent[UserQualification] {
  override val protagonist: Protagonist = Citizen
}

object LogUserQualificationEvent {
  val action: String = "qualification"

  implicit val logUserQualifEventFormatted: RootJsonFormat[LogUserQualificationEvent] =
    DefaultJsonProtocol.jsonFormat(LogUserQualificationEvent.apply, "userId", "context", "action")

}

final case class LogUserUnqualificationEvent(userId: UserId,
                                             requestContext: RequestContext,
                                             action: UserAction[UserUnqualification])
    extends UserHistoryEvent[UserUnqualification] {
  override val protagonist: Protagonist = Citizen
}

object LogUserUnqualificationEvent {
  val action: String = "unqualification"

  implicit val logUserUnqualifEventFormatted: RootJsonFormat[LogUserUnqualificationEvent] =
    DefaultJsonProtocol.jsonFormat(LogUserUnqualificationEvent.apply, "userId", "context", "action")

}

// Moderator actions
object LogAcceptProposalEvent {
  val action: String = "accept-proposal"

  implicit val logAcceptProposalEventFormatted: RootJsonFormat[LogAcceptProposalEvent] =
    DefaultJsonProtocol.jsonFormat(LogAcceptProposalEvent.apply, "userId", "context", "action")

}
object LogRefuseProposalEvent {
  val action: String = "refuse-proposal"

  implicit val logRefuseProposalEventFormatted: RootJsonFormat[LogRefuseProposalEvent] =
    DefaultJsonProtocol.jsonFormat(LogRefuseProposalEvent.apply, "userId", "context", "action")

}

final case class LogRegisterCitizenEvent(userId: UserId,
                                         requestContext: RequestContext,
                                         action: UserAction[UserRegistered])
    extends UserHistoryEvent[UserRegistered] {
  override val protagonist: Protagonist = Citizen
}

sealed trait UserHistoryAction {
  def userId: UserId
}

final case class GetUserHistory(userId: UserId) extends UserHistoryAction
