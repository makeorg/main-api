package org.make.api.technical

import java.util.Properties

import akka.actor.{Actor, ActorLogging, ActorSystem}
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.apache.kafka.clients.producer.{KafkaProducer, _}
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}
import org.make.api.extensions.{KafkaConfiguration, KafkaConfigurationExtension}

import scala.util.Try

abstract class ProducerActor[T] extends Actor with KafkaConfigurationExtension with AvroSerializers with ActorLogging {

  protected val eventClass: Class[_]

  val sendCallBack: Callback = (r: RecordMetadata, e: Exception) => {
    val topic = Option(r).map(_.topic()).getOrElse("unknown")
    Option(e).foreach(e => log.error(e, "Error when producing message on topic {}", topic))
  }

  protected val format: RecordFormat[T]
  protected val schema: SchemaFor[T]
  protected val producer: KafkaProducer[String, T] = createProducer()

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, eventClass)
  }

  protected def createProducer(): KafkaProducer[String, T] = {
    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfiguration.connectionString)
    props.put(ProducerConfig.ACKS_CONFIG, "all")
    props.put(ProducerConfig.RETRIES_CONFIG, "3")
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384")
    props.put(ProducerConfig.LINGER_MS_CONFIG, "1")
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432")
    val valueSerializer: Serializer[T] =
      new MakeKafkaAvroSerializer[T](kafkaConfiguration.schemaRegistry, schema, format)
    new KafkaProducer(props, new StringSerializer(), valueSerializer)
  }

  protected def sendRecord(kafkaTopic: String, eventId: String, record: T): Unit = {
    producer.send(new ProducerRecord[String, T](kafkaTopic, eventId, record), sendCallBack)
  }

  protected def sendRecord(kafkaTopic: String, record: T): Unit = {
    producer.send(new ProducerRecord[String, T](kafkaTopic, record), sendCallBack)
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
