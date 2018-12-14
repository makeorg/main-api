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

import java.time.{LocalDate, ZonedDateTime}

import org.make.api.proposal.PublishedProposalEvent.{
  ProposalAccepted,
  ProposalLocked,
  ProposalPostponed,
  ProposalRefused
}
import org.make.core.SprayJsonFormatters._
import org.make.core.operation.OperationId
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.sequence.{SequenceId, SequenceStatus, SearchQuery => SequenceSearchQuery}
import org.make.core.user._
import org.make.core.{MakeSerializable, RequestContext}
import spray.json.DefaultJsonProtocol._
import spray.json._

final case class UserAction[T](date: ZonedDateTime, actionType: String, arguments: T)

object UserAction {
  implicit def userActionUserRegisteredFormatted[T](implicit formatter: JsonFormat[T]): RootJsonFormat[UserAction[T]] =
    DefaultJsonProtocol.jsonFormat3[ZonedDateTime, String, T, UserAction[T]](
      (date: ZonedDateTime, action: String, parameter: T) => UserAction[T](date, action, parameter)
    )

}

sealed trait Protagonist

case object Moderator extends Protagonist
case object Citizen extends Protagonist

sealed trait UserHistoryEvent[T] extends UserRelatedEvent with MakeSerializable with Product {
  def requestContext: RequestContext
  def action: UserAction[T]
  def protagonist: Protagonist
}

sealed trait TransactionalUserHistoryEvent[T] extends UserHistoryEvent[T]

object UserHistoryEvent {
  implicit val format: RootJsonFormat[UserHistoryEvent[_]] =
    new RootJsonFormat[UserHistoryEvent[_]] {
      override def read(json: JsValue): UserHistoryEvent[_] = {
        json.asJsObject.getFields("type") match {
          case Seq(JsString("LogUserSearchProposalsEvent"))      => json.convertTo[LogUserSearchProposalsEvent]
          case Seq(JsString("LogUserVoteEvent"))                 => json.convertTo[LogUserVoteEvent]
          case Seq(JsString("LogUserUnvoteEvent"))               => json.convertTo[LogUserUnvoteEvent]
          case Seq(JsString("LogUserQualificationEvent"))        => json.convertTo[LogUserQualificationEvent]
          case Seq(JsString("LogUserUnqualificationEvent"))      => json.convertTo[LogUserUnqualificationEvent]
          case Seq(JsString("LogRegisterCitizenEvent"))          => json.convertTo[LogRegisterCitizenEvent]
          case Seq(JsString("LogUserProposalEvent"))             => json.convertTo[LogUserProposalEvent]
          case Seq(JsString("LogAcceptProposalEvent"))           => json.convertTo[LogAcceptProposalEvent]
          case Seq(JsString("LogRefuseProposalEvent"))           => json.convertTo[LogRefuseProposalEvent]
          case Seq(JsString("LogPostponeProposalEvent"))         => json.convertTo[LogPostponeProposalEvent]
          case Seq(JsString("LogLockProposalEvent"))             => json.convertTo[LogLockProposalEvent]
          case Seq(JsString("LogGetProposalDuplicatesEvent"))    => json.convertTo[LogGetProposalDuplicatesEvent]
          case Seq(JsString("LogUserAddProposalsSequenceEvent")) => json.convertTo[LogUserAddProposalsSequenceEvent]
          case Seq(JsString("LogUserCreateSequenceEvent"))       => json.convertTo[LogUserCreateSequenceEvent]
          case Seq(JsString("LogUserRemoveProposalsSequenceEvent")) =>
            json.convertTo[LogUserRemoveProposalsSequenceEvent]
          case Seq(JsString("LogUserUpdateSequenceEvent"))  => json.convertTo[LogUserUpdateSequenceEvent]
          case Seq(JsString("LogUserSearchSequencesEvent")) => json.convertTo[LogUserSearchSequencesEvent]
          case Seq(JsString("LogUserStartSequenceEvent"))   => json.convertTo[LogUserStartSequenceEvent]
        }
      }

      override def write(obj: UserHistoryEvent[_]): JsObject = {
        JsObject((obj match {
          case event: LogUserSearchProposalsEvent         => event.toJson
          case event: LogUserVoteEvent                    => event.toJson
          case event: LogUserUnvoteEvent                  => event.toJson
          case event: LogUserQualificationEvent           => event.toJson
          case event: LogUserUnqualificationEvent         => event.toJson
          case event: LogRegisterCitizenEvent             => event.toJson
          case event: LogUserProposalEvent                => event.toJson
          case event: LogAcceptProposalEvent              => event.toJson
          case event: LogRefuseProposalEvent              => event.toJson
          case event: LogPostponeProposalEvent            => event.toJson
          case event: LogLockProposalEvent                => event.toJson
          case event: LogGetProposalDuplicatesEvent       => event.toJson
          case event: LogUserAddProposalsSequenceEvent    => event.toJson
          case event: LogUserCreateSequenceEvent          => event.toJson
          case event: LogUserRemoveProposalsSequenceEvent => event.toJson
          case event: LogUserUpdateSequenceEvent          => event.toJson
          case event: LogUserSearchSequencesEvent         => event.toJson
          case event: LogUserStartSequenceEvent           => event.toJson
        }).asJsObject.fields + ("type" -> JsString(obj.productPrefix)))
      }
    }

}

