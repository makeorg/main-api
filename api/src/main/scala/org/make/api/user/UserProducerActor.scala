package org.make.api.user

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.{ProducerActor, ProducerActorCompanion}
import org.make.api.userhistory.UserEvent
import org.make.api.userhistory.UserEvent._

class UserProducerActor extends ProducerActor[UserEventWrapper] {
  override protected lazy val eventClass: Class[UserEvent] = classOf[UserEvent]
  override protected lazy val format: RecordFormat[UserEventWrapper] = RecordFormat[UserEventWrapper]
  override protected lazy val schema: SchemaFor[UserEventWrapper] = SchemaFor[UserEventWrapper]

  val kafkaTopic: String =
    kafkaConfiguration.topics(UserProducerActor.topicKey)

  override def receive: Receive = {
    case event: ResetPasswordEvent         => onResetPassword(event)
    case event: ResendValidationEmailEvent => onResendValidationEmail(event)
    case event: UserRegisteredEvent        => onUserRegisteredEvent(event)
    case event: UserConnectedEvent         => onUserConnectedEvent(event)
    case event: UserUpdatedTagEvent        => onUserUpdatedTagEvent(event)
    case event: UserValidatedAccountEvent  => onUserValidatedAccountEvent(event)
    case other                             => log.warning("Unknown event {}", other)
  }

  def onUserRegisteredEvent(event: UserRegisteredEvent): Unit = {
    val record =
      UserEventWrapper(
        version = UserRegisteredEvent.version,
        id = event.userId.value,
        date = event.eventDate,
        eventType = event.getClass.getSimpleName,
        event = UserEventWrapper.wrapEvent(event)
      )
    sendRecord(kafkaTopic, event.userId.value, record)
  }

  def onResetPassword(event: ResetPasswordEvent): Unit = {
    log.debug(s"Received event $event")
    val record =
      UserEventWrapper(
        version = ResetPasswordEvent.version,
        id = event.userId.value,
        date = event.eventDate,
        eventType = event.getClass.getSimpleName,
        event = UserEventWrapper.wrapEvent(event)
      )
    sendRecord(kafkaTopic, event.userId.value, record)
  }

  def onResendValidationEmail(event: ResendValidationEmailEvent): Unit = {
    log.debug(s"Received event $event")
    val record =
      UserEventWrapper(
        version = ResendValidationEmailEvent.version,
        id = event.userId.value,
        date = event.eventDate,
        eventType = event.getClass.getSimpleName,
        event = UserEventWrapper.wrapEvent(event)
      )
    sendRecord(kafkaTopic, event.userId.value, record)
  }

  def onUserConnectedEvent(event: UserConnectedEvent): Unit = {
    log.debug(s"Received event $event")
    val record =
      UserEventWrapper(
        version = UserConnectedEvent.version,
        id = event.userId.value,
        date = event.eventDate,
        eventType = event.getClass.getSimpleName,
        event = UserEventWrapper.wrapEvent(event)
      )
    sendRecord(kafkaTopic, event.userId.value, record)
  }

  def onUserUpdatedTagEvent(event: UserUpdatedTagEvent): Unit = {
    log.debug(s"Received event $event")
    val record =
      UserEventWrapper(
        version = UserUpdatedTagEvent.version,
        id = event.userId.value,
        date = event.eventDate,
        eventType = event.getClass.getSimpleName,
        event = UserEventWrapper.wrapEvent(event)
      )
    sendRecord(kafkaTopic, event.userId.value, record)
  }

  def onUserValidatedAccountEvent(event: UserValidatedAccountEvent): Unit = {
    log.debug(s"Received event $event")
    val record =
      UserEventWrapper(
        version = UserValidatedAccountEvent.version,
        id = event.userId.value,
        date = event.eventDate,
        eventType = event.getClass.getSimpleName,
        event = UserEventWrapper.wrapEvent(event)
      )
    sendRecord(kafkaTopic, event.userId.value, record)
  }

}

object UserProducerActor extends ProducerActorCompanion {
  val props: Props = Props[UserProducerActor]
  override val name: String = "kafka-user-event-writer"
  override val topicKey: String = "users"
}
