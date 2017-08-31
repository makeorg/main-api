package org.make.api.technical

import java.util
import java.util.Properties

import akka.actor.{Actor, ActorLogging}
import com.sksamuel.avro4s.RecordFormat
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.technical.KafkaConsumerActor.Consume
import org.make.core.EventWrapper

import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

/**
  * TODO: This actor should not use default execution context
  */
abstract class KafkaConsumerActor[T <: EventWrapper](private val kafkaTopic: String)
    extends Actor
    with KafkaConfigurationExtension
    with AvroSerializers
    with ActorLogging {

  protected val format: RecordFormat[T]

  def handleMessage(message: T): Future[Unit]
  def groupId: String

  private var consumer: KafkaConsumer[String, GenericRecord] = _

  override def preStart(): Unit = {
    consumer = createConsumer()
    self ! Consume
  }

  override def postStop(): Unit = {
    Try(consumer.close())
  }

  private def createConsumer[A, B](): KafkaConsumer[A, B] = {
    val props = new Properties()
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfiguration.connectionString)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    props.put("schema.registry.url", kafkaConfiguration.schemaRegistry)
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroDeserializer")
    val consumer = new KafkaConsumer[A, B](props)
    consumer.subscribe(util.Arrays.asList(kafkaConfiguration.topics(kafkaTopic)))
    consumer
  }

  override def receive: Receive = {
    case Consume =>
      self ! Consume
      val records = consumer.poll(kafkaConfiguration.pollTimeout)
      val futures = records.asScala.map { record =>
        val eventWrapper = format.from(record.value())
        log.info(s"handling message $eventWrapper")
        handleMessage(eventWrapper)
      }
      futures.foreach(Await.ready(_, 1.minute))
      // toDo: manage failures
      consumer.commitSync()

  }
}

object KafkaConsumerActor {
  case object Consume
  case class Reset(offset: Long)
}
