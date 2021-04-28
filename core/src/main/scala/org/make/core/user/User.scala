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

package org.make.core.user

import java.time.ZonedDateTime

import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe._
import io.circe.generic.semiauto._
import io.swagger.annotations.ApiModelProperty
import org.make.core.profile.Profile
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.technical.enumeratum.FallbackingCirceEnum.FallbackingStringCirceEnum
import org.make.core.user.UserType.{UserTypeAnonymous, UserTypeOrganisation, UserTypePersonality, UserTypeUser}
import org.make.core.{DateHelper, MakeSerializable, SprayJsonFormatters, StringValue, Timestamped}
import spray.json.JsonFormat

import scala.annotation.meta.field

sealed abstract class Role(val value: String) extends StringEnumEntry with Product with Serializable

object Role extends StringEnum[Role] with FallbackingStringCirceEnum[Role] {

  override def default(value: String): Role = CustomRole(value)

  case object RoleSuperAdmin extends Role("ROLE_SUPER_ADMIN")
  case object RoleAdmin extends Role("ROLE_ADMIN")
  case object RoleModerator extends Role("ROLE_MODERATOR")
  case object RolePolitical extends Role("ROLE_POLITICAL")
  case object RoleCitizen extends Role("ROLE_CITIZEN")
  case object RoleActor extends Role("ROLE_ACTOR")

  override def values: IndexedSeq[Role] = findValues

}

final case class CustomRole(override val value: String) extends Role(value)

sealed abstract class UserType(val value: String) extends StringEnumEntry with Product with Serializable

object UserType extends StringEnum[UserType] with FallbackingStringCirceEnum[UserType] {

  override def default(value: String): UserType = UserTypeUser

  case object UserTypeAnonymous extends UserType("ANONYMOUS")
  case object UserTypeUser extends UserType("USER")
  case object UserTypeOrganisation extends UserType("ORGANISATION")
  case object UserTypePersonality extends UserType("PERSONALITY")

  override def values: IndexedSeq[UserType] = findValues

  implicit class UserTypeOps[T](val t: T) extends AnyVal {
    def isB2B(implicit h: HasUserType[T]): Boolean =
      Set(UserType.UserTypePersonality, UserType.UserTypeOrganisation).contains(h.userType(t))
    def isB2C(implicit h: HasUserType[T]): Boolean = h.userType(t) == UserType.UserTypeUser
  }

}

trait HasUserType[T] {
  def userType(t: T): UserType
}

object HasUserType {
  implicit val userUserType: HasUserType[User] = _.userType
}

final case class MailingErrorLog(error: String, date: ZonedDateTime)

final case class User(
  userId: UserId,
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  lastIp: Option[String],
  hashedPassword: Option[String],
  enabled: Boolean,
  emailVerified: Boolean,
  userType: UserType,
  lastConnection: Option[ZonedDateTime],
  verificationToken: Option[String],
  verificationTokenExpiresAt: Option[ZonedDateTime],
  resetToken: Option[String],
  resetTokenExpiresAt: Option[ZonedDateTime],
  roles: Seq[Role],
  country: Country,
  profile: Option[Profile],
  override val createdAt: Option[ZonedDateTime] = None,
  override val updatedAt: Option[ZonedDateTime] = None,
  isHardBounce: Boolean = false,
  lastMailingError: Option[MailingErrorLog] = None,
  organisationName: Option[String] = None,
  publicProfile: Boolean = false,
  availableQuestions: Seq[QuestionId],
  anonymousParticipation: Boolean,
  privacyPolicyApprovalDate: Option[ZonedDateTime] = None
) extends MakeSerializable
    with Timestamped {

  def fullName: Option[String] = {
    User.fullName(firstName, lastName, organisationName)
  }

  def displayName: Option[String] = this.userType match {
    case UserTypeAnonymous    => None
    case UserTypeUser         => this.firstName
    case UserTypeOrganisation => this.organisationName
    case UserTypePersonality  => this.fullName
  }

  def verificationTokenIsExpired: Boolean =
    verificationTokenExpiresAt.forall(_.isBefore(DateHelper.now()))

  def resetTokenIsExpired: Boolean =
    resetTokenExpiresAt.forall(_.isBefore(DateHelper.now()))

  def hasRole(role: Role): Boolean = {
    roles.contains(role)
  }

}

object User {
  def fullName(
    firstName: Option[String],
    lastName: Option[String],
    organisationName: Option[String]
  ): Option[String] = {
    (firstName, lastName, organisationName) match {
      case (None, None, None)                                 => None
      case (None, None, Some(definedOrganisationName))        => Some(definedOrganisationName)
      case (Some(definedFirstName), None, _)                  => Some(definedFirstName)
      case (None, Some(definedLastName), _)                   => Some(definedLastName)
      case (Some(definedFirstName), Some(definedLastName), _) => Some(s"$definedFirstName $definedLastName")
    }
  }
}

final case class UserId(value: String) extends StringValue

object UserId {
  implicit lazy val userIdEncoder: Encoder[UserId] = (a: UserId) => Json.fromString(a.value)
  implicit lazy val userIdDecoder: Decoder[UserId] =
    Decoder.decodeString.map(UserId(_))

  implicit val userIdFormatter: JsonFormat[UserId] = SprayJsonFormatters.forStringValue(UserId.apply)

}

sealed abstract class ConnectionMode(val value: String) extends StringEnumEntry with Product with Serializable

object ConnectionMode extends StringEnum[ConnectionMode] with StringCirceEnum[ConnectionMode] {

  case object Mail extends ConnectionMode("MAIL")
  case object Facebook extends ConnectionMode("FACEBOOK")
  case object Google extends ConnectionMode("GOOGLE")

  override def values: IndexedSeq[ConnectionMode] = findValues

}

final case class ReconnectInfo(
  reconnectToken: String,
  firstName: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/avatar.png")
  avatarUrl: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "y**********t@make.org") hiddenMail: String,
  @(ApiModelProperty @field)(dataType = "list[string]", allowableValues = "MAIL,FACEBOOK,GOOGLE")
  connectionMode: Seq[ConnectionMode]
)

object ReconnectInfo {
  implicit lazy val encoder: Encoder[ReconnectInfo] = deriveEncoder[ReconnectInfo]
  implicit lazy val decoder: Decoder[ReconnectInfo] = deriveDecoder[ReconnectInfo]
}
