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

import akka.actor.{ActorLogging, Props}
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.MakeSettingsExtension
import org.make.api.technical.crm.PublishedCrmContactEvent._
import org.make.api.technical.{ActorEventBusServiceComponent, AvroSerializers, KafkaConsumerActor, TimeSettings}
import org.make.api.user.UserUpdateEvent._
import org.make.core.DateHelper
import org.make.core.user.{User, UserId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserCrmConsumerActor(userService: UserService)
    extends KafkaConsumerActor[UserUpdateEventWrapper]
    with MakeSettingsExtension
    with ActorEventBusServiceComponent
    with AvroSerializers
    with ActorLogging {

  override protected lazy val kafkaTopic: String = UserUpdateProducerActor.topicKey
  override protected val format: RecordFormat[UserUpdateEventWrapper] = RecordFormat[UserUpdateEventWrapper]
  override val groupId = "user-crm"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: UserUpdateEventWrapper): Future[Unit] = {
    message.event.fold(HandledMessages) match {

      case event: UserCreatedEvent                => handleUserCreatedEvent(event)
      case event: UserUpdatedEvent                => handleUserUpdatedEvent(event)
      case event: UserUpdatedHardBounceEvent      => handleUserUpdatedHardBounceEvent(event)
      case event: UserUpdatedOptInNewsletterEvent => handleUserUpdatedOptInNewsletterEvent(event)
      case event: UserUpdateValidatedEvent        => handleUserUpdateValidatedEvent(event)
      case event: UserUpdatedPasswordEvent        => doNothing(event)
      case event: UserUpdatedTagEvent             => doNothing(event)
      case event: UserAnonymizedEvent             => handleUserAnonymizedEvent(event)
      case event: UserFollowEvent                 => doNothing(event)
      case event: UserUnfollowEvent               => doNothing(event)
    }
  }

  def handleUserUpdatedHardBounceEvent(event: UserUpdatedHardBounceEvent): Future[Unit] = {
    getUserFromEmailOrUserId(event.email, event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        eventBusService.publish(CrmContactHardBounce(id = user.userId, eventDate = DateHelper.now()))
      }
    }
  }

  def handleUserUpdatedOptInNewsletterEvent(event: UserUpdatedOptInNewsletterEvent): Future[Unit] = {
    getUserFromEmailOrUserId(event.email, event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        if (event.optInNewsletter) {
          eventBusService.publish(CrmContactSubscribe(id = user.userId, eventDate = DateHelper.now()))
        } else {
          eventBusService.publish(CrmContactUnsubscribe(id = user.userId, eventDate = DateHelper.now()))
        }
      }
    }
  }

  def handleUserUpdateValidatedEvent(event: UserUpdateValidatedEvent): Future[Unit] = {
    getUserFromEmailOrUserId(event.email, event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        eventBusService.publish(CrmContactUpdateProperties(id = user.userId, eventDate = DateHelper.now()))
      }
    }
  }

  def handleUserCreatedEvent(event: UserCreatedEvent): Future[Unit] = {
    getUserFromEmailOrUserId(event.email, event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        eventBusService.publish(CrmContactNew(id = user.userId, eventDate = DateHelper.now()))
      }
    }
  }

  def handleUserUpdatedEvent(event: UserUpdatedEvent): Future[Unit] = {
    getUserFromEmailOrUserId(event.email, event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        eventBusService.publish(CrmContactUpdateProperties(id = user.userId, eventDate = DateHelper.now()))
      }
    }
  }

  def handleUserAnonymizedEvent(event: UserAnonymizedEvent): Future[Unit] = {
    getUserFromEmailOrUserId(event.email, event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        eventBusService.publish(CrmContactRemoveFromLists(id = user.userId, eventDate = DateHelper.now()))
      }
    }
  }

  private def getUserFromEmailOrUserId(email: Option[String], userId: Option[UserId]): Future[Option[User]] = {
    (userId, email) match {
      case (Some(id), _) => userService.getUser(id)
      case (_, Some(e))  => userService.getUserByEmail(e)
      case _ =>
        log.warning("User event has been sent without email or userId")
        Future.successful(None)
    }
  }
}

object UserCrmConsumerActor {
  def props(userService: UserService): Props =
    Props(new UserCrmConsumerActor(userService))
  val name: String = "user-crm-events-consumer"
}
