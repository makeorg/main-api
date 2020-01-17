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
import com.typesafe.scalalogging.StrictLogging
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor, TimeSettings}
import org.make.api.userhistory._
import org.make.core.AvroSerializers
import org.make.core.profile.Profile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserImageConsumerActor(userHistoryCoordinatorService: UserHistoryCoordinatorService, userService: UserService)
    extends KafkaConsumerActor[UserEventWrapper]
    with ActorEventBusServiceComponent
    with AvroSerializers
    with StrictLogging {

  override protected lazy val kafkaTopic: String = UserProducerActor.topicKey
  override protected val format: RecordFormat[UserEventWrapper] = UserEventWrapper.recordFormat
  override val groupId = "user-images"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: UserEventWrapper): Future[Unit] = {
    message.event match {
      case event: ResetPasswordEvent              => doNothing(event)
      case event: UserRegisteredEvent             => doNothing(event)
      case event: UserConnectedEvent              => doNothing(event)
      case event: UserUpdatedTagEvent             => doNothing(event)
      case event: ResendValidationEmailEvent      => doNothing(event)
      case event: UserValidatedAccountEvent       => doNothing(event)
      case event: OrganisationRegisteredEvent     => doNothing(event)
      case event: OrganisationUpdatedEvent        => doNothing(event)
      case event: OrganisationInitializationEvent => doNothing(event)
      case event: UserUpdatedOptInNewsletterEvent => doNothing(event)
      case event: UserAnonymizedEvent             => doNothing(event)
      case event: UserFollowEvent                 => doNothing(event)
      case event: UserUnfollowEvent               => doNothing(event)
      case event: UserUploadAvatarEvent           => handleUserUploadAvatarEvent(event)
    }
  }

  def handleUserUploadAvatarEvent(event: UserUploadAvatarEvent): Future[Unit] = {
    userService
      .changeAvatarForUser(event.userId, event.avatarUrl)
      .flatMap(
        path =>
          userService.getUser(event.userId).flatMap {
            case Some(user) =>
              val newProfile: Option[Profile] = user.profile match {
                case Some(profile) => Some(profile.copy(avatarUrl = Some(path)))
                case None          => Profile.parseProfile(avatarUrl = Some(path))
              }
              userService.update(user.copy(profile = newProfile), event.requestContext).map(_ => {})
            case None =>
              logger.warn(s"Could not find user ${event.userId} to upload avatar")
              Future.successful {}
        }
      )
      .map(
        _ =>
          userHistoryCoordinatorService.logHistory(
            LogUserUploadedAvatarEvent(
              userId = event.userId,
              requestContext = event.requestContext,
              action = UserAction(
                date = event.eventDate,
                actionType = LogUserAnonymizedEvent.action,
                arguments = UploadedAvatar(avatarUrl = event.avatarUrl)
              )
            )
        )
      )
  }
}

object UserImageConsumerActor {
  def props(userHistoryCoordinatorService: UserHistoryCoordinatorService, userService: UserService): Props =
    Props(new UserImageConsumerActor(userHistoryCoordinatorService, userService))
  val name: String = "user-events-image-consumer"
}