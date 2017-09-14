package org.make.api.proposal

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.{ProducerActor, ProducerActorCompanion}
import org.make.core.DateHelper
import org.make.core.proposal.ProposalEvent
import org.make.core.proposal.ProposalEvent._

class ProposalProducerActor extends ProducerActor {
  override protected lazy val eventClass: Class[ProposalEvent] = classOf[ProposalEvent]
  override protected lazy val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]
  override protected lazy val schema: SchemaFor[ProposalEventWrapper] = SchemaFor[ProposalEventWrapper]

  val kafkaTopic: String =
    kafkaConfiguration.topics(ProposalProducerActor.topicKey)

  override def receive: Receive = {
    case event: ProposalProposed => onPropose(event)
    case event: ProposalAccepted => onProposalAccepted(event)
    case event: ProposalRefused  => onProposalRefused(event)
    case event: ProposalUpdated  => onUpdateProposal(event)
    case event: ProposalViewed   => onViewProposal(event)
    case other                   => log.warning(s"Unknown event $other")
  }

  private def onProposalAccepted(event: ProposalAccepted): Unit = {
    log.debug(s"Received event $event")
    val record = format.to(
      ProposalEventWrapper(
        version = ProposalAccepted.version,
        id = event.id.value,
        date = DateHelper.now(),
        eventType = event.getClass.getSimpleName,
        event = ProposalEventWrapper.wrapEvent(event)
      )
    )
    sendRecord(kafkaTopic, event.id.value, record)
  }

  private def onProposalRefused(event: ProposalRefused): Unit = {
    log.debug(s"Received event $event")
    val record = format.to(
      ProposalEventWrapper(
        version = ProposalRefused.version,
        id = event.id.value,
        date = DateHelper.now(),
        eventType = event.getClass.getSimpleName,
        event = ProposalEventWrapper.wrapEvent(event)
      )
    )
    sendRecord(kafkaTopic, event.id.value, record)
  }

  private def onViewProposal(event: ProposalViewed): Unit = {
    log.debug(s"Received event $event")
    val record = format.to(
      ProposalEventWrapper(
        version = ProposalViewed.version,
        id = event.id.value,
        date = DateHelper.now(),
        eventType = event.getClass.getSimpleName,
        event = ProposalEventWrapper.wrapEvent(event)
      )
    )
    sendRecord(kafkaTopic, event.id.value, record)
  }

  private def onUpdateProposal(event: ProposalUpdated): Unit = {
    log.debug(s"Received event $event")
    val record = format.to(
      ProposalEventWrapper(
        version = ProposalUpdated.version,
        id = event.id.value,
        date = DateHelper.now(),
        eventType = event.getClass.getSimpleName,
        event = ProposalEventWrapper.wrapEvent(event)
      )
    )
    sendRecord(kafkaTopic, event.id.value, record)
  }

  private def onPropose(event: ProposalProposed): Unit = {
    log.debug(s"Received event $event")
    val record = format.to(
      ProposalEventWrapper(
        version = ProposalProposed.version,
        id = event.id.value,
        date = DateHelper.now(),
        eventType = event.getClass.getSimpleName,
        event = ProposalEventWrapper.wrapEvent(event)
      )
    )
    sendRecord(kafkaTopic, event.id.value, record)
  }

}

object ProposalProducerActor extends ProducerActorCompanion {
  val props: Props = Props[ProposalProducerActor]
  val name: String = "kafka-proposals-event-writer"
  val topicKey = "proposals"
}
