package org.make.api.user

import akka.actor.Props
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.{MailJetTemplateConfigurationExtension, MakeSettingsExtension}
import org.make.api.operation.OperationService
import org.make.api.technical.mailjet.{Recipient, SendEmail}
import org.make.api.technical.{ActorEventBusServiceComponent, AvroSerializers, KafkaConsumerActor}
import org.make.api.userhistory.UserEvent._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class UserEmailConsumerActor(userService: UserService, operationService: OperationService)
    extends KafkaConsumerActor[UserEventWrapper]
    with MakeSettingsExtension
    with MailJetTemplateConfigurationExtension
    with ActorEventBusServiceComponent
    with AvroSerializers {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(UserProducerActor.topicKey)
  override protected val format: RecordFormat[UserEventWrapper] = RecordFormat[UserEventWrapper]
  override val groupId = "user-email"

  implicit val timeout: Timeout = Timeout(3.seconds)

  override def handleMessage(message: UserEventWrapper): Future[Unit] = {
    message.event.fold(HandledMessages) match {
      case event: ResetPasswordEvent         => handleResetPasswordEvent(event)
      case event: UserRegisteredEvent        => handleUserRegisteredEventEvent(event)
      case event: UserValidatedAccountEvent  => handleUserValidatedAccountEvent(event)
      case event: UserConnectedEvent         => doNothing(event)
      case event: UserUpdatedTagEvent        => doNothing(event)
      case event: ResendValidationEmailEvent => handleResendValidationEmailEvent(event)
    }
  }

  def handleUserValidatedAccountEvent(event: UserValidatedAccountEvent): Future[Unit] = {
    userService.getUser(event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        val language = event.requestContext.language.getOrElse("fr")
        val country = event.requestContext.country.getOrElse("FR")

        val futureOperationSlug: Future[String] = event.requestContext.operationId match {
          case Some(operationId) => operationService.findOne(operationId).map(_.map(_.slug).getOrElse("core"))
          case None              => Future.successful("core")
        }

        futureOperationSlug.map { operationSlug =>
          val templateConfiguration = mailJetTemplateConfiguration
            .welcome(operation = operationSlug, country = country, language = language)

          if (templateConfiguration.enabled) {
            eventBusService.publish(
              SendEmail(
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
                customCampaign = Some(templateConfiguration.customCampaign),
                monitoringCategory = Some(templateConfiguration.monitoringCategory)
              )
            )
          }
        }
      }
    }
  }

  def handleUserRegisteredEventEvent(event: UserRegisteredEvent): Future[Unit] = {
    userService.getUser(event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        val language = event.requestContext.language.getOrElse("fr")
        val country = event.requestContext.country.getOrElse("FR")

        val futureOperationSlug: Future[String] = event.requestContext.operationId match {
          case Some(operationId) => operationService.findOne(operationId).map(_.map(_.slug).getOrElse("core"))
          case None              => Future.successful("core")
        }

        futureOperationSlug.map { operationSlug =>
          val registration =
            mailJetTemplateConfiguration.registration(operation = operationSlug, country = country, language = language)

          if (registration.enabled) {
            val url = s"${mailJetTemplateConfiguration
              .getFrontUrl()}/#/account-activation/${user.userId.value}/${user.verificationToken.get}" +
              s"?operation=${event.requestContext.operationId.map(_.value).getOrElse("core")}&language=$language&country=$country"

            eventBusService.publish(
              SendEmail(
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
                customCampaign = Some(registration.customCampaign),
                monitoringCategory = Some(registration.monitoringCategory)
              )
            )
          }
        }
      }
    }
  }

  private def handleResetPasswordEvent(event: ResetPasswordEvent): Future[Unit] = {
    userService.getUser(event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        val language = event.requestContext.language.getOrElse("fr")
        val country = event.requestContext.country.getOrElse("FR")

        val futureOperationSlug: Future[String] = event.requestContext.operationId match {
          case Some(operationId) => operationService.findOne(operationId).map(_.map(_.slug).getOrElse("core"))
          case None              => Future.successful("core")
        }

        futureOperationSlug.map { operationSlug =>
          val forgottenPassword =
            mailJetTemplateConfiguration
              .forgottenPassword(operation = operationSlug, country = country, language = language)

          if (forgottenPassword.enabled) {
            val url = s"${mailJetTemplateConfiguration
              .getFrontUrl()}/#/password-recovery/${user.userId.value}/${user.resetToken.get}" +
              s"?operation=${event.requestContext.operationId.map(_.value).getOrElse("core")}&language=$language&country=$country"

            context.system.eventStream.publish(
              SendEmail(
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
                customCampaign = Some(forgottenPassword.customCampaign),
                monitoringCategory = Some(forgottenPassword.monitoringCategory)
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
    userService.getUser(event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        val language = event.requestContext.language.getOrElse("fr")
        val country = event.requestContext.country.getOrElse("FR")

        val futureOperationSlug: Future[String] = event.requestContext.operationId match {
          case Some(operationId) => operationService.findOne(operationId).map(_.map(_.slug).getOrElse("core"))
          case None              => Future.successful("core")
        }

        futureOperationSlug.map { operationSlug =>
          val resendAccountValidationLink = mailJetTemplateConfiguration
            .resendAccountValidationLink(operation = operationSlug, country = country, language = language)

          if (resendAccountValidationLink.enabled) {
            val url = s"${mailJetTemplateConfiguration
              .getFrontUrl()}/#/account-activation/${user.userId.value}/${user.verificationToken.get}" +
              s"?operation=${event.requestContext.operationId.map(_.value).getOrElse("core")}&language=$language&country=$country"
            eventBusService.publish(
              SendEmail(
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
                customCampaign = Some(resendAccountValidationLink.customCampaign),
                monitoringCategory = Some(resendAccountValidationLink.monitoringCategory)
              )
            )
          }
        }
      }
    }
  }
}

object UserEmailConsumerActor {
  def props(userService: UserService, operationService: OperationService): Props =
    Props(new UserEmailConsumerActor(userService, operationService))
  val name: String = "user-events-consumer"
}
