package org.make.api.sequence

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.sequence.PublishedSequenceEvent._
import org.make.api.technical.{ProducerActor, ProducerActorCompanion}
import org.make.core.DateHelper

class SequenceProducerActor extends ProducerActor[SequenceEventWrapper] {
  override protected lazy val eventClass: Class[SequenceEvent] = classOf[SequenceEvent]
  override protected lazy val format: RecordFormat[SequenceEventWrapper] = RecordFormat[SequenceEventWrapper]
  override protected lazy val schema: SchemaFor[SequenceEventWrapper] = SchemaFor[SequenceEventWrapper]

  val kafkaTopic: String =
    kafkaConfiguration.topics(SequenceProducerActor.topicKey)

  override def receive: Receive = {
    case event: SequenceCreated          => onEventSequence(event, SequenceCreated.version)
    case event: SequenceUpdated          => onEventSequence(event, SequenceUpdated.version)
    case event: SequenceViewed           => onEventSequence(event, SequenceViewed.version)
    case event: SequenceProposalsAdded   => onEventSequence(event, SequenceProposalsAdded.version)
    case event: SequenceProposalsRemoved => onEventSequence(event, SequenceProposalsRemoved.version)
    case event: SequencePatched          => onEventSequence(event, SequencePatched.version)
    case other                           => log.warning(s"Unknown event $other")
  }

  private def onEventSequence(event: PublishedSequenceEvent, version: Int): Unit = {
    log.debug(s"Received event $event")
    val record =
      SequenceEventWrapper(
        version = version,
        id = event.id.value,
        date = DateHelper.now(),
        eventType = event.getClass.getSimpleName,
        event = SequenceEventWrapper.wrapEvent(event)
      )
    sendRecord(kafkaTopic, event.id.value, record)
  }
}

object SequenceProducerActor extends ProducerActorCompanion {
  val props: Props = Props[SequenceProducerActor]
  val name: String = "kafka-sequences-event-writer"
  val topicKey = "sequences"
}
