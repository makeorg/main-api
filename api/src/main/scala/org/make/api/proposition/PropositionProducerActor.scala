package org.make.api.proposition

import java.time.ZonedDateTime

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.{ProducerActor, ProducerActorCompanion}
import org.make.core.proposition.PropositionEvent
import org.make.core.proposition.PropositionEvent._

class PropositionProducerActor extends ProducerActor {
  override protected lazy val eventClass: Class[PropositionEvent] = classOf[PropositionEvent]
  override protected lazy val format: RecordFormat[PropositionEventWrapper] = RecordFormat[PropositionEventWrapper]
  override protected lazy val schema: SchemaFor[PropositionEventWrapper] = SchemaFor[PropositionEventWrapper]

  val kafkaTopic: String =
    kafkaConfiguration.topics(PropositionProducerActor.topicKey)

  override def receive: Receive = {
    case event: PropositionProposed => onPropose(event)
    case event: PropositionUpdated  => onUpdateProposition(event)
    case event: PropositionViewed   => onViewProposition(event)
    case other                      => log.warning(s"Unknown event $other")
  }

  private def onViewProposition(event: PropositionViewed) = {
    log.debug(s"Received event $event")
    val record = format.to(
      PropositionEventWrapper(
        version = 1,
        id = event.id.value,
        date = ZonedDateTime.now(),
        eventType = event.getClass.getSimpleName,
        event = PropositionEventWrapper.wrapEvent(event)
      )
    )
    sendRecord(kafkaTopic, event.id.value, record)
  }

  private def onUpdateProposition(event: PropositionUpdated) = {
    log.debug(s"Received event $event")
    val record = format.to(
      PropositionEventWrapper(
        version = 1,
        id = event.id.value,
        date = ZonedDateTime.now(),
        eventType = event.getClass.getSimpleName,
        event = PropositionEventWrapper.wrapEvent(event)
      )
    )
    sendRecord(kafkaTopic, event.id.value, record)
  }

  private def onPropose(event: PropositionProposed) = {
    log.debug(s"Received event $event")
    val record = format.to(
      PropositionEventWrapper(
        version = 1,
        id = event.id.value,
        date = ZonedDateTime.now(),
        eventType = event.getClass.getSimpleName,
        event = PropositionEventWrapper.wrapEvent(event)
      )
    )
    sendRecord(kafkaTopic, event.id.value, record)
  }

}

object PropositionProducerActor extends ProducerActorCompanion {
  val props: Props = Props[PropositionProducerActor]
  val name: String = "kafka-propositions-event-writer"
  val topicKey = "propositions"
}
