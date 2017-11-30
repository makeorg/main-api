package org.make.api.proposal

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.{ProducerActor, ProducerActorCompanion}
import org.make.core.DateHelper
import org.make.api.proposal.PublishedProposalEvent._

class ProposalProducerActor extends ProducerActor {
  override protected lazy val eventClass: Class[ProposalEvent] = classOf[ProposalEvent]
  override protected lazy val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]
  override protected lazy val schema: SchemaFor[ProposalEventWrapper] = SchemaFor[ProposalEventWrapper]

  val kafkaTopic: String =
    kafkaConfiguration.topics(ProposalProducerActor.topicKey)

  override def receive: Receive = {
    case event: ProposalProposed      => onEventProposal(event, ProposalProposed.version)
    case event: ProposalAccepted      => onEventProposal(event, ProposalAccepted.version)
    case event: ProposalRefused       => onEventProposal(event, ProposalRefused.version)
    case event: ProposalUpdated       => onEventProposal(event, ProposalUpdated.version)
    case event: ProposalViewed        => onEventProposal(event, ProposalViewed.version)
    case event: ProposalVoted         => onEventProposal(event, ProposalVoted.version)
    case event: ProposalUnvoted       => onEventProposal(event, ProposalUnvoted.version)
    case event: ProposalQualified     => onEventProposal(event, ProposalQualified.version)
    case event: ProposalUnqualified   => onEventProposal(event, ProposalUnqualified.version)
    case event: SimilarProposalsAdded => onEventProposal(event, SimilarProposalsAdded.version)
    case event: ProposalLocked        => onEventProposal(event, ProposalLocked.version)
    case event: ProposalPatched       => onEventProposal(event, ProposalPatched.version)
    case event: ProposalPostponed     => onEventProposal(event, ProposalPostponed.version)
    case other                        => log.warning(s"Unknown event $other")
  }

  private def onEventProposal(event: PublishedProposalEvent, version: Int): Unit = {
    log.debug(s"Received event $event")
    val record = format.to(
      ProposalEventWrapper(
        version = version,
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
