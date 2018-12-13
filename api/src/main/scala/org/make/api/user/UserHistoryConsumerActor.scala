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

import akka.actor.{ActorRef, Props}
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.MailJetTemplateConfigurationExtension
import org.make.api.technical.{ActorEventBusServiceComponent, AvroSerializers, KafkaConsumerActor, TimeSettings}
import org.make.api.userhistory.UserEvent._
import org.make.api.userhistory.{LogRegisterCitizenEvent, UserAction, UserRegistered}

import scala.concurrent.Future

class UserHistoryConsumerActor(userHistoryCoordinator: ActorRef)
    extends KafkaConsumerActor[UserEventWrapper]
    with MailJetTemplateConfigurationExtension
    with ActorEventBusServiceComponent
    with AvroSerializers {

  override protected lazy val kafkaTopic: String = UserProducerActor.topicKey
  override protected val format: RecordFormat[UserEventWrapper] = RecordFormat[UserEventWrapper]
  override val groupId = "user-history"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: UserEventWrapper): Future[Unit] = {
    message.event.fold(HandledMessages) match {
      case event: ResetPasswordEvent              => doNothing(event)
      case event: UserRegisteredEvent             => handleUserRegisteredEvent(event)
      case event: UserConnectedEvent              => doNothing(event)
      case event: UserUpdatedTagEvent             => doNothing(event)
      case event: ResendValidationEmailEvent      => doNothing(event)
      case event: UserValidatedAccountEvent       => doNothing(event)
      case event: OrganisationRegisteredEvent     => doNothing(event)
      case event: OrganisationUpdatedEvent        => doNothing(event)
      case event: OrganisationInitializationEvent => doNothing(event)
    }
  }

  def handleUserRegisteredEvent(event: UserRegisteredEvent): Future[Unit] = {
    userHistoryCoordinator ! LogRegisterCitizenEvent(
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
          country = event.country,
          language = event.language
        )
      )
    )

    Future.successful {}
  }

}

object UserHistoryConsumerActor {
  def props(userHistoryCoordinator: ActorRef): Props =
    Props(new UserHistoryConsumerActor(userHistoryCoordinator))
  val name: String = "user-events-history-consumer"
}
