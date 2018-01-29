package org.make.api.proposal

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.technical.{BasicProducerActor, ProducerActorCompanion}
import org.make.core.DateHelper

class ProposalProducerActor extends BasicProducerActor[ProposalEventWrapper, PublishedProposalEvent] {
  override protected lazy val eventClass: Class[PublishedProposalEvent] = classOf[PublishedProposalEvent]
  override protected lazy val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]
  override protected lazy val schema: SchemaFor[ProposalEventWrapper] = SchemaFor[ProposalEventWrapper]
  override val kafkaTopic: String = kafkaConfiguration.topics(ProposalProducerActor.topicKey)

  override protected def convert(event: PublishedProposalEvent): ProposalEventWrapper = {
    ProposalEventWrapper(
      version = event.version(),
      id = event.id.value,
      date = DateHelper.now(),
      eventType = event.getClass.getSimpleName,
      event = ProposalEventWrapper.wrapEvent(event)
    )
  }
}

object ProposalProducerActor extends ProducerActorCompanion {
  val props: Props = Props[ProposalProducerActor]
  val name: String = "kafka-proposals-event-writer"
  val topicKey = "proposals"
}
