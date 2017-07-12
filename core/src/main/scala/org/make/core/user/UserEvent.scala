package org.make.core.user

import java.time.{ZoneOffset, ZonedDateTime}

import org.make.core.EventWrapper
import shapeless.{:+:, CNil, Coproduct}

sealed trait UserEvent {
  def connectedUserId: Option[UserId]
  def eventDate: ZonedDateTime
  def userId: UserId
  def version: Int
}

object UserEvent {

  type AnyUserEvent = ResetPasswordEvent :+: CNil

  final case class UserEventWrapper(version: Int,
                                    id: String,
                                    date: ZonedDateTime,
                                    eventType: String,
                                    event: AnyUserEvent)
      extends EventWrapper

  object UserEventWrapper {
    def wrapEvent(event: UserEvent): AnyUserEvent = event match {
      case e: ResetPasswordEvent => Coproduct[AnyUserEvent](e)
    }
  }

  final case class ResetPasswordEvent(override val connectedUserId: Option[UserId] = None,
                                      override val eventDate: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC),
                                      override val userId: UserId)
      extends UserEvent {
    val version: Int = 1
  }

  object ResetPasswordEvent {
    def apply(connectedUserId: Option[UserId], user: User): ResetPasswordEvent = {
      ResetPasswordEvent(userId = user.userId, connectedUserId = connectedUserId)
    }
  }

  final case class ResendValidationEmailEvent(override val connectedUserId: Option[UserId] = None,
                                              override val eventDate: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC),
                                              override val userId: UserId)
      extends UserEvent {
    val version: Int = 1
  }

  object ResendValidationEmailEvent {
    def apply(connectedUserId: UserId, userId: UserId): ResendValidationEmailEvent = {
      ResendValidationEmailEvent(userId = userId, connectedUserId = connectedUserId)
    }
  }

}
