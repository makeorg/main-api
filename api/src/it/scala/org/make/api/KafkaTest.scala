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

package org.make.api

import java.util.Properties

import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig}
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}
import org.make.api.docker.DockerKafkaService
import org.make.api.technical.MakeKafkaAvroSerializer

trait KafkaTest extends ItMakeTest with DockerKafkaService {
  def createProducer[T](schema: SchemaFor[T], format: RecordFormat[T]): KafkaProducer[String, T] = {
    val registryUrl = s"http://localhost:$registryExposedPort"
    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, s"localhost:$kafkaExposedPort")
    props.put(ProducerConfig.ACKS_CONFIG, "all")
    props.put(ProducerConfig.RETRIES_CONFIG, "3")
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384")
    props.put(ProducerConfig.LINGER_MS_CONFIG, "1")
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432")
    val serializer: Serializer[T] = new MakeKafkaAvroSerializer[T](registryUrl, schema, format)
    new KafkaProducer(props, new StringSerializer(), serializer)
  }

}
