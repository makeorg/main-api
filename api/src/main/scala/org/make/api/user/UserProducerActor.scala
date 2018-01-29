package org.make.api.user

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.{BasicProducerActor, ProducerActorCompanion}
import org.make.api.userhistory.UserEvent
import org.make.api.userhistory.UserEvent._

class UserProducerActor extends BasicProducerActor[UserEventWrapper, UserEvent] {
  override protected lazy val eventClass: Class[UserEvent] = classOf[UserEvent]
  override protected lazy val format: RecordFormat[UserEventWrapper] = RecordFormat[UserEventWrapper]
  override protected lazy val schema: SchemaFor[UserEventWrapper] = SchemaFor[UserEventWrapper]
  override val kafkaTopic: String = kafkaConfiguration.topics(UserProducerActor.topicKey)

  override protected def convert(event: UserEvent): UserEventWrapper = {
    UserEventWrapper(
      version = event.version(),
      id = event.userId.value,
      date = event.eventDate,
      eventType = event.getClass.getSimpleName,
      event = UserEventWrapper.wrapEvent(event)
    )
  }
}

object UserProducerActor extends ProducerActorCompanion {
  val props: Props = Props[UserProducerActor]
  override val name: String = "kafka-user-event-writer"
  override val topicKey: String = "users"
}
