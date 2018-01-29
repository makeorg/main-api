package org.make.api.technical.mailjet

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.ProducerActor

import scala.util.Try

class MailJetCallbackProducerActor extends ProducerActor[MailJetEvent] {

  override protected lazy val eventClass: Class[MailJetEvent] = classOf[MailJetEvent]
  override protected lazy val format: RecordFormat[MailJetEvent] = RecordFormat[MailJetEvent]
  override protected lazy val schema: SchemaFor[MailJetEvent] = SchemaFor[MailJetEvent]

  val kafkaTopic: String =
    kafkaConfiguration.topics(MailJetCallbackProducerActor.topicKey)

  override def receive: Receive = {
    case event: MailJetEvent => onEvent(event)
    case other               => log.warning(s"Unknown event $other")
  }

  private def onEvent(event: MailJetEvent): Unit = {
    log.debug(s"Received event $event")
    sendRecord(kafkaTopic, event)
  }

  override def postStop(): Unit = {
    Try(producer.close())
  }
}

object MailJetCallbackProducerActor {
  val name: String = "mailjet-callback-event-producer"
  val props: Props = Props[MailJetCallbackProducerActor]

  val topicKey: String = "mailjet-events"
}
