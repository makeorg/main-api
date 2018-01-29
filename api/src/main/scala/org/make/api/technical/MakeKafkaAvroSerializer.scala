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
