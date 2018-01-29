package org.make.api.docker

import com.whisk.docker.{ContainerLink, DockerContainer, DockerReadyChecker}

trait DockerKafkaService extends DockerZookeeperService {

  val kafkaExposedPort: Int = 29092
  val kafkaInternalPort: Int = 9092
  protected val registryInternalPort: Int = 8081
  val registryExposedPort: Int = 28081
  val brokerId: Int = 1
  val kafkaName: String = "kafka"
  val registryName = "registry"

  protected val kafkaContainer: DockerContainer =
    DockerContainer(s"confluentinc/cp-kafka:${ConfluentPlatformTest.confluentVersion}", name = Some(kafkaName))
      .withEnv(
        s"KAFKA_ZOOKEEPER_CONNECT=zookeeper:$zookeeperInternalPort/kafka",
        s"KAFKA_ADVERTISED_LISTENERS=EXTERNAL://127.0.0.1:$kafkaExposedPort,INTERNAL://kafka:$kafkaInternalPort",
        s"KAFKA_LISTENERS=INTERNAL://0.0.0.0:$kafkaInternalPort,EXTERNAL://0.0.0.0:$kafkaExposedPort",
        "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=EXTERNAL:PLAINTEXT,INTERNAL:PLAINTEXT",
        s"KAFKA_BROKER_ID=$brokerId",
        "KAFKA_INTER_BROKER_LISTENER_NAME=INTERNAL",
        "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1"
      )
      .withHostname(kafkaName)
      .withLinks(ContainerLink(zookeeperContainer, zookeeperName))
      .withPorts(kafkaExposedPort -> Some(kafkaExposedPort))
      .withReadyChecker(DockerReadyChecker.LogLineContains("started (kafka.server.KafkaServer)"))

  protected val avroRegistryContainer: DockerContainer =
    DockerContainer(
      s"confluentinc/cp-schema-registry:${ConfluentPlatformTest.confluentVersion}",
      name = Some(registryName)
    ).withEnv(
        s"SCHEMA_REGISTRY_HOST_NAME=$registryName",
        s"SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS=PLAINTEXT://kafka:$kafkaInternalPort",
        "SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL=PLAINTEXT",
        s"SCHEMA_REGISTRY_LISTENERS=http://$registryName:$registryInternalPort"
      )
      .withHostname(registryName)
      .withPorts(registryInternalPort -> Some(registryExposedPort))
      .withLinks(ContainerLink(kafkaContainer, "kafka"))
      .withReadyChecker(DockerReadyChecker.LogLineContains("Server started, listening for requests..."))

  abstract override def dockerContainers: List[DockerContainer] =
    avroRegistryContainer :: kafkaContainer :: super.dockerContainers
}
