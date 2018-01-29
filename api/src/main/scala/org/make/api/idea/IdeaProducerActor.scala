package org.make.api.idea

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.idea.IdeaEvent._
import org.make.api.technical.{BasicProducerActor, ProducerActorCompanion}

class IdeaProducerActor extends BasicProducerActor[IdeaEventWrapper, IdeaEvent] {
  override protected lazy val eventClass: Class[IdeaEvent] = classOf[IdeaEvent]
  override protected lazy val format: RecordFormat[IdeaEventWrapper] = RecordFormat[IdeaEventWrapper]
  override protected lazy val schema: SchemaFor[IdeaEventWrapper] = SchemaFor[IdeaEventWrapper]
  override val kafkaTopic: String = kafkaConfiguration.topics(IdeaProducerActor.topicKey)

  override protected def convert(event: IdeaEvent): IdeaEventWrapper = {
    IdeaEventWrapper(
      version = event.version(),
      id = event.ideaId.value,
      date = event.eventDate,
      eventType = event.getClass.getSimpleName,
      event = IdeaEventWrapper.wrapEvent(event)
    )
  }
}

object IdeaProducerActor extends ProducerActorCompanion {
  val props: Props = Props[IdeaProducerActor]
  override val name: String = "kafka-idea-event-writer"
  override val topicKey: String = "ideas"
}
