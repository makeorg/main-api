package org.make.core.user

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import io.circe._
import org.make.core.profile.Profile
import org.make.core.{MakeSerializable, StringValue, Timestamped}

sealed trait Role {
  def shortName: String

  implicit lazy val roleEncoder: Encoder[Role] = (role: Role) => Json.fromString(role.shortName)
  implicit lazy val roleDecoder: Decoder[Role] =
    Decoder.decodeString.map(
      role => Role.matchRole(role).getOrElse(throw new IllegalArgumentException(s"$role is not a Role"))
    )
}

object Role extends StrictLogging {
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
case class User(userId: UserId,
                email: String,
                firstName: Option[String],
                lastName: Option[String],
                lastIp: Option[String],
                hashedPassword: Option[String],
                salt: Option[String],
                enabled: Boolean,
                verified: Boolean,
                lastConnection: ZonedDateTime,
                verificationToken: Option[String],
                verificationTokenExpiresAt: Option[ZonedDateTime],
                resetToken: Option[String],
                resetTokenExpiresAt: Option[ZonedDateTime],
                roles: Seq[Role],
                profile: Option[Profile],
                override val createdAt: Option[ZonedDateTime] = None,
                override val updatedAt: Option[ZonedDateTime] = None)
    extends MakeSerializable
    with Timestamped {

  def fullName: Option[String] = {
    (firstName, lastName) match {
      case (None, None)                      => None
      case (Some(firstName), None)           => Some(firstName)
      case (None, Some(lastName))            => Some(lastName)
      case (Some(firstName), Some(lastName)) => Some(s"$firstName $lastName")
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

}
