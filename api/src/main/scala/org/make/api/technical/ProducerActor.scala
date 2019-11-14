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

import java.util.Properties

import akka.actor.{Actor, ActorLogging, ActorSystem}
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.apache.kafka.clients.producer.{KafkaProducer, _}
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}
import org.make.api.extensions.{KafkaConfiguration, KafkaConfigurationExtension}
import org.make.core.AvroSerializers

import scala.util.Try

abstract class ProducerActor[Wrapper, Event]
    extends Actor
    with KafkaConfigurationExtension
    with AvroSerializers
    with ActorLogging {

  protected val eventClass: Class[Event]

  val sendCallBack: Callback = (r: RecordMetadata, e: Exception) => {
    val topic = Option(r).map(_.topic()).getOrElse("unknown")
    Option(e).foreach(e => log.error(e, "Error when producing message on topic {}", topic))
  }

  protected val format: RecordFormat[Wrapper]
  protected val schema: SchemaFor[Wrapper]
  protected val producer: KafkaProducer[String, Wrapper] = createProducer()

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, eventClass)
  }

  protected def createProducer(): KafkaProducer[String, Wrapper] = {
    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfiguration.connectionString)
    props.put(ProducerConfig.ACKS_CONFIG, "all")
    props.put(ProducerConfig.RETRIES_CONFIG, "3")
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384")
    props.put(ProducerConfig.LINGER_MS_CONFIG, "1")
    props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, "org.make.api.technical.MakePartitioner")
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432")
    val valueSerializer: Serializer[Wrapper] =
      new MakeKafkaAvroSerializer[Wrapper](kafkaConfiguration.schemaRegistry, schema, format)
    new KafkaProducer(props, new StringSerializer(), valueSerializer)
  }

  protected def sendRecord(kafkaTopic: String, record: Wrapper): Unit = {
    producer.send(
      new ProducerRecord[String, Wrapper](kafkaTopic, None.orNull, System.currentTimeMillis(), None.orNull, record),
      sendCallBack
    )
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
