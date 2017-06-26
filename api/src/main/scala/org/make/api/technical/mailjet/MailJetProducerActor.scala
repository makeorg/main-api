package org.make.api.technical.mailjet

import java.util.Properties

import akka.actor.{Actor, ActorLogging, Props}
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord, RecordMetadata}
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.technical.AvroSerializers
import org.make.core.proposition.PropositionEvent

import scala.util.Try

class MailJetProducerActor extends Actor with KafkaConfigurationExtension with AvroSerializers with ActorLogging {

  val kafkaTopic: String =
    kafkaConfiguration.topics(MailJetProducerActor.topicKey)

  private val format: RecordFormat[MailJetEvent] = RecordFormat[MailJetEvent]

  private var producer: KafkaProducer[String, GenericRecord] = _

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[PropositionEvent])
    producer = createProducer()
  }

  private def createProducer[A, B](): KafkaProducer[A, B] = {
    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfiguration.connectionString)
    props.put(ProducerConfig.ACKS_CONFIG, "all")
    props.put(ProducerConfig.RETRIES_CONFIG, "0")
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384")
    props.put(ProducerConfig.LINGER_MS_CONFIG, "1")
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432")
    props.put("schema.registry.url", kafkaConfiguration.schemaRegistry)
    props.put("value.schema", SchemaFor[MailJetEvent].toString)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroSerializer")
    new KafkaProducer[A, B](props)
  }

  override def receive: Receive = {
    case event: MailJetEvent => onEvent(event)
    case other               => log.warning(s"Unknown event $other")
  }

  private def onEvent(event: MailJetEvent) = {
    log.debug(s"Received event $event")
    val record = format.to(event)
    producer.send(new ProducerRecord[String, GenericRecord](kafkaTopic, record), (r: RecordMetadata, e: Exception) => {
      Option(e).foreach(e => log.debug("[EXCEPTION] Producer sent: ", e))
      Option(r).foreach(r => log.debug("[RECORDMETADATA] Producer sent: {} {}", Array(r.topic(), r.checksum())))
    })
  }

  override def postStop(): Unit = {
    Try(producer.close())
  }
}

object MailJetProducerActor {
  val name: String = "mailjet-callback-event-producer"
  val props: Props = Props[MailJetProducerActor]

  val topicKey: String = "mailjet-events"
}
