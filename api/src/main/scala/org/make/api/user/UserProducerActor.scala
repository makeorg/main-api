package org.make.api.user

import java.time.ZonedDateTime
import java.util.Properties

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.make.api.extensions.{KafkaConfiguration, KafkaConfigurationExtension}
import org.make.api.technical.AvroSerializers
import org.make.core.user.UserEvent
import org.make.core.user.UserEvent.{UserEventWrapper, UserRegistered}

import scala.util.Try

class UserProducerActor extends Actor with KafkaConfigurationExtension with AvroSerializers with ActorLogging {

  val kafkaTopic: String =
    kafkaConfiguration.topics(UserProducerActor.topicKey)

  private val format: RecordFormat[UserEventWrapper] =
    RecordFormat[UserEventWrapper]

  private var producer: KafkaProducer[String, GenericRecord] = _

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[UserEvent])
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
    props.put("value.schema", SchemaFor[UserEventWrapper].toString)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroSerializer")
    new KafkaProducer[A, B](props)
  }

  override def receive: Receive = {

    case event: UserRegistered =>
      log.debug(s"Received event $event")

      val record = format.to(
        UserEventWrapper(
          version = 1,
          id = event.id.value,
          date = ZonedDateTime.now(),
          eventType = event.getClass.getSimpleName,
          event = UserEventWrapper.wrapEvent(event)
        )
      )

      producer.send(new ProducerRecord[String, GenericRecord](kafkaTopic, event.id.value, record))
    case other => log.info(s"Unknown event $other")
  }

  override def postStop(): Unit = {
    Try(producer.close())
  }
}

object UserProducerActor {
  val props: Props = Props(new UserProducerActor)
  val name: String = "kafka-users-event-writer"
  val topicKey = "users"
  def kafkaTopic(actorSystem: ActorSystem): String =
    KafkaConfiguration(actorSystem).topics(topicKey)
}
