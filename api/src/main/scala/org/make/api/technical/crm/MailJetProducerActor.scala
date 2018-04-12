package org.make.api.technical.crm

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.BasicProducerActor

class MailJetProducerActor extends BasicProducerActor[SendEmail, SendEmail] {
  override protected lazy val eventClass: Class[SendEmail] = classOf[SendEmail]
  override protected lazy val format: RecordFormat[SendEmail] = RecordFormat[SendEmail]
  override protected lazy val schema: SchemaFor[SendEmail] = SchemaFor[SendEmail]
  override val kafkaTopic: String = kafkaConfiguration.topics(MailJetProducerActor.topicKey)
  override protected def convert(event: SendEmail): SendEmail = event
}

object MailJetProducerActor {
  val name: String = "mailjet-email-producer"
  val props: Props = Props[MailJetProducerActor]
  val topicKey: String = "emails"
}
