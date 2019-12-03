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
import org.make.api.technical.crm.SendMailPublisherService
import org.make.api.technical.{KafkaConsumerActor, TimeSettings}
import org.make.api.userhistory._
import org.make.core.user.{User, UserId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserEmailConsumerActor(userService: UserService, sendMailPublisherService: SendMailPublisherService)
    extends KafkaConsumerActor[UserEventWrapper] {

  override protected lazy val kafkaTopic: String = UserProducerActor.topicKey
  override protected val format: RecordFormat[UserEventWrapper] = UserEventWrapper.recordFormat
  override val groupId = "user-email"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: UserEventWrapper): Future[Unit] = {
    message.event match {
      case event: ResetPasswordEvent              => handleResetPasswordEvent(event)
      case event: UserRegisteredEvent             => handleUserRegisteredEvent(event)
      case event: UserValidatedAccountEvent       => handleUserValidatedAccountEvent(event)
      case event: UserConnectedEvent              => doNothing(event)
      case event: UserUpdatedTagEvent             => doNothing(event)
      case event: ResendValidationEmailEvent      => handleResendValidationEmailEvent(event)
      case event: OrganisationRegisteredEvent     => doNothing(event)
      case event: OrganisationUpdatedEvent        => doNothing(event)
      case event: OrganisationInitializationEvent => doNothing(event)
      case event: UserUpdatedOptInNewsletterEvent => doNothing(event)
      case event: UserAnonymizedEvent             => doNothing(event)
      case event: UserFollowEvent                 => doNothing(event)
      case event: UserUnfollowEvent               => doNothing(event)
    }
  }

  def handleResendValidationEmailEvent(event: ResendValidationEmailEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).flatMap {
      case Some(user) =>
        sendMailPublisherService.resendRegistration(user, event.country, event.language, event.requestContext)
      case None => Future.successful {}
    }
  }

  def handleUserValidatedAccountEvent(event: UserValidatedAccountEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).flatMap {
      case Some(user) =>
        sendMailPublisherService.publishWelcome(user, event.country, event.language, event.requestContext)
      case None => Future.successful {}
    }
  }

  def handleUserRegisteredEvent(event: UserRegisteredEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).flatMap {
      case Some(user) if !event.isSocialLogin =>
        sendMailPublisherService.publishRegistration(user, event.country, event.language, event.requestContext)
      case _ => Future.successful {}
    }
  }

  private def handleResetPasswordEvent(event: ResetPasswordEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).flatMap {
      case Some(organisation) if organisation.isOrganisation =>
        sendMailPublisherService.publishForgottenPasswordOrganisation(
          organisation,
          event.country,
          event.language,
          event.requestContext
        )
      case Some(user) =>
        sendMailPublisherService.publishForgottenPassword(user, event.country, event.language, event.requestContext)
      case None => Future.successful {}
    }
  }

  private def getUserWithValidEmail(userId: UserId): Future[Option[User]] = {
    userService.getUser(userId).map {
      case Some(user) if user.isHardBounce =>
        log.info(s"a hardbounced user (${user.userId}) will be ignored by email consumer")
        None
      case Some(user) => Some(user)
      case None =>
        log.warning(s"can't find user with id $userId")
        None
    }
  }
}

object UserEmailConsumerActor {
  def props(userService: UserService, sendMailPublisherService: SendMailPublisherService): Props =
    Props(new UserEmailConsumerActor(userService, sendMailPublisherService))
  val name: String = "user-events-consumer"
}
