package org.make.core.user

import java.time.{LocalDate, ZonedDateTime}

import org.make.core.{DateHelper, EventWrapper, RequestContext}
import shapeless.{:+:, CNil, Coproduct}

sealed trait UserEvent {
  def connectedUserId: Option[UserId]
  def eventDate: ZonedDateTime
  def userId: UserId
  def context: RequestContext
}

object UserEvent {

  type AnyUserEvent = ResetPasswordEvent :+: ResendValidationEmailEvent :+: UserRegisteredEvent :+: CNil

  final case class UserEventWrapper(version: Int,
                                    id: String,
                                    date: ZonedDateTime,
                                    eventType: String,
                                    event: AnyUserEvent)
      extends EventWrapper

  object UserEventWrapper {
    def wrapEvent(event: UserEvent): AnyUserEvent =
      event match {
        case e: ResetPasswordEvent         => Coproduct[AnyUserEvent](e)
        case e: ResendValidationEmailEvent => Coproduct[AnyUserEvent](e)
        case e: UserRegisteredEvent        => Coproduct[AnyUserEvent](e)
      }
  }

  final case class ResetPasswordEvent(override val connectedUserId: Option[UserId] = None,
                                      override val eventDate: ZonedDateTime = DateHelper.now(),
                                      override val userId: UserId,
                                      override val context: RequestContext)
      extends UserEvent

  object ResetPasswordEvent {
    def apply(connectedUserId: Option[UserId], user: User, context: RequestContext): ResetPasswordEvent = {
      ResetPasswordEvent(userId = user.userId, connectedUserId = connectedUserId, context = context)
    }
    val version: Int = 1
  }

  final case class ResendValidationEmailEvent(override val connectedUserId: Option[UserId] = None,
                                              override val eventDate: ZonedDateTime = DateHelper.now(),
                                              override val userId: UserId,
                                              override val context: RequestContext)
      extends UserEvent

  object ResendValidationEmailEvent {
    def apply(connectedUserId: UserId, userId: UserId, context: RequestContext): ResendValidationEmailEvent = {
      ResendValidationEmailEvent(userId = userId, connectedUserId = connectedUserId, context = context)
    }
    val version: Int = 1
  }

  case class UserRegisteredEvent(override val connectedUserId: Option[UserId] = None,
                                 override val eventDate: ZonedDateTime = DateHelper.now(),
                                 override val userId: UserId,
                                 override val context: RequestContext,
                                 email: String,
                                 firstName: Option[String],
                                 lastName: Option[String],
                                 profession: Option[String],
                                 dateOfBirth: Option[LocalDate],
                                 postalCode: Option[String])
      extends UserEvent

  object UserRegisteredEvent {
    val version: Int = 1
  }

}
