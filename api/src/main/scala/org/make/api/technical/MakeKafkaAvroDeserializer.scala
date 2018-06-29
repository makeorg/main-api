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

import com.sksamuel.avro4s.RecordFormat
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Deserializer

class MakeKafkaAvroDeserializer[T](registryUrl: String, format: RecordFormat[T]) extends Deserializer[T] {
  val identityMapCapacity = 1000
  val delegate = new KafkaAvroDeserializer(new CachedSchemaRegistryClient(registryUrl, identityMapCapacity))

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {
    delegate.configure(configs, isKey)
  }

  override def close(): Unit = {
    delegate.close()
  }

  override def deserialize(topic: String, data: Array[Byte]): T = {
    format.from(delegate.deserialize(topic, data).asInstanceOf[GenericRecord])
  }
}
