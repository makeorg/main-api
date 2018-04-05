package org.make.core.user

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import io.circe._
import org.make.core.profile.Profile
import org.make.core.{MakeSerializable, StringValue, Timestamped}
import spray.json.{JsString, JsValue, JsonFormat}

sealed trait Role {
  def shortName: String
}

object Role extends StrictLogging {
  implicit lazy val roleEncoder: Encoder[Role] = (role: Role) => Json.fromString(role.shortName)
  implicit lazy val roleDecoder: Decoder[Role] =
    Decoder.decodeString.emap(role => Role.matchRole(role).map(Right.apply).getOrElse(Left(s"$role is not a Role")))

  val roles: Map[String, Role] = Map(
    RoleAdmin.shortName -> RoleAdmin,
    RoleModerator.shortName -> RoleModerator,
    RolePolitical.shortName -> RolePolitical,
    RoleCitizen.shortName -> RoleCitizen
  )

  def matchRole(role: String): Option[Role] = {
    val maybeRole = roles.get(role)
    if (maybeRole.isEmpty) {
      logger.warn(s"$role is not a role")
    }
    maybeRole
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
}
case class MailingErrorLog(error: String, date: ZonedDateTime)

case class User(userId: UserId,
                email: String,
                firstName: Option[String],
                lastName: Option[String],
                lastIp: Option[String],
                hashedPassword: Option[String],
                enabled: Boolean,
                verified: Boolean,
                lastConnection: ZonedDateTime,
                verificationToken: Option[String],
                verificationTokenExpiresAt: Option[ZonedDateTime],
                resetToken: Option[String],
                resetTokenExpiresAt: Option[ZonedDateTime],
                roles: Seq[Role],
                country: String,
                language: String,
                profile: Option[Profile],
                override val createdAt: Option[ZonedDateTime] = None,
                override val updatedAt: Option[ZonedDateTime] = None,
                isHardBounce: Boolean = false,
                lastMailingError: Option[MailingErrorLog] = None)
    extends MakeSerializable
    with Timestamped {

  def fullName: Option[String] = {
    (firstName, lastName) match {
      case (None, None)                                    => None
      case (Some(definedFirstName), None)                  => Some(definedFirstName)
      case (None, Some(definedLastName))                   => Some(definedLastName)
      case (Some(definedFirstName), Some(definedLastName)) => Some(s"$definedFirstName $definedLastName")
    }
  }

  def verificationTokenIsExpired: Boolean =
    verificationTokenExpiresAt.forall(_.isBefore(ZonedDateTime.now()))

  def resetTokenIsExpired: Boolean =
    resetTokenExpiresAt.forall(_.isBefore(ZonedDateTime.now()))
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
