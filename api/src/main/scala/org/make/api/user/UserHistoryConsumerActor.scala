package org.make.api.user

import akka.actor.{ActorRef, Props}
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.MailJetTemplateConfigurationExtension
import org.make.api.technical.{ActorEventBusServiceComponent, AvroSerializers, KafkaConsumerActor, TimeSettings}
import org.make.api.userhistory.UserEvent._
import org.make.api.userhistory.{LogRegisterCitizenEvent, UserAction, UserRegistered}

import scala.concurrent.Future

class UserHistoryConsumerActor(userHistoryCoordinator: ActorRef)
    extends KafkaConsumerActor[UserEventWrapper]
    with MailJetTemplateConfigurationExtension
    with ActorEventBusServiceComponent
    with AvroSerializers {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(UserProducerActor.topicKey)
  override protected val format: RecordFormat[UserEventWrapper] = RecordFormat[UserEventWrapper]
  override val groupId = "user-history"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: UserEventWrapper): Future[Unit] = {
    message.event.fold(HandledMessages) match {
      case event: ResetPasswordEvent         => doNothing(event)
      case event: UserRegisteredEvent        => handleUserRegisteredEvent(event)
      case event: UserConnectedEvent         => doNothing(event)
      case event: UserUpdatedTagEvent        => doNothing(event)
      case event: ResendValidationEmailEvent => doNothing(event)
      case event: UserValidatedAccountEvent  => doNothing(event)
    }
  }

  def handleUserRegisteredEvent(event: UserRegisteredEvent): Future[Unit] = {
    userHistoryCoordinator ! LogRegisterCitizenEvent(
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
          postalCode = event.postalCode,
          country = event.country,
          language = event.language
        )
      )
    )

    Future.successful {}
  }

}

object UserHistoryConsumerActor {
  def props(userHistoryCoordinator: ActorRef): Props =
    Props(new UserHistoryConsumerActor(userHistoryCoordinator))
  val name: String = "user-events-history-consumer"
}
