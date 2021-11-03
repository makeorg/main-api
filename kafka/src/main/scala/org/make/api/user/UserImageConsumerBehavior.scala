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

import akka.actor.typed.Behavior
import akka.util.Timeout
import grizzled.slf4j.Logging
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.make.api.technical.{KafkaConsumerBehavior, TimeSettings}
import org.make.api.userhistory._

import java.util.Properties
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class UserImageConsumerBehavior(userService: UserService) extends KafkaConsumerBehavior[UserEventWrapper] with Logging {

  override protected val topicKey: String = UserProducerBehavior.topicKey
  override val groupId = "user-images"

  override def customProperties: Properties = {
    val props = new Properties()
// Fetch a batch of events only in order to handle some downloads but not all at once
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 4)
    props
  }
  override def handleMessagesTimeout: FiniteDuration = 5.minutes

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: UserEventWrapper): Future[_] = {
    message.event match {
      case event: ResetPasswordEvent              => doNothing(event)
      case event: UserRegisteredEvent             => doNothing(event)
      case event: UserConnectedEvent              => doNothing(event)
      case event: UserUpdatedTagEvent             => doNothing(event)
      case event: ResendValidationEmailEvent      => doNothing(event)
      case event: UserValidatedAccountEvent       => doNothing(event)
      case event: OrganisationRegisteredEvent     => doNothing(event)
      case event: OrganisationUpdatedEvent        => doNothing(event)
      case event: OrganisationEmailChangedEvent   => doNothing(event)
      case event: PersonalityEmailChangedEvent    => doNothing(event)
      case event: OrganisationInitializationEvent => doNothing(event)
      case event: UserUpdatedOptInNewsletterEvent => doNothing(event)
      case event: UserAnonymizedEvent             => doNothing(event)
      case event: UserFollowEvent                 => doNothing(event)
      case event: UserUnfollowEvent               => doNothing(event)
      case event: UserUploadAvatarEvent           => handleUserUploadAvatarEvent(event)
      case event: PersonalityRegisteredEvent      => doNothing(event)
    }
  }

  def handleUserUploadAvatarEvent(event: UserUploadAvatarEvent): Future[Unit] = {
    userService.changeAvatarForUser(event.userId, event.avatarUrl, event.requestContext, event.eventDate)
  }
}

object UserImageConsumerBehavior {
  def apply(userService: UserService): Behavior[KafkaConsumerBehavior.Protocol] =
    new UserImageConsumerBehavior(userService).createBehavior(name)
  val name: String = "user-image-consumer"
}