final case class UserSearchParameters(term: String)

object UserSearchParameters {
  implicit val searchParametersFormatted: RootJsonFormat[UserSearchParameters] =
    DefaultJsonProtocol.jsonFormat1(UserSearchParameters.apply)
}

final case class SearchSequenceParameters(query: SequenceSearchQuery)
object SearchSequenceParameters {
  implicit val searchParametersFormatted: RootJsonFormat[SearchSequenceParameters] =
    DefaultJsonProtocol.jsonFormat1(SearchSequenceParameters.apply)
}

final case class StartSequenceParameters(slug: Option[String],
                                         sequenceId: Option[SequenceId],
                                         includedProposals: Seq[ProposalId] = Seq.empty)
object StartSequenceParameters {
  implicit val searchParametersFormatted: RootJsonFormat[StartSequenceParameters] =
    DefaultJsonProtocol.jsonFormat3(StartSequenceParameters.apply)
}

final case class UserRegistered(email: String,
                                dateOfBirth: Option[LocalDate],
                                firstName: Option[String],
                                lastName: Option[String],
                                profession: Option[String],
                                postalCode: Option[String],
                                country: Country = Country("FR"),
                                language: Language = Language("fr"))

object UserRegistered {
  implicit val userRegisteredFormatted: RootJsonFormat[UserRegistered] =
    DefaultJsonProtocol.jsonFormat8(UserRegistered.apply)

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

  implicit val format: RootJsonFormat[LogGetProposalDuplicatesEvent] =
    DefaultJsonProtocol.jsonFormat(LogGetProposalDuplicatesEvent.apply, "userId", "context", "action")
}

final case class LogAcceptProposalEvent(userId: UserId,
                                        requestContext: RequestContext,
                                        action: UserAction[ProposalAccepted])
    extends UserHistoryEvent[ProposalAccepted] {
  override val protagonist: Protagonist = Moderator
}

object LogAcceptProposalEvent {
  val action: String = "accept-proposal"

  implicit val logAcceptProposalEventFormatted: RootJsonFormat[LogAcceptProposalEvent] =
    DefaultJsonProtocol.jsonFormat(LogAcceptProposalEvent.apply, "userId", "context", "action")

}

final case class LogRefuseProposalEvent(userId: UserId,
                                        requestContext: RequestContext,
                                        action: UserAction[ProposalRefused])
    extends UserHistoryEvent[ProposalRefused] {
  override val protagonist: Protagonist = Moderator
}

object LogRefuseProposalEvent {
  val action: String = "refuse-proposal"

  implicit val logRefuseProposalEventFormatted: RootJsonFormat[LogRefuseProposalEvent] =
    DefaultJsonProtocol.jsonFormat(LogRefuseProposalEvent.apply, "userId", "context", "action")

}

final case class LogPostponeProposalEvent(userId: UserId,
                                          requestContext: RequestContext,
                                          action: UserAction[ProposalPostponed])
    extends UserHistoryEvent[ProposalPostponed] {
  override val protagonist: Protagonist = Moderator
}

