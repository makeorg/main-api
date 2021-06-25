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

import akka.actor.typed.eventstream.EventStream.Subscribe
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, LogOptions, PostStop}
import com.sksamuel.avro4s.{Encoder, SchemaFor}
import org.apache.kafka.clients.producer._
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}
import org.make.api.extensions.KafkaConfiguration
import org.make.core.WithEventId
import org.slf4j.event.Level

import scala.concurrent.duration.DurationInt
import java.util.{Properties, UUID}
import scala.reflect.ClassTag

abstract class KafkaProducerBehavior[Event: ClassTag, Wrapper: SchemaFor: Encoder] {

  protected val topicKey: String

  protected def createProducerConfiguration(kafkaConfiguration: KafkaConfiguration, name: String): Properties = {
    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfiguration.connectionString)
    props.put(ProducerConfig.ACKS_CONFIG, "all")
    props.put(ProducerConfig.RETRIES_CONFIG, "3")
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384")
    props.put(ProducerConfig.LINGER_MS_CONFIG, "1")
    props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, "org.make.api.technical.MakePartitioner")
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432")
    props.put(ProducerConfig.CLIENT_ID_CONFIG, name)
    props
  }

  protected def createProducer(kafkaConfiguration: KafkaConfiguration, name: String): KafkaProducer[String, Wrapper] = {
    val props: Properties = createProducerConfiguration(kafkaConfiguration, name)
    val valueSerializer: Serializer[Wrapper] =
      new MakeKafkaAvroSerializer[Wrapper](kafkaConfiguration.schemaRegistry)
    new KafkaProducer(props, new StringSerializer(), valueSerializer)
  }

  protected def wrapEvent(event: Event): Wrapper

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  def createBehavior(name: String): Behavior[Event] = {
    Behaviors.setup { context =>
      val kafkaConfiguration = KafkaConfiguration(context.system)
      val topic = kafkaConfiguration.topic(topicKey)
      val producer = createProducer(kafkaConfiguration, name)
      context.system.eventStream ! Subscribe(context.self)

      val sendCallBack: Callback = (r: RecordMetadata, e: Exception) => {
        val topic = Option(r).map(_.topic()).getOrElse("unknown")
        Option(e).foreach(e => context.log.error(s"Error when producing message on topic $topic", e))
      }
      context.log.info(s"Starting producer for topic $topic")
      Behaviors.logMessages[Event](
        LogOptions().withLevel(Level.DEBUG),
        Behaviors
          .receiveMessage[Event] { event =>
            if (producer.partitionsFor(topic).size > 0) {
              val record = wrapEvent(event)
              producer.send(
                new ProducerRecord[String, Wrapper](
                  topic,
                  None.orNull,
                  System.currentTimeMillis(),
                  extractKey(record).getOrElse(UUID.randomUUID().toString),
                  record
                ),
                sendCallBack
              )
              Behaviors.same
            } else {
              context.scheduleOnce(100.milliseconds, context.self, event)
              Behaviors.same
            }
          }
          .receiveSignal {
            case (_, PostStop) =>
              context.log.info(s"Stopping producer for topic $topic")
              producer.close()
              Behaviors.same
          }
      )
    }
  }

  private def extractKey(message: Wrapper): Option[String] = {
    message match {
      case event: WithEventId => event.eventId.map(_.value)
      case _                  => None
    }
  }
}
