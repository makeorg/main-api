package org.make.api.idea

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.idea.IdeaEvent._
import org.make.api.technical.{ProducerActor, ProducerActorCompanion}

class IdeaProducerActor extends ProducerActor {
  override protected lazy val eventClass: Class[IdeaEvent] = classOf[IdeaEvent]
  override protected lazy val format: RecordFormat[IdeaEventWrapper] = RecordFormat[IdeaEventWrapper]
  override protected lazy val schema: SchemaFor[IdeaEventWrapper] = SchemaFor[IdeaEventWrapper]

  val kafkaTopic: String =
    kafkaConfiguration.topics(IdeaProducerActor.topicKey)

  override def receive: Receive = {
    case event: IdeaCreatedEvent  => onIdeaCreatedEvent(event)
    case event: IdeaUpdatedEvent  => onIdeaUpdatedEvent(event)
    case other                    => log.warning("Unknown event {}", other)
  }

  def onIdeaCreatedEvent(event: IdeaCreatedEvent): Unit = {
    log.debug(s"Received event $event")
    val record = format.to(
      IdeaEventWrapper(
        version = IdeaCreatedEvent.version,
        id = event.ideaId.value,
        date = event.eventDate,
        eventType = event.getClass.getSimpleName,
        event = IdeaEventWrapper.wrapEvent(event)
      )
    )
    sendRecord(kafkaTopic, event.ideaId.value, record)
  }

  def onIdeaUpdatedEvent(event: IdeaUpdatedEvent): Unit = {
    log.debug(s"Received event $event")
    val record = format.to(
      IdeaEventWrapper(
        version = IdeaUpdatedEvent.version,
        id = event.ideaId.value,
        date = event.eventDate,
        eventType = event.getClass.getSimpleName,
        event = IdeaEventWrapper.wrapEvent(event)
      )
    )
    sendRecord(kafkaTopic, event.ideaId.value, record)
  }

}


object IdeaProducerActor extends ProducerActorCompanion {
  val props: Props = Props[IdeaProducerActor]
  override val name: String = "kafka-idea-event-writer"
  override val topicKey: String = "ideas"
}
