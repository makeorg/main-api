package org.make.api.user

import akka.actor.Props
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.MailJetTemplateConfigurationExtension
import org.make.api.technical.KafkaConsumerActor
import org.make.api.technical.mailjet.{Recipient, SendEmail}
import org.make.core.user.User
import org.make.core.user.UserEvent.{ResendValidationEmailEvent, ResetPasswordEvent, UserEventWrapper}
import shapeless.Poly1
import org.make.api.technical.AvroSerializers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserKafkaConsumerActor(userService: UserService)
    extends KafkaConsumerActor[UserEventWrapper](UserProducerActor.topicKey)
    with MailJetTemplateConfigurationExtension
    with AvroSerializers {

  override protected val format: RecordFormat[UserEventWrapper] = RecordFormat[UserEventWrapper]

  /*
   * Add an implicit for each event to manage
   */
  object HandledMessages extends Poly1 {
    implicit val atResetPasswordEvent: Case.Aux[ResetPasswordEvent, Future[Unit]] = at(handleResetPasswordEvent)
    implicit val atResendValidationEmail: Case.Aux[ResendValidationEmailEvent, Future[Unit]] = at(
      handleResendValidationEmail
    )
  }

  override def handleMessage(message: UserEventWrapper): Future[Unit] = {
    message.event.fold(HandledMessages)
  }

  private def handleResetPasswordEvent(resetPasswordEvent: ResetPasswordEvent): Future[Unit] = {
    val templateId = mailJetTemplateConfiguration.resetPasswordTemplate
    val from = mailJetTemplateConfiguration.from
    // toDo i18n
    val subject = "reset.password.subject"

    val futureConnectedUser: Future[Option[User]] = resetPasswordEvent.connectedUserId match {
      case Some(connectedUserId) => userService.getUser(connectedUserId)
      case _                     => Future(None)
    }
    val futureUser: Future[Option[User]] = userService.getUser(resetPasswordEvent.userId)

    val variables: Future[Map[String, String]] = for {
      mayBeConnectedUser <- futureConnectedUser
      mayBeUser          <- futureUser
    } yield {
      Map("connectedUser" -> mayBeConnectedUser.flatMap(_.fullName), "user" -> mayBeUser.flatMap(_.fullName))
        .filter({ case (k, v) => v.isDefined })
        .toList
        .flatMap {
          case (k, Some(v)) => List(k -> v)
          case (_, None)    => List()
        }
        .toMap
    }

    variables.map { variablesMap =>
      context.system.eventStream.publish(
        SendEmail(
          fromEmail = Some(from),
          templateId = Some(templateId),
          subject = Some(subject),
          recipients = Seq(Recipient(email = "", name = None)),
          variables = Some(variablesMap)
        )
      )
    }
  }

  /**
    * Handles the resend validation email event and publishes as the send email event to the event bus
    * @param resendValidationEmailEvent resend validation email event
    * @return Future[Unit]
    */
  private def handleResendValidationEmail(resendValidationEmailEvent: ResendValidationEmailEvent): Future[Unit] = {
    val templateId = mailJetTemplateConfiguration.resendValidationEmailTemplate
    val from = mailJetTemplateConfiguration.from
    // toDo i18n
    val subject = "resend.validation.email.subject"

    val futureConnectedUser: Future[Option[User]] = resendValidationEmailEvent.connectedUserId match {
      case Some(connectedUserId) => userService.getUser(connectedUserId)
      case _                     => Future(None)
    }
    val futureUser: Future[Option[User]] = userService.getUser(resendValidationEmailEvent.userId)

    val variables: Future[Map[String, String]] = for {
      mayBeConnectedUser <- futureConnectedUser
      mayBeUser          <- futureUser
    } yield {
      Map("connectedUser" -> mayBeConnectedUser.flatMap(_.fullName), "user" -> mayBeUser.flatMap(_.fullName))
        .filter({ case (k, v) => v.isDefined })
        .toList
        .flatMap {
          case (k, Some(v)) => List(k -> v)
          case (_, None)    => List()
        }
        .toMap
    }

    variables.map { variablesMap =>
      context.system.eventStream.publish(
        SendEmail(
          fromEmail = Some(from),
          templateId = Some(templateId),
          subject = Some(subject),
          recipients = Seq(Recipient(email = "", name = None)),
          variables = Some(variablesMap)
        )
      )
    }
  }
}

object UserKafkaConsumerActor {
  def props(userService: UserService): Props = Props(new UserKafkaConsumerActor(userService))
  val name: String = "kafka-user-event-writer"
}
