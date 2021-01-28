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

import akka.actor.Props
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import grizzled.slf4j.Logging
import org.make.api.technical.RichFutures._
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor, TimeSettings}
import org.make.api.userhistory._
import org.make.core.AvroSerializers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserHistoryConsumerActor(userHistoryCoordinatorService: UserHistoryCoordinatorService, userService: UserService)
    extends KafkaConsumerActor[UserEventWrapper]
    with ActorEventBusServiceComponent
    with AvroSerializers
    with Logging {

  override protected lazy val kafkaTopic: String = UserProducerActor.topicKey
  override protected val format: RecordFormat[UserEventWrapper] = UserEventWrapper.recordFormat
  override val groupId = "user-history"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: UserEventWrapper): Future[_] = {
    message.event match {
      case event: ResetPasswordEvent              => doNothing(event)
      case event: UserRegisteredEvent             => handleUserRegisteredEvent(event)
      case event: UserConnectedEvent              => handleUserConnectedEvent(event)
      case event: UserUpdatedTagEvent             => doNothing(event)
      case event: ResendValidationEmailEvent      => doNothing(event)
      case event: UserValidatedAccountEvent       => doNothing(event)
      case event: OrganisationRegisteredEvent     => doNothing(event)
      case event: OrganisationUpdatedEvent        => doNothing(event)
      case event: OrganisationEmailChangedEvent   => handleOrganisationEmailChangedEvent(event)
      case event: PersonalityEmailChangedEvent    => handlePersonalityEmailChangedEvent(event)
      case event: OrganisationInitializationEvent => doNothing(event)
      case event: UserUpdatedOptInNewsletterEvent => handleUserUpdatedOptInNewsletterEvent(event)
      case event: UserAnonymizedEvent             => handleUserAnonymizedEvent(event)
      case event: UserFollowEvent                 => doNothing(event)
      case event: UserUnfollowEvent               => doNothing(event)
      case event: UserUploadAvatarEvent           => doNothing(event)
      case event: PersonalityRegisteredEvent      => doNothing(event)
    }
  }

  def handleUserRegisteredEvent(event: UserRegisteredEvent): Future[Unit] = {
    userHistoryCoordinatorService.logHistory(
      LogRegisterCitizenEvent(
        userId = event.userId,
        requestContext = event.requestContext,
        action = UserAction(
          date = event.eventDate,
          actionType = LogRegisterCitizenEvent.action,
          arguments = UserRegistered(
            email = event.email,
            dateOfBirth = event.dateOfBirth,
            firstName = event.firstName,
            lastName = event.lastName,
            profession = event.profession,
            postalCode = event.postalCode,
            country = event.country
          )
        )
      )
    )

    Future.unit
  }

  def handleUserConnectedEvent(event: UserConnectedEvent): Future[Unit] = {
    userService.getUser(event.userId).flatMap {
      case Some(user) =>
        userHistoryCoordinatorService.logHistory(
          LogUserConnectedEvent(
            userId = event.userId,
            requestContext = event.requestContext,
            action = UserAction(
              date = event.eventDate,
              actionType = LogUserConnectedEvent.action,
              arguments = UserHasConnected()
            )
          )
        )
        userService
          .update(user.copy(lastConnection = event.eventDate), event.requestContext)
          .toUnit
      case None =>
        log.warning("User not found after UserConnectedEvent: ", event)
        Future.unit
    }
  }

  def handleUserUpdatedOptInNewsletterEvent(event: UserUpdatedOptInNewsletterEvent): Future[Unit] = {
    if (event.optInNewsletter) {
      userHistoryCoordinatorService.logHistory(
        LogUserOptInNewsletterEvent(
          userId = event.userId,
          requestContext = event.requestContext,
          action = UserAction(
            date = event.eventDate,
            actionType = LogUserOptInNewsletterEvent.action,
            arguments = UserUpdatedOptIn(newOptIn = event.optInNewsletter)
          )
        )
      )
    } else {
      userHistoryCoordinatorService.logHistory(
        LogUserOptOutNewsletterEvent(
          userId = event.userId,
          requestContext = event.requestContext,
          action = UserAction(
            date = event.eventDate,
            actionType = LogUserOptOutNewsletterEvent.action,
            arguments = UserUpdatedOptIn(newOptIn = event.optInNewsletter)
          )
        )
      )
    }

    Future.unit
  }

  def handleUserAnonymizedEvent(event: UserAnonymizedEvent): Future[Unit] = {
    userHistoryCoordinatorService.logHistory(
      LogUserAnonymizedEvent(
        userId = event.userId,
        requestContext = event.requestContext,
        action = UserAction(
          date = event.eventDate,
          actionType = LogUserAnonymizedEvent.action,
          arguments = UserAnonymized(userId = event.userId, adminId = event.adminId)
        )
      )
    )

    Future.unit
  }

  def handleOrganisationEmailChangedEvent(event: OrganisationEmailChangedEvent): Future[Unit] = {
    userHistoryCoordinatorService.logHistory(
      LogOrganisationEmailChangedEvent(
        userId = event.userId,
        requestContext = event.requestContext,
        action = UserAction(
          date = event.eventDate,
          actionType = LogOrganisationEmailChangedEvent.action,
          arguments = OrganisationEmailChanged(oldEmail = event.oldEmail, newEmail = event.newEmail)
        )
      )
    )

    Future.unit
  }

  def handlePersonalityEmailChangedEvent(event: PersonalityEmailChangedEvent): Future[Unit] = {
    userHistoryCoordinatorService.logHistory(
      LogPersonalityEmailChangedEvent(
        userId = event.userId,
        requestContext = event.requestContext,
        action = UserAction(
          date = event.eventDate,
          actionType = LogPersonalityEmailChangedEvent.action,
          arguments = PersonalityEmailChanged(oldEmail = event.oldEmail, newEmail = event.newEmail)
        )
      )
    )

    Future.unit
  }
}

object UserHistoryConsumerActor {
  def props(userHistoryCoordinatorService: UserHistoryCoordinatorService, userService: UserService): Props =
    Props(new UserHistoryConsumerActor(userHistoryCoordinatorService, userService))
  val name: String = "user-events-history-consumer"
}
