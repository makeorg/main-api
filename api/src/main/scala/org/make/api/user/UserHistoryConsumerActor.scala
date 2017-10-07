package org.make.api.user

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.MailJetTemplateConfigurationExtension
import org.make.api.technical.{ActorEventBusServiceComponent, AvroSerializers, KafkaConsumerActor}
import org.make.api.userhistory.{LogRegisterCitizenEvent, UserAction, UserRegistered}
import org.make.api.userhistory.UserEvent._
import shapeless.Poly1

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class UserHistoryConsumerActor(userHistoryCoordinator: ActorRef)
    extends KafkaConsumerActor[UserEventWrapper]
    with MailJetTemplateConfigurationExtension
    with ActorEventBusServiceComponent
    with AvroSerializers {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(UserProducerActor.topicKey)
  override protected val format: RecordFormat[UserEventWrapper] = RecordFormat[UserEventWrapper]
  override val groupId = "user-history"

  implicit val timeout: Timeout = Timeout(3.seconds)

  /*
   * Add an implicit for each event to manage
   */
  object HandledMessages extends Poly1 {
    implicit val atResetPasswordEvent: Case.Aux[ResetPasswordEvent, ResetPasswordEvent] = at(identity)
    implicit val atUserRegisteredEvent: Case.Aux[UserRegisteredEvent, UserRegisteredEvent] = at(identity)
    implicit val atUserConnectedEvent: Case.Aux[UserConnectedEvent, UserConnectedEvent] = at(identity)
    implicit val atResendValidationEmail: Case.Aux[ResendValidationEmailEvent, ResendValidationEmailEvent] =
      at(identity)
  }

  override def handleMessage(message: UserEventWrapper): Future[Unit] = {
    message.event.fold(HandledMessages) match {
      case event: ResetPasswordEvent         => handleResetPasswordEvent(event)
      case event: UserRegisteredEvent        => handleUserRegisteredEvent(event)
      case event: UserConnectedEvent         => handleUserConnectedEvent(event)
      case event: ResendValidationEmailEvent => handleResendValidationEmailEvent(event)
    }
  }

  def handleUserRegisteredEvent(event: UserRegisteredEvent): Future[Unit] = {
    (
      userHistoryCoordinator ? LogRegisterCitizenEvent(
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
            postalCode = event.postalCode
          )
        )
      )
    ).map(_ => {})
  }

  private def handleResetPasswordEvent(resetPasswordEvent: ResetPasswordEvent): Future[Unit] = {
    log.debug(s"got event $resetPasswordEvent")
    Future.successful {}
  }

  /**
    * Handles the resend validation email event and publishes as the send email event to the event bus
    * @param resendValidationEmailEvent resend validation email event
    * @return Future[Unit]
    */
  private def handleResendValidationEmailEvent(resendValidationEmailEvent: ResendValidationEmailEvent): Future[Unit] = {
    log.debug(s"got event $resendValidationEmailEvent")
    Future.successful {}

  }

  def handleUserConnectedEvent(event: UserConnectedEvent): Future[Unit] = {
    log.debug(s"got event $event")
    Future.successful {}
  }
}

object UserHistoryConsumerActor {
  def props(userHistoryCoordinator: ActorRef): Props =
    Props(new UserHistoryConsumerActor(userHistoryCoordinator))
  val name: String = "user-events-history-consumer"
}
