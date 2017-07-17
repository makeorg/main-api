package org.make.core.user

import java.time.{ZoneOffset, ZonedDateTime}

sealed trait UserEvent {
  def connectedUserId: Option[UserId]
  def eventDate: ZonedDateTime
  def userId: UserId
}

final case class ResetPasswordEvent(override val connectedUserId: Option[UserId] = None,
                                    override val eventDate: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC),
                                    override val userId: UserId)
    extends UserEvent

object ResetPasswordEvent {
  def apply(connectedUserId: Option[UserId], user: User): ResetPasswordEvent = {
    ResetPasswordEvent(userId = user.userId, connectedUserId = connectedUserId)
  }
}
