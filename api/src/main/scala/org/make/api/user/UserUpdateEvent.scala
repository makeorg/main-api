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
      UserUpdatedEvent :+:
      UserUpdatedHardBounceEvent :+:
      UserUpdatedOptInNewsletterEvent :+:
      UserUpdatedPasswordEvent :+:
      UserUpdatedTagEvent :+:
      UserUpdateValidatedEvent :+:
      UserAnonymizedEvent :+:
      UserFollowEvent :+:
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
        case e: UserUpdatedEvent                => Coproduct[AnyUserUpdateEvent](e)
        case e: UserUpdatedHardBounceEvent      => Coproduct[AnyUserUpdateEvent](e)
        case e: UserUpdatedPasswordEvent        => Coproduct[AnyUserUpdateEvent](e)
        case e: UserUpdatedOptInNewsletterEvent => Coproduct[AnyUserUpdateEvent](e)
        case e: UserUpdateValidatedEvent        => Coproduct[AnyUserUpdateEvent](e)
        case e: UserAnonymizedEvent             => Coproduct[AnyUserUpdateEvent](e)
        case e: UserFollowEvent                 => Coproduct[AnyUserUpdateEvent](e)
      }
  }

  /*
   * Add an implicit for each event to manage
   */
  object HandledMessages extends Poly1 {
    implicit val atUserRegisteredEvent: Case.Aux[UserCreatedEvent, UserCreatedEvent] = at(identity)
    implicit val atUserUpdatedEvent: Case.Aux[UserUpdatedEvent, UserUpdatedEvent] = at(identity)
    implicit val atUserUpdatedHardBounceEvent: Case.Aux[UserUpdatedHardBounceEvent, UserUpdatedHardBounceEvent] = at(
      identity
    )
    implicit val atUserUpdatedOptInNewsletterEvent
      : Case.Aux[UserUpdatedOptInNewsletterEvent, UserUpdatedOptInNewsletterEvent] = at(identity)
    implicit val atUserUpdatedPasswordEvent: Case.Aux[UserUpdatedPasswordEvent, UserUpdatedPasswordEvent] = at(identity)
    implicit val atUserUpdatedTagEvent: Case.Aux[UserUpdatedTagEvent, UserUpdatedTagEvent] = at(identity)
    implicit val atUserUpdateValidatedEvent: Case.Aux[UserUpdateValidatedEvent, UserUpdateValidatedEvent] = at(identity)
    implicit val atUserDeletedEvent: Case.Aux[UserAnonymizedEvent, UserAnonymizedEvent] = at(identity)
    implicit val atUserUnfollowEvent: Case.Aux[UserFollowEvent, UserFollowEvent] = at(identity)
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

  final case class UserUpdateValidatedEvent(override val eventDate: ZonedDateTime = DateHelper.now(),
                                            override val userId: Option[UserId] = None,
                                            override val email: Option[String] = None)
      extends UserUpdateEvent {
    override def version(): Int = MakeSerializable.V1
  }

  final case class UserUpdatedEvent(override val eventDate: ZonedDateTime = DateHelper.now(),
                                    override val userId: Option[UserId] = None,
                                    override val email: Option[String] = None)
      extends UserUpdateEvent {
    override def version(): Int = MakeSerializable.V1
  }

  final case class UserAnonymizedEvent(override val eventDate: ZonedDateTime = DateHelper.now(),
                                       override val userId: Option[UserId] = None,
                                       override val email: Option[String] = None)
      extends UserUpdateEvent {
    override def version(): Int = MakeSerializable.V1
  }

  final case class UserFollowEvent(override val eventDate: ZonedDateTime = DateHelper.now(),
                                   override val userId: Option[UserId],
                                   override val email: Option[String] = None,
                                   followedUserId: UserId)
      extends UserUpdateEvent {
    override def version(): Int = MakeSerializable.V1
  }

}
