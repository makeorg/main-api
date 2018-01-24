package org.make.api.docker

import com.whisk.docker.{ContainerLink, DockerContainer, DockerReadyChecker}

trait DockerKafkaService extends DockerZookeeperService {

  val kafkaPort: Int = 29092
  protected val registryInternalPort: Int = 8081
  val registryExposedPort: Int = 28081
  val brokerId: Int = 1
  val kafkaName: String = "kafka"

  protected val kafkaContainer: DockerContainer =
    DockerContainer(s"confluentinc/cp-kafka:${ConfluentPlatformTest.confluentVersion}", name = Some(kafkaName))
      .withEnv(
        s"KAFKA_ZOOKEEPER_CONNECT=zookeeper:$zookeeperInternalPort/kafka",
        s"KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:$kafkaPort",
        s"KAFKA_LISTENERS=PLAINTEXT://localhost:$kafkaPort",
        s"KAFKA_BROKER_ID=$brokerId",
        "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1"
      )
      .withHostname(kafkaName)
      .withLinks(ContainerLink(zookeeperContainer, zookeeperName))
      .withPorts(kafkaPort -> Some(kafkaPort), registryInternalPort -> Some(registryExposedPort))
      .withReadyChecker(DockerReadyChecker.LogLineContains("started (kafka.server.KafkaServer)"))

  protected val avroRegistryContainer: DockerContainer =
    DockerContainer(s"confluentinc/cp-schema-registry:${ConfluentPlatformTest.confluentVersion}")
      .withEnv(
        s"SCHEMA_REGISTRY_HOST_NAME=$kafkaName",
        s"SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS=PLAINTEXT://localhost:$kafkaPort",
        s"SCHEMA_REGISTRY_LISTENERS=http://$kafkaName:$registryInternalPort"
      )
      .withNetworkMode(s"container:$kafkaName")
      .withUnlinkedDependencies(kafkaContainer)
      .withReadyChecker(DockerReadyChecker.LogLineContains("Server started, listening for requests..."))

  abstract override def dockerContainers: List[DockerContainer] =
    avroRegistryContainer :: kafkaContainer :: super.dockerContainers
}
