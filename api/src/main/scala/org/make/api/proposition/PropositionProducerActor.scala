package org.make.api.proposition

import java.time.ZonedDateTime
import java.util.Properties

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord, RecordMetadata}
import org.make.api.extensions.{KafkaConfiguration, KafkaConfigurationExtension}
import org.make.api.technical.AvroSerializers
import org.make.core.proposition.PropositionEvent._

import scala.util.Try

class PropositionProducerActor extends Actor with KafkaConfigurationExtension with AvroSerializers with ActorLogging {

  val kafkaTopic: String =
    kafkaConfiguration.topics(PropositionProducerActor.topicKey)

  private val format: RecordFormat[PropositionEventWrapper] =
    RecordFormat[PropositionEventWrapper]

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
    props.put("value.schema", SchemaFor[PropositionEventWrapper].toString)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroSerializer")
    new KafkaProducer[A, B](props)
  }

  override def receive: Receive = {
    case event: PropositionProposed => onPropose(event)
    case event: PropositionUpdated => onUpdateProposition(event)
    case event: PropositionViewed => onViewProposition(event)
    case other => log.warning(s"Unknown event $other")
  }

  private def onViewProposition(event: PropositionViewed) = {
    log.debug(s"Received event $event")
    val record = format.to(
      PropositionEventWrapper(
        version = 1,
        id = event.id.value,
        date = ZonedDateTime.now(),
        eventType = event.getClass.getSimpleName,
        event = PropositionEventWrapper.wrapEvent(event)
      )
    )
    producer.send(
      new ProducerRecord[String, GenericRecord](kafkaTopic, event.id.value, record),
      (r: RecordMetadata, e: Exception) => {
        Option(e).foreach(e => log.debug("[EXCEPTION] Producer sent: ", e))
        Option(r).foreach(r => log.debug("[RECORDMETADATA] Producer sent: {} {}", Array(r.topic(), r.checksum())))
      }
    )
  }

  private def onUpdateProposition(event: PropositionUpdated) = {
    log.debug(s"Received event $event")
    val record = format.to(
      PropositionEventWrapper(
        version = 1,
        id = event.id.value,
        date = ZonedDateTime.now(),
        eventType = event.getClass.getSimpleName,
        event = PropositionEventWrapper.wrapEvent(event)
      )
    )
    producer.send(
      new ProducerRecord[String, GenericRecord](kafkaTopic, event.id.value, record),
      (r: RecordMetadata, e: Exception) => {
        Option(e).foreach(e => log.debug("[EXCEPTION] Producer sent: ", e))
        Option(r).foreach(r => log.debug("[RECORDMETADATA] Producer sent: {} {}", Array(r.topic(), r.checksum())))
      }
    )
  }

  private def onPropose(event: PropositionProposed) = {
    log.debug(s"Received event $event")
    val record = format.to(
      PropositionEventWrapper(
        version = 1,
        id = event.id.value,
        date = ZonedDateTime.now(),
        eventType = event.getClass.getSimpleName,
        event = PropositionEventWrapper.wrapEvent(event)
      )
    )
    producer.send(
      new ProducerRecord[String, GenericRecord](kafkaTopic, event.id.value, record),
      (r: RecordMetadata, e: Exception) => {
        Option(e).foreach(e => log.debug("[EXCEPTION] Producer sent: ", e))
        Option(r).foreach(r => log.debug("[RECORDMETADATA] Producer sent: {} {}", Array(r.topic(), r.checksum())))
      }
    )
  }

  override def postStop(): Unit = {
    Try(producer.close())
  }
}

object PropositionProducerActor {
  val props: Props = Props(new PropositionProducerActor)
  val name: String = "kafka-propositions-event-writer"
  val topicKey = "propositions"
  def kafkaTopic(actorSystem: ActorSystem): String =
    KafkaConfiguration(actorSystem).topics(topicKey)
}
