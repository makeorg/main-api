package org.make.api.technical

import java.util.Properties

import akka.actor.{Actor, ActorLogging, ActorSystem}
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer._
import org.make.api.extensions.{KafkaConfiguration, KafkaConfigurationExtension}

import scala.util.Try

abstract class ProducerActor extends Actor with KafkaConfigurationExtension with AvroSerializers with ActorLogging {

  protected val eventClass: Class[_]

  val sendCallBack: Callback = (r: RecordMetadata, e: Exception) => {
    Option(e).foreach(e => log.debug("[EXCEPTION] Producer sent: ", e))
    Option(r).foreach(r => log.debug("[RECORDMETADATA] Producer sent: {} {}", Array(r.topic(), r.offset())))
  }

  protected val format: RecordFormat[_]
  protected val schema: SchemaFor[_]
  protected val producer: KafkaProducer[String, GenericRecord] = createProducer()

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, eventClass)
  }

  protected def createProducer[A, B](): KafkaProducer[A, B] = {
    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfiguration.connectionString)
    props.put(ProducerConfig.ACKS_CONFIG, "all")
    props.put(ProducerConfig.RETRIES_CONFIG, "0")
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384")
    props.put(ProducerConfig.LINGER_MS_CONFIG, "1")
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432")
    props.put("schema.registry.url", kafkaConfiguration.schemaRegistry)
    props.put("value.schema", schema.toString)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroSerializer")
    new KafkaProducer[A, B](props)
  }

  protected def sendRecord(kafkaTopic: String, eventId: String, record: GenericRecord): Unit = {
    producer.send(new ProducerRecord[String, GenericRecord](kafkaTopic, eventId, record), sendCallBack)
  }

  protected def sendRecord(kafkaTopic: String, record: GenericRecord): Unit = {
    producer.send(new ProducerRecord[String, GenericRecord](kafkaTopic, record), sendCallBack)
  }

  override def postStop(): Unit = {
    Try(producer.close())
  }
}

trait ProducerActorCompanion {
  val name: String
  val topicKey: String
  def kafkaTopic(actorSystem: ActorSystem): String =
    KafkaConfiguration(actorSystem).topics(topicKey)
}
