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
