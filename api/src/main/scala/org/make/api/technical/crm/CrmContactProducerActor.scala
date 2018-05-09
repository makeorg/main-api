package org.make.api.technical.crm

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.{BasicProducerActor, ProducerActorCompanion}
import org.make.core.DateHelper
import org.make.api.technical.crm.PublishedCrmContactEvent._

class CrmContactProducerActor extends BasicProducerActor[CrmContactEventWrapper, PublishedCrmContactEvent] {
  override protected lazy val eventClass: Class[PublishedCrmContactEvent] = classOf[PublishedCrmContactEvent]
  override protected lazy val format: RecordFormat[CrmContactEventWrapper] =
    RecordFormat[CrmContactEventWrapper]
  override protected lazy val schema: SchemaFor[CrmContactEventWrapper] = SchemaFor[CrmContactEventWrapper]
  override val kafkaTopic: String = kafkaConfiguration.topics(CrmContactProducerActor.topicKey)

  override protected def convert(event: PublishedCrmContactEvent): CrmContactEventWrapper = {
    CrmContactEventWrapper(
      version = event.version(),
      id = event.id.value,
      date = DateHelper.now(),
      eventType = event.getClass.getSimpleName,
      event = CrmContactEventWrapper.wrapEvent(event)
    )
  }
}

object CrmContactProducerActor extends ProducerActorCompanion {
  val props: Props = Props[CrmContactProducerActor]
  val name: String = "kafka-crm-contact-event-writer"
  val topicKey = "crm-contact"
}
