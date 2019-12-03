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

import com.typesafe.scalalogging.StrictLogging
import io.circe._
import io.circe.generic.semiauto._
import org.make.core.profile.Profile
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.{DateHelper, MakeSerializable, StringValue, Timestamped}
import spray.json.{JsString, JsValue, JsonFormat}

sealed trait Role {
  def shortName: String
}

object Role {
  implicit lazy val roleEncoder: Encoder[Role] = (role: Role) => Json.fromString(role.shortName)
  implicit lazy val roleDecoder: Decoder[Role] = Decoder.decodeString.map(Role.matchRole)

  val roles: Map[String, Role] = Map(
    RoleAdmin.shortName -> RoleAdmin,
    RoleModerator.shortName -> RoleModerator,
    RolePolitical.shortName -> RolePolitical,
    RoleCitizen.shortName -> RoleCitizen,
    RoleActor.shortName -> RoleActor
  )

  def matchRole(role: String): Role = {
    roles.getOrElse(role, CustomRole(role))
  }

  case object RoleAdmin extends Role {
    val shortName: String = "ROLE_ADMIN"
  }

  case object RoleModerator extends Role {
    val shortName: String = "ROLE_MODERATOR"
  }

  case object RolePolitical extends Role {
    val shortName: String = "ROLE_POLITICAL"
  }

  case object RoleCitizen extends Role {
    val shortName: String = "ROLE_CITIZEN"
  }

  case object RoleActor extends Role {
    val shortName: String = "ROLE_ACTOR"
  }
}

final case class CustomRole(override val shortName: String) extends Role

object CustomRole extends StrictLogging {
  implicit lazy val customRoleEncoder: Encoder[CustomRole] =
    (customRole: CustomRole) => Json.fromString(customRole.shortName)
  implicit lazy val customRoleDecoder: Decoder[CustomRole] =
    Decoder.decodeString.map(CustomRole(_))
}

sealed trait UserType {
  def shortName: String
}

object UserType {
  implicit lazy val userTypeEncoder: Encoder[UserType] = (userType: UserType) => Json.fromString(userType.shortName)
  implicit lazy val userTypeDecoder: Decoder[UserType] = Decoder.decodeString.map(UserType.matchUserType)

  implicit val UserTypeFormatter: JsonFormat[UserType] = new JsonFormat[UserType] {
    override def read(json: JsValue): UserType = json match {
      case JsString(s) => matchUserType(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: UserType): JsValue = {
      JsString(obj.shortName)
    }
  }

  val userTypes: Map[String, UserType] = Map(
    UserTypeUser.shortName -> UserTypeUser,
    UserTypeOrganisation.shortName -> UserTypeOrganisation,
    UserTypePersonality.shortName -> UserTypePersonality
  )

  def matchUserType(userType: String): UserType = {
    userTypes.getOrElse(userType, UserTypeUser)
  }

  case object UserTypeUser extends UserType {
    val shortName: String = "USER"
  }

  case object UserTypeOrganisation extends UserType {
    val shortName: String = "ORGANISATION"
  }

  case object UserTypePersonality extends UserType {
    val shortName: String = "PERSONALITY"
  }
}

case class MailingErrorLog(error: String, date: ZonedDateTime)

case class User(userId: UserId,
                email: String,
                firstName: Option[String],
                lastName: Option[String],
                lastIp: Option[String],
                hashedPassword: Option[String],
                enabled: Boolean,
                emailVerified: Boolean,
                userType: UserType,
                lastConnection: ZonedDateTime,
                verificationToken: Option[String],
                verificationTokenExpiresAt: Option[ZonedDateTime],
                resetToken: Option[String],
                resetTokenExpiresAt: Option[ZonedDateTime],
                roles: Seq[Role],
                country: Country,
                language: Language,
                profile: Option[Profile],
                override val createdAt: Option[ZonedDateTime] = None,
                override val updatedAt: Option[ZonedDateTime] = None,
                isHardBounce: Boolean = false,
                lastMailingError: Option[MailingErrorLog] = None,
                organisationName: Option[String] = None,
                publicProfile: Boolean = false,
                availableQuestions: Seq[QuestionId],
                anonymousParticipation: Boolean)
    extends MakeSerializable
    with Timestamped {

  def fullName: Option[String] = {
    (firstName, lastName, organisationName) match {
      case (None, None, None)                                 => None
      case (None, None, Some(definedOrganisationName))        => Some(definedOrganisationName)
      case (Some(definedFirstName), None, _)                  => Some(definedFirstName)
      case (None, Some(definedLastName), _)                   => Some(definedLastName)
      case (Some(definedFirstName), Some(definedLastName), _) => Some(s"$definedFirstName $definedLastName")
    }
  }

  def verificationTokenIsExpired: Boolean =
    verificationTokenExpiresAt.forall(_.isBefore(DateHelper.now()))

  def resetTokenIsExpired: Boolean =
    resetTokenExpiresAt.forall(_.isBefore(DateHelper.now()))

  def hasRole(role: Role): Boolean = {
    roles.contains(role)
  }
}

case class UserId(value: String) extends StringValue

object UserId {
  implicit lazy val userIdEncoder: Encoder[UserId] = (a: UserId) => Json.fromString(a.value)
  implicit lazy val userIdDecoder: Decoder[UserId] =
    Decoder.decodeString.map(UserId(_))

  implicit val userIdFormatter: JsonFormat[UserId] = new JsonFormat[UserId] {
    override def read(json: JsValue): UserId = json match {
      case JsString(s) => UserId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: UserId): JsValue = {
      JsString(obj.value)
    }
  }

}

sealed trait ConnectionMode {
  val shortName: String
}

object ConnectionMode {

  val connectionModes: Map[String, ConnectionMode] = {
    Map(Mail.shortName -> Mail, Facebook.shortName -> Facebook, Google.shortName -> Google)
  }

  implicit lazy val encoder: Encoder[ConnectionMode] = (connectionMode: ConnectionMode) =>
    Json.fromString(connectionMode.shortName)

  implicit lazy val decoder: Decoder[ConnectionMode] =
    Decoder.decodeString.emap { value: String =>
      connectionModes.get(value) match {
        case Some(connectionMode) => Right(connectionMode)
        case None                 => Left(s"$value is not a connection mode")
      }
    }

  case object Mail extends ConnectionMode {
    override val shortName: String = "MAIL"
  }

  case object Facebook extends ConnectionMode {
    override val shortName: String = "FACEBOOK"
  }

  case object Google extends ConnectionMode {
    override val shortName: String = "GOOGLE"
  }
}

case class ReconnectInfo(reconnectToken: String,
                         firstName: Option[String],
                         avatarUrl: Option[String],
                         hiddenMail: String,
                         connectionMode: Seq[ConnectionMode])

object ReconnectInfo {
  implicit lazy val encoder: Encoder[ReconnectInfo] = deriveEncoder[ReconnectInfo]
  implicit lazy val decoder: Decoder[ReconnectInfo] = deriveDecoder[ReconnectInfo]
}