object LogPostponeProposalEvent {
  val action: String = "postpone-proposal"

  implicit val logPostponeProposalEventFormatted: RootJsonFormat[LogPostponeProposalEvent] =
    DefaultJsonProtocol.jsonFormat(LogPostponeProposalEvent.apply, "userId", "context", "action")
}

final case class LogLockProposalEvent(userId: UserId,
                                      moderatorName: Option[String],
                                      requestContext: RequestContext,
                                      action: UserAction[ProposalLocked])
    extends UserHistoryEvent[ProposalLocked] {
  override val protagonist: Protagonist = Moderator
}

object LogLockProposalEvent {
  val action: String = "lock-proposal"

  implicit val logLockProposalEventFormatted: RootJsonFormat[LogLockProposalEvent] =
    DefaultJsonProtocol.jsonFormat(LogLockProposalEvent.apply, "userId", "moderatorName", "context", "action")

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
    extends TransactionalUserHistoryEvent[UserVote] {
  override val protagonist: Protagonist = Citizen
}

object LogUserVoteEvent {
  val action: String = "vote"

  implicit val logUserVoteEventFormatted: RootJsonFormat[LogUserVoteEvent] =
    DefaultJsonProtocol.jsonFormat(LogUserVoteEvent.apply, "userId", "context", "action")

}

final case class LogUserUnvoteEvent(userId: UserId, requestContext: RequestContext, action: UserAction[UserUnvote])
    extends TransactionalUserHistoryEvent[UserUnvote] {
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
    extends TransactionalUserHistoryEvent[UserQualification] {
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
    extends TransactionalUserHistoryEvent[UserUnqualification] {
  override val protagonist: Protagonist = Citizen
}

object LogUserUnqualificationEvent {
  val action: String = "unqualification"

  implicit val logUserUnqualifEventFormatted: RootJsonFormat[LogUserUnqualificationEvent] =
    DefaultJsonProtocol.jsonFormat(LogUserUnqualificationEvent.apply, "userId", "context", "action")

}

final case class LogRegisterCitizenEvent(userId: UserId,
                                         requestContext: RequestContext,
                                         action: UserAction[UserRegistered])
    extends UserHistoryEvent[UserRegistered] {
  override val protagonist: Protagonist = Citizen
}

final case class LogUserCreateSequenceEvent(userId: UserId,
                                            requestContext: RequestContext,
                                            action: UserAction[SequenceCreated])
    extends UserHistoryEvent[SequenceCreated] {
  override val protagonist: Protagonist = Moderator
}

object LogUserCreateSequenceEvent {
  val action: String = "create-sequence"

  implicit val logUserCreateSequenceEventFormatted: RootJsonFormat[LogUserCreateSequenceEvent] =
    DefaultJsonProtocol.jsonFormat(LogUserCreateSequenceEvent.apply, "userId", "context", "action")
}

final case class LogUserAddProposalsSequenceEvent(userId: UserId,
                                                  requestContext: RequestContext,
                                                  action: UserAction[SequenceProposalsAdded])
    extends UserHistoryEvent[SequenceProposalsAdded] {
  override val protagonist: Protagonist = Moderator
}

object LogUserAddProposalsSequenceEvent {
  val action: String = "add-proposals-sequence"

  implicit val logUserAddProposalsSequenceEventFormatted: RootJsonFormat[LogUserAddProposalsSequenceEvent] =
    DefaultJsonProtocol.jsonFormat(LogUserAddProposalsSequenceEvent.apply, "userId", "context", "action")
}

final case class LogUserRemoveProposalsSequenceEvent(userId: UserId,
                                                     requestContext: RequestContext,
                                                     action: UserAction[SequenceProposalsRemoved])
    extends UserHistoryEvent[SequenceProposalsRemoved] {
  override val protagonist: Protagonist = Moderator
}
object LogUserRemoveProposalsSequenceEvent {
  val action: String = "remove-proposals-sequence"

  implicit val logUserRemoveProposalsSequenceEvent: RootJsonFormat[LogUserRemoveProposalsSequenceEvent] =
    DefaultJsonProtocol.jsonFormat(LogUserRemoveProposalsSequenceEvent.apply, "userId", "context", "action")
}

final case class LogUserUpdateSequenceEvent(userId: UserId,
                                            requestContext: RequestContext,
                                            action: UserAction[SequenceUpdated])
    extends UserHistoryEvent[SequenceUpdated] {
  override val protagonist: Protagonist = Moderator
}
object LogUserUpdateSequenceEvent {
  val action: String = "update-sequence"

  implicit val logUserUpdateSequenceEventFormatted: RootJsonFormat[LogUserUpdateSequenceEvent] =
    DefaultJsonProtocol.jsonFormat(LogUserUpdateSequenceEvent.apply, "userId", "context", "action")
}

final case class LogUserSearchSequencesEvent(userId: UserId,
                                             requestContext: RequestContext,
                                             action: UserAction[SearchSequenceParameters])
    extends UserHistoryEvent[SearchSequenceParameters] {
  override val protagonist: Protagonist = Moderator
}
object LogUserSearchSequencesEvent {
  val action: String = "search-sequence"

  implicit val logUserSearchSequencesEventFormatted: RootJsonFormat[LogUserSearchSequencesEvent] =
    DefaultJsonProtocol.jsonFormat(LogUserSearchSequencesEvent.apply, "userId", "context", "action")

}

final case class LogUserStartSequenceEvent(userId: UserId,
                                           requestContext: RequestContext,
                                           action: UserAction[StartSequenceParameters])
    extends UserHistoryEvent[StartSequenceParameters] {
  override val protagonist: Protagonist = Citizen
}

object LogUserStartSequenceEvent {
  val action: String = "start-sequence"

  implicit val logUserStartSequenceEventFormatted: RootJsonFormat[LogUserStartSequenceEvent] =
    DefaultJsonProtocol.jsonFormat(LogUserStartSequenceEvent.apply, "userId", "context", "action")

}

final case class SequenceProposalsAdded(id: SequenceId,
                                        proposalIds: Seq[ProposalId],
                                        requestContext: RequestContext,
                                        eventDate: ZonedDateTime,
                                        userId: UserId) {

  def version(): Int = MakeSerializable.V1
}

object SequenceProposalsAdded {
  val actionType: String = "sequence-proposal-added"

  implicit val sequenceProposalsAddedFormatter: RootJsonFormat[SequenceProposalsAdded] =
    DefaultJsonProtocol.jsonFormat5(SequenceProposalsAdded.apply)
}

final case class SequenceProposalsRemoved(id: SequenceId,
                                          proposalIds: Seq[ProposalId],
                                          requestContext: RequestContext,
                                          eventDate: ZonedDateTime,
                                          userId: UserId) {

  def version(): Int = MakeSerializable.V1
}

object SequenceProposalsRemoved {
  val actionType: String = "sequence-proposal-added"

  implicit val sequenceProposalsRemovedFormatter: RootJsonFormat[SequenceProposalsRemoved] =
    DefaultJsonProtocol.jsonFormat5(SequenceProposalsRemoved.apply)

}

final case class SequenceCreated(id: SequenceId,
                                 slug: String,
                                 requestContext: RequestContext,
                                 userId: UserId,
                                 eventDate: ZonedDateTime,
                                 title: String,
                                 themeIds: Seq[ThemeId],
                                 operationId: Option[OperationId] = None,
                                 searchable: Boolean) {
  def version(): Int = MakeSerializable.V2
}

object SequenceCreated {
  val actionType: String = "sequence-created"

  implicit val sequenceCreatedFormatter: RootJsonFormat[SequenceCreated] =
    DefaultJsonProtocol.jsonFormat9(SequenceCreated.apply)
}

final case class SequenceUpdated(id: SequenceId,
                                 userId: UserId,
                                 eventDate: ZonedDateTime,
                                 requestContext: RequestContext,
                                 title: Option[String],
                                 status: Option[SequenceStatus],
                                 @Deprecated operation: Option[String] = None,
                                 operationId: Option[OperationId] = None,
                                 themeIds: Seq[ThemeId]) {
  def version(): Int = MakeSerializable.V2
}

object SequenceUpdated {
  val actionType: String = "sequence-updated"

  implicit val sequenceUpdated: RootJsonFormat[SequenceUpdated] =
    DefaultJsonProtocol.jsonFormat9(SequenceUpdated.apply)
}
