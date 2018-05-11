package org.make.api.userhistory

import java.time.{LocalDate, ZonedDateTime}

import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, EventWrapper, MakeSerializable, RequestContext}
import shapeless.{:+:, CNil, Coproduct, Poly1}

trait UserRelatedEvent {
  def userId: UserId
}

sealed trait UserEvent extends UserRelatedEvent {
  def connectedUserId: Option[UserId]
  def eventDate: ZonedDateTime
  def requestContext: RequestContext
  def country: String
  def language: String
  def version(): Int
}

object UserEvent {

  type AnyUserEvent =
    ResetPasswordEvent :+:
      ResendValidationEmailEvent :+:
      UserRegisteredEvent :+:
      UserConnectedEvent :+:
      UserUpdatedTagEvent :+:
      UserValidatedAccountEvent :+:
      OrganisationRegisteredEvent :+:
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
        case e: ResetPasswordEvent          => Coproduct[AnyUserEvent](e)
        case e: ResendValidationEmailEvent  => Coproduct[AnyUserEvent](e)
        case e: UserConnectedEvent          => Coproduct[AnyUserEvent](e)
        case e: UserUpdatedTagEvent         => Coproduct[AnyUserEvent](e)
        case e: UserRegisteredEvent         => Coproduct[AnyUserEvent](e)
        case e: UserValidatedAccountEvent   => Coproduct[AnyUserEvent](e)
        case e: OrganisationRegisteredEvent => Coproduct[AnyUserEvent](e)
      }
  }

  /*
   * Add an implicit for each event to manage
   */
  object HandledMessages extends Poly1 {
    implicit val atResetPasswordEvent: Case.Aux[ResetPasswordEvent, ResetPasswordEvent] = at(identity)
    implicit val atUserValidatedAccountEvent: Case.Aux[UserValidatedAccountEvent, UserValidatedAccountEvent] =
      at(identity)
    implicit val atUserRegisteredEvent: Case.Aux[UserRegisteredEvent, UserRegisteredEvent] = at(identity)
    implicit val atUserConnectedEvent: Case.Aux[UserConnectedEvent, UserConnectedEvent] = at(identity)
    implicit val atResendValidationEmail: Case.Aux[ResendValidationEmailEvent, ResendValidationEmailEvent] =
      at(identity)
    implicit val atUserUpdatedTagEvent: Case.Aux[UserUpdatedTagEvent, UserUpdatedTagEvent] = at(identity)
    implicit val atOrganisationRegisteredEvent: Case.Aux[OrganisationRegisteredEvent, OrganisationRegisteredEvent] = at(identity)
  }

  final case class ResetPasswordEvent(override val connectedUserId: Option[UserId] = None,
                                      override val eventDate: ZonedDateTime = DateHelper.now(),
                                      override val userId: UserId,
                                      override val country: String = "FR",
                                      override val language: String = "fr",
                                      override val requestContext: RequestContext)
      extends UserEvent {
    override def version(): Int = MakeSerializable.V1
  }

  object ResetPasswordEvent {
    def apply(connectedUserId: Option[UserId],
              user: User,
              country: String,
              language: String,
              requestContext: RequestContext): ResetPasswordEvent = {
      ResetPasswordEvent(
        userId = user.userId,
        connectedUserId = connectedUserId,
        country = country,
        language = language,
        requestContext = requestContext
      )
    }
  }

  final case class ResendValidationEmailEvent(override val connectedUserId: Option[UserId] = None,
                                              override val eventDate: ZonedDateTime = DateHelper.now(),
                                              override val userId: UserId,
                                              override val country: String = "FR",
                                              override val language: String = "fr",
                                              override val requestContext: RequestContext)
      extends UserEvent {
    override def version(): Int = MakeSerializable.V1
  }

  object ResendValidationEmailEvent {
    def apply(connectedUserId: UserId,
              userId: UserId,
              country: String,
              language: String,
              requestContext: RequestContext): ResendValidationEmailEvent = {
      ResendValidationEmailEvent(
        userId = userId,
        connectedUserId = connectedUserId,
        country = country,
        language = language,
        requestContext = requestContext
      )
    }
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
                                 postalCode: Option[String],
                                 override val country: String = "FR",
                                 override val language: String = "fr")
      extends UserEvent {
    override def version(): Int = MakeSerializable.V1
  }

  final case class UserConnectedEvent(override val connectedUserId: Option[UserId] = None,
                                      override val eventDate: ZonedDateTime = DateHelper.now(),
                                      override val userId: UserId,
                                      override val country: String = "FR",
                                      override val language: String = "fr",
                                      override val requestContext: RequestContext)
      extends UserEvent {

    override def version(): Int = MakeSerializable.V1
  }

  final case class UserValidatedAccountEvent(override val connectedUserId: Option[UserId] = None,
                                             override val eventDate: ZonedDateTime = DateHelper.now(),
                                             override val userId: UserId = UserId(value = ""),
                                             override val country: String = "FR",
                                             override val language: String = "fr",
                                             override val requestContext: RequestContext = RequestContext.empty)
      extends UserEvent {
    override def version(): Int = MakeSerializable.V1
  }

  final case class UserUpdatedTagEvent(override val connectedUserId: Option[UserId] = None,
                                       override val eventDate: ZonedDateTime = DateHelper.now(),
                                       override val userId: UserId = UserId(value = ""),
                                       override val country: String = "FR",
                                       override val language: String = "fr",
                                       override val requestContext: RequestContext = RequestContext.empty,
                                       oldTag: String,
                                       newTag: String)
      extends UserEvent {
    override def version(): Int = MakeSerializable.V1
  }

  case class OrganisationRegisteredEvent(override val connectedUserId: Option[UserId] = None,
                                 override val eventDate: ZonedDateTime = DateHelper.now(),
                                 override val userId: UserId,
                                 override val requestContext: RequestContext,
                                 email: String,
                                 override val country: String = "FR",
                                 override val language: String = "fr")
    extends UserEvent {
    override def version(): Int = MakeSerializable.V1
  }

}
