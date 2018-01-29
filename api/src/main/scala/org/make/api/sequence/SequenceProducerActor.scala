package org.make.api.sequence

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.sequence.PublishedSequenceEvent._
import org.make.api.technical.{BasicProducerActor, ProducerActorCompanion}
import org.make.core.DateHelper

class SequenceProducerActor extends BasicProducerActor[SequenceEventWrapper, PublishedSequenceEvent] {
  override protected lazy val eventClass: Class[PublishedSequenceEvent] = classOf[PublishedSequenceEvent]
  override protected lazy val format: RecordFormat[SequenceEventWrapper] = RecordFormat[SequenceEventWrapper]
  override protected lazy val schema: SchemaFor[SequenceEventWrapper] = SchemaFor[SequenceEventWrapper]
  override val kafkaTopic: String = kafkaConfiguration.topics(SequenceProducerActor.topicKey)

  def convert(event: PublishedSequenceEvent): SequenceEventWrapper = {
    SequenceEventWrapper(
      version = event.version(),
      id = event.id.value,
      date = DateHelper.now(),
      eventType = event.getClass.getSimpleName,
      event = SequenceEventWrapper.wrapEvent(event)
    )
  }
}

object SequenceProducerActor extends ProducerActorCompanion {
  val props: Props = Props[SequenceProducerActor]
  val name: String = "kafka-sequences-event-writer"
  val topicKey = "sequences"
}
