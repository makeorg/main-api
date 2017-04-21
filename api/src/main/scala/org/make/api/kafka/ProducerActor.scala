package org.make.api.kafka

import java.time.ZonedDateTime
import java.util.Properties

import akka.actor.{Actor, Props}
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.make.core.citizen.CitizenEvent.{CitizenEvent, CitizenRegistered, EventWrapper}

import scala.util.Try

class ProducerActor extends Actor with KafkaConfigurationExtension with AvroSerializers with StrictLogging {

  private val format: RecordFormat[EventWrapper] = RecordFormat[EventWrapper]

  private var producer: KafkaProducer[String, GenericRecord] = _

  case class TestClass(id: String)

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[CitizenEvent])
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
    props.put("value.schema", SchemaFor[EventWrapper].toString)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroSerializer")
    new KafkaProducer[A, B](props)
  }

  override def receive: Receive = {

    case event: CitizenRegistered =>
      logger.debug(s"Received event $event")

      val record = format.to(
        EventWrapper(
          version = 1,
          id = event.id.value,
          date = ZonedDateTime.now(),
          eventType = event.getClass.getSimpleName,
          event = EventWrapper.wrapEvent(event)
        )
      )

      producer.send(new ProducerRecord[String, GenericRecord](kafkaConfiguration.topic, event.id.value, record))
    case other => println(s"Unknown event $other")
  }


  override def postStop(): Unit = {
    Try(producer.close())
  }
}


object ProducerActor {
  val props: Props = Props(new ProducerActor)
  val name: String = "kafka-event-writer"
}
