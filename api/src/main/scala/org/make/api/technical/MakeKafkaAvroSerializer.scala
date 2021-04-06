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

import com.sksamuel.avro4s.{DefaultFieldMapper, Encoder, FieldMapper, SchemaFor}
import grizzled.slf4j.Logging
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.kafka.common.serialization.Serializer

import java.util
import scala.jdk.CollectionConverters._

class MakeKafkaAvroSerializer[T: Encoder: SchemaFor](registryUrl: String, fieldMapper: FieldMapper = DefaultFieldMapper)
    extends Serializer[T]
    with Logging {

  private val schema: SchemaFor[T] = SchemaFor[T]
  private val encoder: Encoder[T] = Encoder[T]

  private val identityMapCapacity = 1000
  private val delegate: Serializer[Object] = new KafkaAvroSerializer(
    new CachedSchemaRegistryClient(registryUrl, identityMapCapacity),
    Map("value.schema" -> schema.schema(DefaultFieldMapper).toString, "schema.registry.url" -> registryUrl).asJava
  )

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {
    delegate.configure(configs, isKey)
  }

  override def serialize(topic: String, data: T): Array[Byte] = {

    delegate.serialize(topic, encoder.encode(data, schema.schema(fieldMapper), fieldMapper))
  }

  override def close(): Unit = {
    delegate.close()
  }
}
