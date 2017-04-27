package org.make.api.kafka

import java.util
import java.util.Properties

import akka.actor.{Actor, Props}
import com.sksamuel.avro4s.RecordFormat
import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.make.api.kafka.ConsumerActor.Consume
import org.make.core.EventWrapper

import scala.util.Try

/**
  * TODO: This actor should not use default execution context
  */
class ConsumerActor[T <: EventWrapper](private val format: RecordFormat[T]) extends Actor with KafkaConfigurationExtension with AvroSerializers with StrictLogging {

  private var consumer: KafkaConsumer[String, GenericRecord] = _
//  private val format: RecordFormat[EventWrapper] = RecordFormat[EventWrapper]

  override def preStart(): Unit = {
    consumer = createConsumer()
  }


  override def postStop(): Unit = {
    Try(consumer.close())
  }

  private def createConsumer[A, B](): KafkaConsumer[A, B] = {
    val props = new Properties()
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfiguration.connectionString)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "read-model-update")
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    props.put("schema.registry.url", kafkaConfiguration.schemaRegistry)
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroDeserializer")
    val consumer = new KafkaConsumer[A, B](props)
    consumer.subscribe(util.Arrays.asList(kafkaConfiguration.topic))
    consumer
  }

  override def receive: Receive = {
    case Consume =>
      self ! Consume
      val records = consumer.poll(kafkaConfiguration.pollTimeout)
      records.forEach { record =>
        val event = format.from(record.value())
        logger.info(s"Got event: $event")
      }
      // TODO: handle record
      consumer.commitSync()

  }

}

object ConsumerActor {

  def props(format: RecordFormat[EventWrapper]): Props = Props(new ConsumerActor(format))
  val name: String = "read-model-consumer"

  case object Consume
  case class Reset(offset: Long)

}


