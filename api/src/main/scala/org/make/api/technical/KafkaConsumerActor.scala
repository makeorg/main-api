package org.make.api.technical

import java.util
import java.util.Properties

import akka.actor.{Actor, ActorLogging}
import com.sksamuel.avro4s.RecordFormat
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.technical.KafkaConsumerActor.Consume

import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * TODO: This actor should not use default execution context
  */
abstract class KafkaConsumerActor[T]
    extends Actor
    with KafkaConfigurationExtension
    with AvroSerializers
    with ActorLogging {

  protected val kafkaTopic: String
  protected val format: RecordFormat[T]
  final def doNothing(event: Any): Future[Unit] = {
    Future.successful {
      log.debug(s"received $event")
    }
  }

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

  private def createConsumer(): KafkaConsumer[String, GenericRecord] = {
    val identityMapCapacity = 1000
    val props = new Properties()
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfiguration.connectionString)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    val consumer = new KafkaConsumer[String, Object](
      props,
      new StringDeserializer(),
      new KafkaAvroDeserializer(new CachedSchemaRegistryClient(kafkaConfiguration.schemaRegistry, identityMapCapacity))
    )
    consumer.subscribe(util.Arrays.asList(kafkaConfiguration.topics(kafkaTopic)))
    consumer.asInstanceOf[KafkaConsumer[String, GenericRecord]]
  }

  override def receive: Receive = {
    case Consume =>
      self ! Consume
      val records = consumer.poll(kafkaConfiguration.pollTimeout)
      val futures = records.asScala.map { record =>
        val event = format.from(record.value())
        handleMessage(event)
      }
      futures.foreach(Await.ready(_, 1.minute))
      futures.foreach(_.onComplete {
        case Success(_) =>
        case Failure(e) => log.error(e, "Error while consuming messages")
      })
      // toDo: manage failures
      consumer.commitSync()

  }
}

object KafkaConsumerActor {
  case object Consume
  case class Reset(offset: Long)
}
