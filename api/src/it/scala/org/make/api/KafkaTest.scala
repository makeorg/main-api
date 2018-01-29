package org.make.api

import java.util.Properties

import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig}
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}
import org.make.api.docker.DockerKafkaService
import org.make.api.technical.MakeKafkaAvroSerializer

trait KafkaTest extends ItMakeTest with DockerKafkaService {
  override protected def beforeAll(): Unit = {
    super.beforeAll()
    startAllOrFail()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    stopAllQuietly()
  }

  def createProducer[T](schema: SchemaFor[T], format: RecordFormat[T]): KafkaProducer[String, T] = {
    val registryUrl = s"http://localhost:$registryExposedPort"
    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, s"localhost:$kafkaPort")
    props.put(ProducerConfig.ACKS_CONFIG, "all")
    props.put(ProducerConfig.RETRIES_CONFIG, "3")
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384")
    props.put(ProducerConfig.LINGER_MS_CONFIG, "1")
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432")
    val serializer: Serializer[T] = new MakeKafkaAvroSerializer[T](registryUrl, schema, format)
    new KafkaProducer(props, new StringSerializer(), serializer)
  }

}
