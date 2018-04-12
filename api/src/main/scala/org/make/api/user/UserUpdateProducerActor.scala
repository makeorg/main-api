package org.make.api.user

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import com.typesafe.scalalogging.StrictLogging
import org.make.api.technical.{BasicProducerActor, ProducerActorCompanion}
import org.make.api.user.UserUpdateEvent.UserUpdateEventWrapper

class UserUpdateProducerActor extends BasicProducerActor[UserUpdateEventWrapper, UserUpdateEvent] with StrictLogging {
  override protected lazy val eventClass: Class[UserUpdateEvent] = classOf[UserUpdateEvent]
  override protected lazy val format: RecordFormat[UserUpdateEventWrapper] = RecordFormat[UserUpdateEventWrapper]
  override protected lazy val schema: SchemaFor[UserUpdateEventWrapper] = SchemaFor[UserUpdateEventWrapper]
  override val kafkaTopic: String = kafkaConfiguration.topics(UserUpdateProducerActor.topicKey)

  override protected def convert(event: UserUpdateEvent): UserUpdateEventWrapper = {
    logger.debug(s"Produce UserUpdateEvent: ${event.toString}")
    val identifier: String = (event.userId, event.email) match {
      case (Some(userId), _) => userId.value
      case (_, Some(email))  => email
      case _ =>
        log.warning("User event has been sent without email or userId")
        "invalid"
    }

    UserUpdateEventWrapper(
      id = identifier,
      version = event.version(),
      date = event.eventDate,
      eventType = event.getClass.getSimpleName,
      event = UserUpdateEventWrapper.wrapEvent(event)
    )
  }
}

object UserUpdateProducerActor extends ProducerActorCompanion {
  val props: Props = Props[UserUpdateProducerActor]
  override val name: String = "kafka-user-update-event-writer"
  override val topicKey: String = "users-update"
}
