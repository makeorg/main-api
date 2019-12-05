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

import com.sksamuel.avro4s
import com.sksamuel.avro4s.{AvroDefault, AvroSortPriority, DefaultFieldMapper, RecordFormat, SchemaFor}
import org.make.core._
import org.make.core.profile.{Gender, SocioProfessionalCategory}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.user.{User, UserId}

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

  val defaultCountry: Country = Country("FR")
  val defaultLanguage: Language = Language("fr")

  val defaultDate: ZonedDateTime = ZonedDateTime.parse("2017-11-01T09:00:00Z")
}

final case class UserEventWrapper(version: Int, id: String, date: ZonedDateTime, eventType: String, event: UserEvent)
    extends EventWrapper[UserEvent]

object UserEventWrapper extends AvroSerializers {
  lazy val schemaFor: SchemaFor[UserEventWrapper] = SchemaFor.gen[UserEventWrapper]
  implicit lazy val avroDecoder: avro4s.Decoder[UserEventWrapper] = avro4s.Decoder.gen[UserEventWrapper]
  implicit lazy val avroEncoder: avro4s.Encoder[UserEventWrapper] = avro4s.Encoder.gen[UserEventWrapper]
  lazy val recordFormat: RecordFormat[UserEventWrapper] =
    RecordFormat[UserEventWrapper](schemaFor.schema(DefaultFieldMapper))
}

@AvroSortPriority(15)
final case class ResetPasswordEvent(override val connectedUserId: Option[UserId] = None,
                                    @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime =
                                      UserEvent.defaultDate,
                                    override val userId: UserId,
                                    @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
                                    @AvroDefault("fr") override val language: Language = UserEvent.defaultLanguage,
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

@AvroSortPriority(14)
final case class ResendValidationEmailEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  @AvroDefault("fr") override val language: Language = UserEvent.defaultLanguage,
  override val requestContext: RequestContext
) extends UserEvent {
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

@AvroSortPriority(13)
case class UserRegisteredEvent(override val connectedUserId: Option[UserId] = None,
                               @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime =
                                 UserEvent.defaultDate,
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
                               @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
                               @AvroDefault("fr") override val language: Language = UserEvent.defaultLanguage,
                               isSocialLogin: Boolean = false,
                               registerQuestionId: Option[QuestionId] = None,
                               optInPartner: Option[Boolean] = None)
    extends UserEvent {
  override def version(): Int = MakeSerializable.V3
}

@AvroSortPriority(12)
final case class UserConnectedEvent(override val connectedUserId: Option[UserId] = None,
                                    @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime =
                                      UserEvent.defaultDate,
                                    override val userId: UserId,
                                    @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
                                    @AvroDefault("fr") override val language: Language = UserEvent.defaultLanguage,
                                    override val requestContext: RequestContext)
    extends UserEvent {

  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(10)
final case class UserValidatedAccountEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId = UserId(value = ""),
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  @AvroDefault("fr") override val language: Language = UserEvent.defaultLanguage,
  override val requestContext: RequestContext = RequestContext.empty,
  isSocialLogin: Boolean = false
) extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(11)
final case class UserUpdatedTagEvent(override val connectedUserId: Option[UserId] = None,
                                     @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime =
                                       UserEvent.defaultDate,
                                     override val userId: UserId = UserId(value = ""),
                                     @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
                                     @AvroDefault("fr") override val language: Language = UserEvent.defaultLanguage,
                                     override val requestContext: RequestContext = RequestContext.empty,
                                     oldTag: String,
                                     newTag: String)
    extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(9)
case class OrganisationRegisteredEvent(override val connectedUserId: Option[UserId] = None,
                                       @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime =
                                         UserEvent.defaultDate,
                                       override val userId: UserId,
                                       override val requestContext: RequestContext,
                                       email: String,
                                       @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
                                       @AvroDefault("fr") override val language: Language = UserEvent.defaultLanguage)
    extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(8)
case class OrganisationUpdatedEvent(override val connectedUserId: Option[UserId] = None,
                                    @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime =
                                      UserEvent.defaultDate,
                                    override val userId: UserId,
                                    override val requestContext: RequestContext,
                                    @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
                                    @AvroDefault("fr") override val language: Language = UserEvent.defaultLanguage)
    extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(7)
case class OrganisationInitializationEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  override val requestContext: RequestContext,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  @AvroDefault("fr") override val language: Language = UserEvent.defaultLanguage
) extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

//TODO: remove
case class SnapshotUser(override val userId: UserId) extends UserRelatedEvent

@AvroSortPriority(6)
case class UserUpdatedOptInNewsletterEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  override val requestContext: RequestContext,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  @AvroDefault("fr") override val language: Language = UserEvent.defaultLanguage,
  optInNewsletter: Boolean
) extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(5)
case class UserAnonymizedEvent(override val connectedUserId: Option[UserId] = None,
                               @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime =
                                 UserEvent.defaultDate,
                               override val userId: UserId,
                               override val requestContext: RequestContext,
                               @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
                               @AvroDefault("fr") override val language: Language = UserEvent.defaultLanguage,
                               adminId: UserId)
    extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(4)
case class UserFollowEvent(override val connectedUserId: Option[UserId] = None,
                           @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime =
                             UserEvent.defaultDate,
                           override val userId: UserId,
                           override val requestContext: RequestContext,
                           @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
                           @AvroDefault("fr") override val language: Language = UserEvent.defaultLanguage,
                           followedUserId: UserId)
    extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(3)
case class UserUnfollowEvent(override val connectedUserId: Option[UserId] = None,
                             @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime =
                               UserEvent.defaultDate,
                             override val userId: UserId,
                             override val requestContext: RequestContext,
                             @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
                             @AvroDefault("fr") override val language: Language = UserEvent.defaultLanguage,
                             unfollowedUserId: UserId)
    extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}
