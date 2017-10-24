package org.make.api.sequence

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.sequence.SequenceEvent._
import org.make.api.technical.{ProducerActor, ProducerActorCompanion}
import org.make.core.DateHelper

class SequenceProducerActor extends ProducerActor {
  override protected lazy val eventClass: Class[SequenceEvent] = classOf[SequenceEvent]
  override protected lazy val format: RecordFormat[SequenceEventWrapper] = RecordFormat[SequenceEventWrapper]
  override protected lazy val schema: SchemaFor[SequenceEventWrapper] = SchemaFor[SequenceEventWrapper]

  val kafkaTopic: String =
    kafkaConfiguration.topics(SequenceProducerActor.topicKey)

  override def receive: Receive = {
    case event: SequenceCreated          => onCreate(event)
    case event: SequenceUpdated          => onUpdateSequence(event)
    case event: SequenceViewed           => onViewSequence(event)
    case event: SequenceProposalsAdded   => onProposalsAddedSequence(event)
    case event: SequenceProposalsRemoved => onProposalsRemovedSequence(event)
    case other                           => log.warning(s"Unknown event $other")
  }
  private def onViewSequence(event: SequenceViewed): Unit = {
    log.debug(s"Received event $event")
    val record = format.to(
      SequenceEventWrapper(
        version = SequenceViewed.version,
        id = event.id.value,
        date = DateHelper.now(),
        eventType = event.getClass.getSimpleName,
        event = SequenceEventWrapper.wrapEvent(event)
      )
    )
    sendRecord(kafkaTopic, event.id.value, record)
  }

  private def onUpdateSequence(event: SequenceUpdated): Unit = {
    log.debug(s"Received event $event")
    val record = format.to(
      SequenceEventWrapper(
        version = SequenceUpdated.version,
        id = event.id.value,
        date = DateHelper.now(),
        eventType = event.getClass.getSimpleName,
        event = SequenceEventWrapper.wrapEvent(event)
      )
    )
    sendRecord(kafkaTopic, event.id.value, record)
  }

  private def onCreate(event: SequenceCreated): Unit = {
    log.debug(s"Received event $event")
    val record = format.to(
      SequenceEventWrapper(
        version = SequenceCreated.version,
        id = event.id.value,
        date = DateHelper.now(),
        eventType = event.getClass.getSimpleName,
        event = SequenceEventWrapper.wrapEvent(event)
      )
    )
    sendRecord(kafkaTopic, event.id.value, record)
  }

  private def onProposalsAddedSequence(event: SequenceProposalsAdded): Unit = {
    log.debug(s"Received event $event")
    val record = format.to(
      SequenceEventWrapper(
        version = SequenceProposalsAdded.version,
        id = event.id.value,
        date = DateHelper.now(),
        eventType = event.getClass.getSimpleName,
        event = SequenceEventWrapper.wrapEvent(event)
      )
    )
    sendRecord(kafkaTopic, event.id.value, record)
  }

  private def onProposalsRemovedSequence(event: SequenceProposalsRemoved): Unit = {
    log.debug(s"Received event $event")
    val record = format.to(
      SequenceEventWrapper(
        version = SequenceProposalsRemoved.version,
        id = event.id.value,
        date = DateHelper.now(),
        eventType = event.getClass.getSimpleName,
        event = SequenceEventWrapper.wrapEvent(event)
      )
    )
    sendRecord(kafkaTopic, event.id.value, record)
  }

}

object SequenceProducerActor extends ProducerActorCompanion {
  val props: Props = Props[SequenceProducerActor]
  val name: String = "kafka-sequences-event-writer"
  val topicKey = "sequences"
}
