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

package org.make.api.userhistory

import java.time.{LocalDate, ZonedDateTime}

import org.make.core.profile.{Gender, SocioProfessionalCategory}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
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
  def country: Country
  def language: Language
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
      OrganisationUpdatedEvent :+:
      OrganisationInitializationEvent :+:
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
        case e: ResetPasswordEvent              => Coproduct[AnyUserEvent](e)
        case e: ResendValidationEmailEvent      => Coproduct[AnyUserEvent](e)
        case e: UserConnectedEvent              => Coproduct[AnyUserEvent](e)
        case e: UserUpdatedTagEvent             => Coproduct[AnyUserEvent](e)
        case e: UserRegisteredEvent             => Coproduct[AnyUserEvent](e)
        case e: UserValidatedAccountEvent       => Coproduct[AnyUserEvent](e)
        case e: OrganisationRegisteredEvent     => Coproduct[AnyUserEvent](e)
        case e: OrganisationUpdatedEvent        => Coproduct[AnyUserEvent](e)
        case e: OrganisationInitializationEvent => Coproduct[AnyUserEvent](e)
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
    implicit val atOrganisationRegisteredEvent: Case.Aux[OrganisationRegisteredEvent, OrganisationRegisteredEvent] = at(
      identity
    )
    implicit val atOrganisationUpdatedEvent: Case.Aux[OrganisationUpdatedEvent, OrganisationUpdatedEvent] = at(identity)
    implicit val atOrganisationAskPasswordEvent
      : Case.Aux[OrganisationInitializationEvent, OrganisationInitializationEvent] =
      at(identity)
  }

  private val defaultCountry = Country("FR")
  private val defaultLanguage = Language("fr")

  private val defaultDate: ZonedDateTime = ZonedDateTime.parse("2017-11-01T09:00:00Z")

  final case class ResetPasswordEvent(override val connectedUserId: Option[UserId] = None,
                                      override val eventDate: ZonedDateTime = defaultDate,
                                      override val userId: UserId,
                                      override val country: Country = defaultCountry,
                                      override val language: Language = defaultLanguage,
                                      override val requestContext: RequestContext)
      extends UserEvent {
    override def version(): Int = MakeSerializable.V1
  }

  object ResetPasswordEvent {
    def apply(connectedUserId: Option[UserId],
              user: User,
              country: Country,
              language: Language,
              requestContext: RequestContext): ResetPasswordEvent = {
      ResetPasswordEvent(
        userId = user.userId,
        connectedUserId = connectedUserId,
        country = country,
        language = language,
        requestContext = requestContext,
        eventDate = DateHelper.now()
      )
    }
  }

  final case class ResendValidationEmailEvent(override val connectedUserId: Option[UserId] = None,
                                              override val eventDate: ZonedDateTime = defaultDate,
                                              override val userId: UserId,
                                              override val country: Country = defaultCountry,
                                              override val language: Language = defaultLanguage,
                                              override val requestContext: RequestContext)
      extends UserEvent {
    override def version(): Int = MakeSerializable.V1
  }

  object ResendValidationEmailEvent {
    def apply(connectedUserId: UserId,
              userId: UserId,
              country: Country,
              language: Language,
              requestContext: RequestContext): ResendValidationEmailEvent = {
      ResendValidationEmailEvent(
        connectedUserId = Some(connectedUserId),
        eventDate = DateHelper.now(),
        userId = userId,
        country = country,
        language = language,
        requestContext = requestContext
      )
    }
  }

  case class UserRegisteredEvent(override val connectedUserId: Option[UserId] = None,
                                 override val eventDate: ZonedDateTime = defaultDate,
                                 override val userId: UserId,
                                 override val requestContext: RequestContext,
                                 email: String,
                                 firstName: Option[String],
                                 lastName: Option[String],
                                 profession: Option[String],
                                 dateOfBirth: Option[LocalDate],
                                 postalCode: Option[String],
                                 gender: Option[Gender] = None,
                                 socioProfessionalCategory: Option[SocioProfessionalCategory] = None,
                                 override val country: Country = defaultCountry,
                                 override val language: Language = defaultLanguage,
                                 isSocialLogin: Boolean = false,
                                 registerQuestionId: Option[QuestionId] = None,
                                 optInPartner: Option[Boolean] = None)
      extends UserEvent {
    override def version(): Int = MakeSerializable.V3
  }

  final case class UserConnectedEvent(override val connectedUserId: Option[UserId] = None,
                                      override val eventDate: ZonedDateTime = defaultDate,
                                      override val userId: UserId,
                                      override val country: Country = defaultCountry,
                                      override val language: Language = defaultLanguage,
                                      override val requestContext: RequestContext)
      extends UserEvent {

    override def version(): Int = MakeSerializable.V1
  }

  final case class UserValidatedAccountEvent(override val connectedUserId: Option[UserId] = None,
                                             override val eventDate: ZonedDateTime = defaultDate,
                                             override val userId: UserId = UserId(value = ""),
                                             override val country: Country = defaultCountry,
                                             override val language: Language = defaultLanguage,
                                             override val requestContext: RequestContext = RequestContext.empty,
                                             isSocialLogin: Boolean = false)
      extends UserEvent {
    override def version(): Int = MakeSerializable.V1
  }

  final case class UserUpdatedTagEvent(override val connectedUserId: Option[UserId] = None,
                                       override val eventDate: ZonedDateTime = defaultDate,
                                       override val userId: UserId = UserId(value = ""),
                                       override val country: Country = defaultCountry,
                                       override val language: Language = defaultLanguage,
                                       override val requestContext: RequestContext = RequestContext.empty,
                                       oldTag: String,
                                       newTag: String)
      extends UserEvent {
    override def version(): Int = MakeSerializable.V1
  }

  case class OrganisationRegisteredEvent(override val connectedUserId: Option[UserId] = None,
                                         override val eventDate: ZonedDateTime = defaultDate,
                                         override val userId: UserId,
                                         override val requestContext: RequestContext,
                                         email: String,
                                         override val country: Country = defaultCountry,
                                         override val language: Language = defaultLanguage)
      extends UserEvent {
    override def version(): Int = MakeSerializable.V1
  }

  case class OrganisationUpdatedEvent(override val connectedUserId: Option[UserId] = None,
                                      override val eventDate: ZonedDateTime = defaultDate,
                                      override val userId: UserId,
                                      override val requestContext: RequestContext,
                                      override val country: Country = defaultCountry,
                                      override val language: Language = defaultLanguage)
      extends UserEvent {
    override def version(): Int = MakeSerializable.V1
  }

  case class OrganisationInitializationEvent(override val connectedUserId: Option[UserId] = None,
                                             override val eventDate: ZonedDateTime = defaultDate,
                                             override val userId: UserId,
                                             override val requestContext: RequestContext,
                                             override val country: Country = defaultCountry,
                                             override val language: Language = defaultLanguage)
      extends UserEvent {
    override def version(): Int = MakeSerializable.V1
  }

  //TODO: remove
  case class SnapshotUser(override val userId: UserId) extends UserRelatedEvent
}
