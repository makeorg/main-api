package org.make.api.userhistory

import java.time.{LocalDate, ZonedDateTime}

import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, EventWrapper, RequestContext}
import shapeless.{:+:, CNil, Coproduct}

trait UserRelatedEvent {
  def userId: UserId
}

sealed trait UserEvent extends UserRelatedEvent {
  def connectedUserId: Option[UserId]
  def eventDate: ZonedDateTime
  def requestContext: RequestContext
}

object UserEvent {

  type AnyUserEvent =
    ResetPasswordEvent :+:
      ResendValidationEmailEvent :+:
      UserRegisteredEvent :+:
      UserConnectedEvent :+:
      UserValidatedAccountEvent :+:
      CNil

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
        case e: UserConnectedEvent         => Coproduct[AnyUserEvent](e)
        case e: UserRegisteredEvent        => Coproduct[AnyUserEvent](e)
        case e: UserValidatedAccountEvent  => Coproduct[AnyUserEvent](e)
      }
  }

  final case class ResetPasswordEvent(override val connectedUserId: Option[UserId] = None,
                                      override val eventDate: ZonedDateTime = DateHelper.now(),
                                      override val userId: UserId,
                                      override val requestContext: RequestContext)
      extends UserEvent

  object ResetPasswordEvent {
    def apply(connectedUserId: Option[UserId], user: User, requestContext: RequestContext): ResetPasswordEvent = {
      ResetPasswordEvent(userId = user.userId, connectedUserId = connectedUserId, requestContext = requestContext)
    }
    val version: Int = 1
  }

  final case class ResendValidationEmailEvent(override val connectedUserId: Option[UserId] = None,
                                              override val eventDate: ZonedDateTime = DateHelper.now(),
                                              override val userId: UserId,
                                              override val requestContext: RequestContext)
      extends UserEvent

  object ResendValidationEmailEvent {
    def apply(connectedUserId: UserId, userId: UserId, requestContext: RequestContext): ResendValidationEmailEvent = {
      ResendValidationEmailEvent(userId = userId, connectedUserId = connectedUserId, requestContext = requestContext)
    }
    val version: Int = 1
  }

  case class UserRegisteredEvent(override val connectedUserId: Option[UserId] = None,
                                 override val eventDate: ZonedDateTime = DateHelper.now(),
                                 override val userId: UserId,
                                 override val requestContext: RequestContext,
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

  final case class UserConnectedEvent(override val connectedUserId: Option[UserId] = None,
                                      override val eventDate: ZonedDateTime = DateHelper.now(),
                                      override val userId: UserId,
                                      override val requestContext: RequestContext)
      extends UserEvent

  object UserConnectedEvent {
    val version: Int = 1
  }

  final case class UserValidatedAccountEvent(override val connectedUserId: Option[UserId] = None,
                                             override val eventDate: ZonedDateTime = DateHelper.now(),
                                             override val userId: UserId = UserId(value = ""),
                                             override val requestContext: RequestContext = RequestContext.empty)
      extends UserEvent

  object UserValidatedAccountEvent {
    val version: Int = 1
  }

}
