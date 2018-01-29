package org.make.api.technical.mailjet

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.ProducerActor

import scala.util.Try

class MailJetProducerActor extends ProducerActor[SendEmail] {

  override protected lazy val eventClass: Class[SendEmail] = classOf[SendEmail]
  override protected lazy val format: RecordFormat[SendEmail] = RecordFormat[SendEmail]
  override protected lazy val schema: SchemaFor[SendEmail] = SchemaFor[SendEmail]

  val kafkaTopic: String =
    kafkaConfiguration.topics(MailJetProducerActor.topicKey)

  override def receive: Receive = {
    case event: SendEmail => onEvent(event)
    case other            => log.warning(s"Unknown event $other")
  }

  private def onEvent(event: SendEmail): Unit = {
    log.debug(s"Received event $event")
    sendRecord(kafkaTopic, event)
  }

  override def postStop(): Unit = {
    Try(producer.close())
  }
}

object MailJetProducerActor {
  val name: String = "mailjet-email-producer"
  val props: Props = Props[MailJetProducerActor]
  val topicKey: String = "emails"
}
