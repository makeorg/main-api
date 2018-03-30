package org.make.api.technical.mailjet

import java.time.{Instant, ZoneOffset, ZonedDateTime}

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import com.typesafe.scalalogging.StrictLogging
import org.make.api.technical.BasicProducerActor
import org.make.core.{DateHelper, MakeSerializable}

class MailJetCallbackProducerActor extends BasicProducerActor[MailJetEventWrapper, MailJetEvent] with StrictLogging {
  override protected lazy val eventClass: Class[MailJetEvent] = classOf[MailJetEvent]
  override protected lazy val format: RecordFormat[MailJetEventWrapper] = RecordFormat[MailJetEventWrapper]
  override protected lazy val schema: SchemaFor[MailJetEventWrapper] = SchemaFor[MailJetEventWrapper]
  override val kafkaTopic: String = kafkaConfiguration.topics(MailJetCallbackProducerActor.topicKey)
  override protected def convert(event: MailJetEvent): MailJetEventWrapper = {
    MailJetEventWrapper(version = MakeSerializable.V1, id = event.email, date = event.time.map { timestamp =>
      ZonedDateTime.from(Instant.ofEpochMilli(timestamp * 1000).atZone(ZoneOffset.UTC))
    }.getOrElse(DateHelper.now()), event = MailJetEventWrapper.wrapEvent(event))
  }
}

object MailJetCallbackProducerActor {
  val name: String = "mailjet-callback-event-producer"
  val props: Props = Props[MailJetCallbackProducerActor]
  val topicKey: String = "mailjet-events"
}
