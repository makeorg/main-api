package org.make.api.user

import java.time.ZonedDateTime

import org.make.core.user.UserId
import org.make.core.{DateHelper, EventWrapper, MakeSerializable}
import shapeless.{:+:, CNil, Coproduct, Poly1}

sealed trait UserUpdateEvent {
  def eventDate: ZonedDateTime
  def userId: Option[UserId]
  def email: Option[String]
  def version(): Int
}

object UserUpdateEvent {

  type AnyUserUpdateEvent =
    UserCreatedEvent :+:
      UserUpdatedHardBounceEvent :+:
      UserUpdatedOptInNewsletterEvent :+:
      UserUpdatedPasswordEvent :+:
      UserUpdatedTagEvent :+:
      CNil

  final case class UserUpdateEventWrapper(version: Int,
                                          id: String,
                                          date: ZonedDateTime,
                                          eventType: String,
                                          event: AnyUserUpdateEvent)
      extends EventWrapper

  object UserUpdateEventWrapper {
    def wrapEvent(event: UserUpdateEvent): AnyUserUpdateEvent =
      event match {
        case e: UserUpdatedTagEvent             => Coproduct[AnyUserUpdateEvent](e)
        case e: UserCreatedEvent                => Coproduct[AnyUserUpdateEvent](e)
        case e: UserUpdatedHardBounceEvent      => Coproduct[AnyUserUpdateEvent](e)
        case e: UserUpdatedPasswordEvent        => Coproduct[AnyUserUpdateEvent](e)
        case e: UserUpdatedOptInNewsletterEvent => Coproduct[AnyUserUpdateEvent](e)
      }
  }

  /*
   * Add an implicit for each event to manage
   */
  object HandledMessages extends Poly1 {
    implicit val atUserRegisteredEvent: Case.Aux[UserCreatedEvent, UserCreatedEvent] = at(identity)
    implicit val atUserUpdatedHardBounceEvent: Case.Aux[UserUpdatedHardBounceEvent, UserUpdatedHardBounceEvent] = at(
      identity
    )
    implicit val atUserUpdatedOptInNewsletterEvent
      : Case.Aux[UserUpdatedOptInNewsletterEvent, UserUpdatedOptInNewsletterEvent] = at(identity)
    implicit val atUserUpdatedPasswordEvent: Case.Aux[UserUpdatedPasswordEvent, UserUpdatedPasswordEvent] = at(identity)
    implicit val atUserUpdatedTagEvent: Case.Aux[UserUpdatedTagEvent, UserUpdatedTagEvent] = at(identity)
  }

  case class UserCreatedEvent(override val eventDate: ZonedDateTime = DateHelper.now(),
                              override val userId: Option[UserId] = None,
                              override val email: Option[String] = None)
      extends UserUpdateEvent {
    override def version(): Int = MakeSerializable.V1
  }
  case class UserUpdatedHardBounceEvent(override val eventDate: ZonedDateTime = DateHelper.now(),
                                        override val email: Option[String] = None,
                                        override val userId: Option[UserId] = None)
      extends UserUpdateEvent {
    override def version(): Int = MakeSerializable.V1
  }
  case class UserUpdatedPasswordEvent(override val eventDate: ZonedDateTime = DateHelper.now(),
                                      override val email: Option[String] = None,
                                      override val userId: Option[UserId] = None)
      extends UserUpdateEvent {
    def version(): Int = MakeSerializable.V1
  }
  case class UserUpdatedOptInNewsletterEvent(override val eventDate: ZonedDateTime = DateHelper.now(),
                                             override val email: Option[String] = None,
                                             override val userId: Option[UserId] = None,
                                             optInNewsletter: Boolean)
      extends UserUpdateEvent {
    def version(): Int = MakeSerializable.V1
  }

  final case class UserUpdatedTagEvent(override val eventDate: ZonedDateTime = DateHelper.now(),
                                       override val userId: Option[UserId] = None,
                                       override val email: Option[String] = None,
                                       oldTag: String,
                                       newTag: String)
      extends UserUpdateEvent {
    override def version(): Int = MakeSerializable.V1
  }
}
