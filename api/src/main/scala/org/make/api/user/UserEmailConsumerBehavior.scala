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
import org.make.api.technical.KafkaConsumerBehavior.Protocol
import org.make.api.technical.crm.SendMailPublisherService
import org.make.api.technical.{KafkaConsumerBehavior, TimeSettings}
import org.make.api.userhistory._
import org.make.core.user.{User, UserId}
import org.make.core.user.UserType._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserEmailConsumerBehavior(userService: UserService, sendMailPublisherService: SendMailPublisherService)
    extends KafkaConsumerBehavior[UserEventWrapper]
    with Logging {

  override protected val topicKey: String = UserProducerBehavior.topicKey
  override val groupId = "user-email"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: UserEventWrapper): Future[_] = {
    message.event match {
      case event: ResetPasswordEvent              => handleResetPasswordEvent(event)
      case event: UserRegisteredEvent             => handleUserRegisteredEvent(event)
      case event: UserValidatedAccountEvent       => handleUserValidatedAccountEvent(event)
      case event: UserConnectedEvent              => doNothing(event)
      case event: UserUpdatedTagEvent             => doNothing(event)
      case event: ResendValidationEmailEvent      => handleResendValidationEmailEvent(event)
      case event: OrganisationRegisteredEvent     => handleUserB2BRegisteredEvent(event)
      case event: OrganisationUpdatedEvent        => doNothing(event)
      case event: OrganisationEmailChangedEvent   => handleOrganisationEmailChangedEvent(event)
      case event: PersonalityEmailChangedEvent    => handlePersonalityEmailChangedEvent(event)
      case event: OrganisationInitializationEvent => doNothing(event)
      case event: UserUpdatedOptInNewsletterEvent => doNothing(event)
      case event: UserAnonymizedEvent             => doNothing(event)
      case event: UserFollowEvent                 => doNothing(event)
      case event: UserUnfollowEvent               => doNothing(event)
      case event: UserUploadAvatarEvent           => doNothing(event)
      case event: PersonalityRegisteredEvent      => handleUserB2BRegisteredEvent(event)
    }
  }

  def handleResendValidationEmailEvent(event: ResendValidationEmailEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).flatMap {
      case Some(user) =>
        sendMailPublisherService.resendRegistration(user, event.requestContext)
      case None => Future.unit
    }
  }

  def handleUserValidatedAccountEvent(event: UserValidatedAccountEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).flatMap {
      case Some(user) if user.isB2C =>
        sendMailPublisherService.publishWelcome(user, event.requestContext)
      case _ => Future.unit
    }
  }

  def handleUserB2BRegisteredEvent(event: B2BRegisteredEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).flatMap {
      case Some(user) if user.isB2B =>
        sendMailPublisherService.publishRegistrationB2B(user, event.requestContext)
      case _ => Future.unit
    }
  }

  def handleUserRegisteredEvent(event: UserRegisteredEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).flatMap {
      case Some(user) if user.isB2C && !event.isSocialLogin =>
        sendMailPublisherService.publishRegistration(user, event.requestContext)
      case _ => Future.unit
    }
  }

  private def handleResetPasswordEvent(event: ResetPasswordEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).flatMap {
      case Some(user) if user.isB2B =>
        sendMailPublisherService.publishForgottenPasswordOrganisation(user, event.requestContext)
      case Some(user) =>
        sendMailPublisherService.publishForgottenPassword(user, event.requestContext)
      case None => Future.unit
    }
  }

  private def handleOrganisationEmailChangedEvent(event: OrganisationEmailChangedEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).flatMap {
      case Some(organisation) =>
        sendMailPublisherService.publishEmailChanged(
          user = organisation,
          requestContext = event.requestContext,
          newEmail = event.newEmail
        )
      case None => Future.unit
    }
  }

  private def handlePersonalityEmailChangedEvent(event: PersonalityEmailChangedEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).flatMap {
      case Some(personality) =>
        sendMailPublisherService.publishEmailChanged(
          user = personality,
          requestContext = event.requestContext,
          newEmail = event.newEmail
        )
      case None => Future.unit
    }
  }

  private def getUserWithValidEmail(userId: UserId): Future[Option[User]] = {
    userService.getUser(userId).map {
      case Some(user) if user.isHardBounce =>
        info(s"a hardbounced user (${user.userId}) will be ignored by email consumer")
        None
      case Some(user) => Some(user)
      case None =>
        warn(s"can't find user with id $userId")
        None
    }
  }
}

object UserEmailConsumerBehavior {
  def apply(userService: UserService, sendMailPublisherService: SendMailPublisherService): Behavior[Protocol] =
    new UserEmailConsumerBehavior(userService, sendMailPublisherService).createBehavior(name)
  val name: String = "user-consumer"
}
