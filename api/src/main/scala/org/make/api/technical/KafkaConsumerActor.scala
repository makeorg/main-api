/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.technical

import java.util
import java.util.Properties

import akka.actor.{Actor, ActorLogging}
import com.sksamuel.avro4s.RecordFormat
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.technical.KafkaConsumerActor.{CheckState, Consume, Ready, Waiting}
import org.make.core.AvroSerializers

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

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
  def customProperties: Properties = new Properties()

  private var consumer: KafkaConsumer[String, T] = _

  override def preStart(): Unit = {
    consumer = createConsumer()
    self ! Consume
  }

  override def postStop(): Unit = {
    Try(consumer.close())
  }

  private def createConsumer(): KafkaConsumer[String, T] = {
    val props = new Properties()
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfiguration.connectionString)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    props.putAll(customProperties)
    val consumer = new KafkaConsumer[String, T](
      props,
      new StringDeserializer(),
      new MakeKafkaAvroDeserializer(kafkaConfiguration.schemaRegistry, format)
    )
    consumer.subscribe(util.Arrays.asList(kafkaConfiguration.topics(kafkaTopic)))
    consumer
  }

  override def receive: Receive = {
    case CheckState =>
      if (consumer.assignment().size() > 0) {
        sender() ! Ready
      } else {
        sender() ! Waiting
      }
    case Consume =>
      self ! Consume
      val records = consumer.poll(kafkaConfiguration.pollTimeout)
      records.asScala.foreach { record =>
        val future = handleMessage(record.value())
        Await.ready(future, 1.minute)
        future.onComplete {
          case Success(_) =>
          case Failure(e) => log.error(e, "Error while consuming messages")
        }
      }
      // toDo: manage failures
      consumer.commitSync()

  }
}

object KafkaConsumerActor {
  case object Consume
  case object CheckState
  case class Reset(offset: Long)

  case object Ready
  case object Waiting
}
