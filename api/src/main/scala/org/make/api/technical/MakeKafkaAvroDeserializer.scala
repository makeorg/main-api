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

import com.sksamuel.avro4s.{Decoder, DefaultFieldMapper, FieldMapper, SchemaFor}
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Deserializer

import java.util

class MakeKafkaAvroDeserializer[T: Decoder: SchemaFor](
  registryUrl: String,
  fieldMapper: FieldMapper = DefaultFieldMapper
) extends Deserializer[T] {

  val schema: Schema = SchemaFor[T].schema(fieldMapper)
  val decoder: Decoder[T] = Decoder[T]

  private val identityMapCapacity = 1000
  private val delegate = new KafkaAvroDeserializer(new CachedSchemaRegistryClient(registryUrl, identityMapCapacity))

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {
    delegate.configure(configs, isKey)
  }

  override def close(): Unit = {
    delegate.close()
  }

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  override def deserialize(topic: String, data: Array[Byte]): T = {
    decoder.decode(delegate.deserialize(topic, data).asInstanceOf[GenericRecord], schema, fieldMapper)
  }
}
