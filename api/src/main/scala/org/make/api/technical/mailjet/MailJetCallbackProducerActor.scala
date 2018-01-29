package org.make.api.technical.mailjet

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.BasicProducerActor

class MailJetCallbackProducerActor extends BasicProducerActor[MailJetEvent, MailJetEvent] {
  override protected lazy val eventClass: Class[MailJetEvent] = classOf[MailJetEvent]
  override protected lazy val format: RecordFormat[MailJetEvent] = RecordFormat[MailJetEvent]
  override protected lazy val schema: SchemaFor[MailJetEvent] = SchemaFor[MailJetEvent]
  override val kafkaTopic: String = kafkaConfiguration.topics(MailJetCallbackProducerActor.topicKey)
  override protected def convert(event: MailJetEvent): MailJetEvent = event
}

object MailJetCallbackProducerActor {
  val name: String = "mailjet-callback-event-producer"
  val props: Props = Props[MailJetCallbackProducerActor]
  val topicKey: String = "mailjet-events"
}
