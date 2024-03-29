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

import com.sksamuel.avro4s.{AvroDefault, AvroSortPriority}
import org.make.api.user.Anonymization
import org.make.core._
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.ProposalId
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.user.{User, UserId}

import java.time.ZonedDateTime

trait VotedProposals {
  val proposals: Seq[ProposalId]
}
trait VotesValues {
  val votesValues: Map[ProposalId, VoteAndQualifications]
}

trait UserPersistentEvent {
  def userId: UserId
}

sealed trait UserEvent extends UserPersistentEvent {
  def connectedUserId: Option[UserId]
  def eventDate: ZonedDateTime
  def requestContext: RequestContext
  def country: Country
  def version(): Int
  def eventId: Option[EventId]
}

object UserEvent {

  val defaultCountry: Country = Country("FR")

  val defaultDate: ZonedDateTime = ZonedDateTime.parse("2017-11-01T09:00:00Z")
}

sealed trait B2BRegisteredEvent extends UserEvent

@AvroSortPriority(17)
final case class PersonalityRegisteredEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  override val requestContext: RequestContext,
  email: String,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  eventId: Option[EventId] = None
) extends B2BRegisteredEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(16)
final case class PersonalityEmailChangedEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  override val requestContext: RequestContext,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  oldEmail: String,
  newEmail: String,
  eventId: Option[EventId] = None
) extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(15)
final case class ResetPasswordEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  override val requestContext: RequestContext,
  eventId: Option[EventId] = None
) extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

object ResetPasswordEvent {
  def apply(
    connectedUserId: Option[UserId],
    user: User,
    country: Country,
    requestContext: RequestContext,
    eventId: EventId
  ): ResetPasswordEvent = {
    ResetPasswordEvent(
      userId = user.userId,
      connectedUserId = connectedUserId,
      country = country,
      requestContext = requestContext,
      eventDate = DateHelper.now(),
      eventId = Some(eventId)
    )
  }
}

@AvroSortPriority(14)
final case class ResendValidationEmailEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  override val requestContext: RequestContext,
  eventId: Option[EventId] = None
) extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

object ResendValidationEmailEvent {
  def apply(
    connectedUserId: UserId,
    userId: UserId,
    country: Country,
    requestContext: RequestContext,
    eventId: EventId
  ): ResendValidationEmailEvent = {
    ResendValidationEmailEvent(
      connectedUserId = Some(connectedUserId),
      eventDate = DateHelper.now(),
      userId = userId,
      country = country,
      requestContext = requestContext,
      eventId = Some(eventId)
    )
  }
}

@AvroSortPriority(13)
final case class UserRegisteredEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  override val requestContext: RequestContext,
  firstName: Option[String],
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  isSocialLogin: Boolean = false,
  registerQuestionId: Option[QuestionId] = None,
  optInPartner: Option[Boolean] = None,
  eventId: Option[EventId] = None
) extends UserEvent {
  override def version(): Int = MakeSerializable.V4
}

@AvroSortPriority(12)
final case class UserConnectedEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  override val requestContext: RequestContext,
  eventId: Option[EventId] = None
) extends UserEvent {

  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(10)
final case class UserValidatedAccountEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId = UserId(value = ""),
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  override val requestContext: RequestContext = RequestContext.empty,
  isSocialLogin: Boolean = false,
  eventId: Option[EventId] = None
) extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(11)
final case class UserUpdatedTagEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId = UserId(value = ""),
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  override val requestContext: RequestContext = RequestContext.empty,
  oldTag: String,
  newTag: String,
  eventId: Option[EventId] = None
) extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(9)
final case class OrganisationRegisteredEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  override val requestContext: RequestContext,
  email: String,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  eventId: Option[EventId] = None
) extends B2BRegisteredEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(8)
final case class OrganisationUpdatedEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  override val requestContext: RequestContext,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  eventId: Option[EventId] = None
) extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(7)
final case class OrganisationInitializationEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  override val requestContext: RequestContext,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  eventId: Option[EventId] = None
) extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(6)
final case class UserUpdatedOptInNewsletterEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  override val requestContext: RequestContext,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  optInNewsletter: Boolean,
  eventId: Option[EventId] = None
) extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(5)
final case class UserAnonymizedEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  override val requestContext: RequestContext,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  adminId: UserId,
  @AvroDefault("automatic") mode: Anonymization = Anonymization.Automatic,
  eventId: Option[EventId] = None
) extends UserEvent {
  override def version(): Int = MakeSerializable.V2
}

@AvroSortPriority(4)
final case class UserFollowEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  override val requestContext: RequestContext,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  followedUserId: UserId,
  eventId: Option[EventId] = None
) extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(3)
final case class UserUnfollowEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  override val requestContext: RequestContext,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  unfollowedUserId: UserId,
  eventId: Option[EventId] = None
) extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(2)
final case class UserUploadAvatarEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  override val requestContext: RequestContext,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  avatarUrl: String,
  eventId: Option[EventId] = None
) extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}

@AvroSortPriority(1)
final case class OrganisationEmailChangedEvent(
  override val connectedUserId: Option[UserId] = None,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = UserEvent.defaultDate,
  override val userId: UserId,
  override val requestContext: RequestContext,
  @AvroDefault("FR") override val country: Country = UserEvent.defaultCountry,
  oldEmail: String,
  newEmail: String,
  eventId: Option[EventId] = None
) extends UserEvent {
  override def version(): Int = MakeSerializable.V1
}
