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

import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import com.typesafe.scalalogging.StrictLogging
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.kafka.common.serialization.Serializer

import scala.collection.JavaConverters._

class MakeKafkaAvroSerializer[T](registryUrl: String, schema: SchemaFor[T], format: RecordFormat[T])
    extends Serializer[T]
    with StrictLogging {

  private val identityMapCapacity = 1000
  private val delegate: Serializer[Object] = new KafkaAvroSerializer(
    new CachedSchemaRegistryClient(registryUrl, identityMapCapacity),
    Map("value.schema" -> schema.toString, "schema.registry.url" -> registryUrl).asJava
  )

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {
    delegate.configure(configs, isKey)
  }

  override def serialize(topic: String, data: T): Array[Byte] = {
    delegate.serialize(topic, format.to(data))
  }

  override def close(): Unit = {
    delegate.close()
  }
}
