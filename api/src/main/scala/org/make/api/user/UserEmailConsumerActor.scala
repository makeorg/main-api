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

import java.time.LocalDate

import akka.actor.Props
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.{MailJetTemplateConfigurationExtension, MakeSettingsExtension}
import org.make.api.operation.OperationService
import org.make.api.technical.businessconfig.BusinessConfig
import org.make.api.technical.crm.{Recipient, SendEmail}
import org.make.api.technical.{ActorEventBusServiceComponent, AvroSerializers, KafkaConsumerActor, TimeSettings}
import org.make.api.userhistory.UserEvent._
import org.make.core.reference.{Country, Language}
import org.make.core.user.{User, UserId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserEmailConsumerActor(userService: UserService, operationService: OperationService)
    extends KafkaConsumerActor[UserEventWrapper]
    with MakeSettingsExtension
    with MailJetTemplateConfigurationExtension
    with ActorEventBusServiceComponent
    with AvroSerializers {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(UserProducerActor.topicKey)
  override protected val format: RecordFormat[UserEventWrapper] = RecordFormat[UserEventWrapper]
  override val groupId = "user-email"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: UserEventWrapper): Future[Unit] = {
    message.event.fold(HandledMessages) match {
      case event: ResetPasswordEvent          => handleResetPasswordEvent(event)
      case event: UserRegisteredEvent         => handleUserRegisteredEventEvent(event)
      case event: UserValidatedAccountEvent   => handleUserValidatedAccountEvent(event)
      case event: UserConnectedEvent          => doNothing(event)
      case event: UserUpdatedTagEvent         => doNothing(event)
      case event: ResendValidationEmailEvent  => handleResendValidationEmailEvent(event)
      case event: OrganisationRegisteredEvent => doNothing(event)
    }
  }

  def handleUserValidatedAccountEvent(event: UserValidatedAccountEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        val language = event.language
        val country = event.country

        val futureOperationSlug: Future[String] = event.requestContext.operationId match {
          case Some(operationId) => operationService.findOne(operationId).map(_.map(_.slug).getOrElse("core"))
          case None              => Future.successful("core")
        }

        futureOperationSlug.map { operationSlug =>
          val templateConfiguration = mailJetTemplateConfiguration
            .welcome(operation = operationSlug, country = country, language = language)

          if (templateConfiguration.enabled) {
            eventBusService.publish(
              SendEmail.create(
                templateId = Some(templateConfiguration.templateId),
                recipients = Seq(Recipient(email = user.email, name = user.fullName)),
                from = Some(
                  Recipient(
                    name = Some(mailJetTemplateConfiguration.fromName),
                    email = mailJetTemplateConfiguration.from
                  )
                ),
                variables = Some(
                  Map(
                    "firstname" -> user.firstName.getOrElse(""),
                    "registration_context" -> operationSlug,
                    "operation" -> event.requestContext.operationId.map(_.value).getOrElse(""),
                    "question" -> event.requestContext.question.getOrElse(""),
                    "location" -> event.requestContext.location.getOrElse(""),
                    "source" -> event.requestContext.source.getOrElse("")
                  )
                ),
                customCampaign = templateConfiguration.customCampaign,
                monitoringCategory = templateConfiguration.monitoringCategory
              )
            )
          }
        }
      }
    }
  }

  def handleUserRegisteredEventEvent(event: UserRegisteredEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        val language = event.language
        val country = event.country

        //todo: refactor to handle multiple operation by country
        val futureOperationSlug: Future[String] = event.requestContext.operationId match {
          case Some(operationId) => operationService.findOne(operationId).map(_.map(_.slug).getOrElse("core"))
          case None =>
            if (BusinessConfig.coreIsAvailableForCountry(country)) {
              Future.successful("core")
            } else {
              operationService
                .find(country = Some(country), openAt = Some(LocalDate.now()))
                .map(_.headOption.map(_.slug).getOrElse("core"))
            }
        }

        futureOperationSlug.map { operationSlug =>
          val registration =
            mailJetTemplateConfiguration.registration(operation = operationSlug, country = country, language = language)

          if (registration.enabled) {
            val verificationToken: String = user.verificationToken match {
              case Some(token) => token
              case _           => throw new IllegalStateException
            }
            val url = s"${mailJetTemplateConfiguration
              .getFrontUrl()}?utm_source=crm&utm_medium=email&utm_campaign=core&utm_term=validation&utm_content=cta#/${user.country}/account-activation/${user.userId.value}/${verificationToken}" +
              s"?operation=${event.requestContext.operationId
                .map(_.value)
                .getOrElse(operationSlug)}&language=$language&country=$country"

            eventBusService.publish(
              SendEmail.create(
                templateId = Some(registration.templateId),
                recipients = Seq(Recipient(email = user.email, name = user.fullName)),
                from = Some(
                  Recipient(
                    name = Some(mailJetTemplateConfiguration.fromName),
                    email = mailJetTemplateConfiguration.from
                  )
                ),
                variables = Some(
                  Map(
                    "firstname" -> user.firstName.getOrElse(""),
                    "email_validation_url" -> url,
                    "operation" -> event.requestContext.operationId.map(_.value).getOrElse(""),
                    "question" -> event.requestContext.question.getOrElse(""),
                    "location" -> event.requestContext.location.getOrElse(""),
                    "source" -> event.requestContext.source.getOrElse("")
                  )
                ),
                customCampaign = registration.customCampaign,
                monitoringCategory = registration.monitoringCategory
              )
            )
          }
        }
      }
    }
  }

  private def handleResetPasswordEvent(event: ResetPasswordEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        val language = event.language
        val country = event.country

        val futureOperationSlug: Future[String] = event.requestContext.operationId match {
          case Some(operationId) => operationService.findOne(operationId).map(_.map(_.slug).getOrElse("core"))
          case None              => Future.successful("core")
        }

        futureOperationSlug.map { operationSlug =>
          val forgottenPassword =
            mailJetTemplateConfiguration
              .forgottenPassword(operation = operationSlug, country = country, language = language)

          if (forgottenPassword.enabled) {
            val resetToken: String = user.resetToken match {
              case Some(token) => token
              case _           => throw new IllegalStateException("reset token required")
            }
            val url = s"${mailJetTemplateConfiguration
              .getFrontUrl()}/#/${user.country}/password-recovery/${user.userId.value}/$resetToken" +
              s"?operation=${event.requestContext.operationId.map(_.value).getOrElse("core")}&language=$language&country=$country"

            context.system.eventStream.publish(
              SendEmail.create(
                templateId = Some(forgottenPassword.templateId),
                recipients = Seq(Recipient(email = user.email, name = user.fullName)),
                from = Some(
                  Recipient(
                    name = Some(mailJetTemplateConfiguration.fromName),
                    email = mailJetTemplateConfiguration.from
                  )
                ),
                variables = Some(
                  Map(
                    "firstname" -> user.firstName.getOrElse(""),
                    "forgotten_password_url" -> url,
                    "operation" -> event.requestContext.operationId.map(_.value).getOrElse(""),
                    "question" -> event.requestContext.question.getOrElse(""),
                    "location" -> event.requestContext.location.getOrElse(""),
                    "source" -> event.requestContext.source.getOrElse("")
                  )
                ),
                customCampaign = forgottenPassword.customCampaign,
                monitoringCategory = forgottenPassword.monitoringCategory
              )
            )
          }
        }
      }
    }
  }

  /**
    * Handles the resend validation email event and publishes as the send email event to the event bus
    * @param event resend validation email event
    * @return Future[Unit]
    */
  private def handleResendValidationEmailEvent(event: ResendValidationEmailEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        val language = event.requestContext.language.getOrElse(Language("fr"))
        val country = event.requestContext.country.getOrElse(Country("FR"))

        val futureOperationSlug: Future[String] = event.requestContext.operationId match {
          case Some(operationId) => operationService.findOne(operationId).map(_.map(_.slug).getOrElse("core"))
          case None              => Future.successful("core")
        }

        futureOperationSlug.map { operationSlug =>
          val resendAccountValidationLink = mailJetTemplateConfiguration
            .resendAccountValidationLink(operation = operationSlug, country = country, language = language)

          if (resendAccountValidationLink.enabled) {
            val verificationToken: String = user.verificationToken match {
              case Some(token) => token
              case _           => throw new IllegalStateException("validation token required")
            }
            val url = s"${mailJetTemplateConfiguration
              .getFrontUrl()}?utm_source=crm&utm_medium=email&utm_campaign=core&utm_term=validation&utm_content=cta#/${user.country}/account-activation/${user.userId.value}/$verificationToken" +
              s"?operation=${event.requestContext.operationId.map(_.value).getOrElse("core")}&language=$language&country=$country"
            eventBusService.publish(
              SendEmail.create(
                templateId = Some(resendAccountValidationLink.templateId),
                recipients = Seq(Recipient(email = user.email, name = user.fullName)),
                from = Some(
                  Recipient(
                    name = Some(mailJetTemplateConfiguration.fromName),
                    email = mailJetTemplateConfiguration.from
                  )
                ),
                variables = Some(
                  Map(
                    "firstname" -> user.firstName.getOrElse(""),
                    "email_validation_url" -> url,
                    "operation" -> event.requestContext.operationId.map(_.value).getOrElse(""),
                    "question" -> event.requestContext.question.getOrElse(""),
                    "location" -> event.requestContext.location.getOrElse(""),
                    "source" -> event.requestContext.source.getOrElse("")
                  )
                ),
                customCampaign = resendAccountValidationLink.customCampaign,
                monitoringCategory = resendAccountValidationLink.monitoringCategory
              )
            )
          }
        }
      }
    }
  }

  private def getUserWithValidEmail(userId: UserId): Future[Option[User]] = {
    userService.getUser(userId).map {
      case Some(user) if user.isHardBounce =>
        log.info(s"an hardbounced user (${user.email}) will be ignored by email consumer")
        None
      case other => other
    }
  }
}

object UserEmailConsumerActor {
  def props(userService: UserService, operationService: OperationService): Props =
    Props(new UserEmailConsumerActor(userService, operationService))
  val name: String = "user-events-consumer"
}
