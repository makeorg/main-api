package org.make.api.user

import akka.actor.Props
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.{MailJetTemplateConfigurationExtension, MakeSettingsExtension}
import org.make.api.technical.mailjet.{Recipient, SendEmail}
import org.make.api.technical.{ActorEventBusServiceComponent, AvroSerializers, KafkaConsumerActor}
import org.make.core.user.UserEvent.{
  ResendValidationEmailEvent,
  ResetPasswordEvent,
  UserEventWrapper,
  UserRegisteredEvent
}
import shapeless.Poly1

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class UserEmailConsumerActor(userService: UserService)
    extends KafkaConsumerActor[UserEventWrapper]
    with MakeSettingsExtension
    with MailJetTemplateConfigurationExtension
    with ActorEventBusServiceComponent
    with AvroSerializers {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(UserProducerActor.topicKey)
  override protected val format: RecordFormat[UserEventWrapper] = RecordFormat[UserEventWrapper]
  override val groupId = "user-email"

  implicit val timeout: Timeout = Timeout(3.seconds)

  /*
   * Add an implicit for each event to manage
   */
  object HandledMessages extends Poly1 {
    implicit val atResetPasswordEvent: Case.Aux[ResetPasswordEvent, ResetPasswordEvent] = at(identity)
    implicit val atUserRegisteredEvent: Case.Aux[UserRegisteredEvent, UserRegisteredEvent] = at(identity)
    implicit val atResendValidationEmail: Case.Aux[ResendValidationEmailEvent, ResendValidationEmailEvent] =
      at(identity)
  }

  override def handleMessage(message: UserEventWrapper): Future[Unit] = {
    message.event.fold(HandledMessages) match {
      case event: ResetPasswordEvent         => handleResetPasswordEvent(event)
      case event: UserRegisteredEvent        => handleUserRegisteredEventEvent(event)
      case event: ResendValidationEmailEvent => handleResendValidationEmailEvent(event)
    }
  }

  def handleUserRegisteredEventEvent(event: UserRegisteredEvent): Future[Unit] = {
    userService.getUser(event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        eventBusService.publish(
          SendEmail(
            templateId = Some(mailJetTemplateConfiguration.userRegisteredTemplate),
            recipients = Seq(Recipient(email = user.email, name = user.fullName)),
            from = Some(
              Recipient(name = Some(mailJetTemplateConfiguration.fromName), email = mailJetTemplateConfiguration.from)
            ),
            variables = Some(
              Map(
                "name" -> user.fullName.getOrElse(""),
                "url" -> s"${settings.frontUrl}/#/account-activation/${user.userId.value}/${user.verificationToken.get}"
              )
            )
          )
        )
      }
    }
  }

  private def handleResetPasswordEvent(resetPasswordEvent: ResetPasswordEvent): Future[Unit] = {
    userService.getUser(resetPasswordEvent.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        context.system.eventStream.publish(
          SendEmail(
            templateId = Some(mailJetTemplateConfiguration.resetPasswordTemplate),
            recipients = Seq(Recipient(email = user.email, name = user.fullName)),
            from = Some(
              Recipient(name = Some(mailJetTemplateConfiguration.fromName), email = mailJetTemplateConfiguration.from)
            ),
            variables = Some(
              Map(
                "name" -> user.fullName.getOrElse(""),
                "url" -> s"${settings.frontUrl}/#/password-recovery/${user.userId.value}/${user.resetToken.get}"
              )
            )
          )
        )
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
        eventBusService.publish(
          SendEmail(
            templateId = Some(mailJetTemplateConfiguration.resendValidationEmailTemplate),
            recipients = Seq(Recipient(email = user.email, name = user.fullName)),
            from = Some(
              Recipient(name = Some(mailJetTemplateConfiguration.fromName), email = mailJetTemplateConfiguration.from)
            ),
            variables = Some(
              Map(
                "name" -> user.fullName.getOrElse(""),
                "url" -> s"${settings.frontUrl}/#/account-activation/${user.userId.value}/${user.verificationToken.get}"
              )
            )
          )
        )
      }
    }
  }
}

object UserEmailConsumerActor {
  def props(userService: UserService): Props =
    Props(new UserEmailConsumerActor(userService))
  val name: String = "user-events-consumer"
}
