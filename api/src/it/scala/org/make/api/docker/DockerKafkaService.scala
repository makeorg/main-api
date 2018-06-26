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

package org.make.api.docker

import com.whisk.docker.{ContainerLink, DockerContainer, DockerReadyChecker}

trait DockerKafkaService extends DockerZookeeperService {

  val kafkaInternalPort: Int = 9092
  private val defaultKafkaExposedPort: Int = 29092
  def kafkaExposedPort: Int = defaultKafkaExposedPort
  def kafkaName: String = "kafka"
  def brokerId: Int = 1

  val registryInternalPort: Int = 8081
  private val defaultRegistryExposedPort: Int = 28081
  def registryExposedPort: Int = defaultRegistryExposedPort
  def registryName: String = "registry"

  protected def kafkaContainer: DockerContainer =
    DockerContainer(s"confluentinc/cp-kafka:${ConfluentPlatformTest.confluentVersion}", name = Some(kafkaName))
      .withEnv(
        s"KAFKA_ZOOKEEPER_CONNECT=$zookeeperName:$zookeeperInternalPort/kafka",
        s"KAFKA_ADVERTISED_LISTENERS=EXTERNAL://127.0.0.1:$kafkaExposedPort,INTERNAL://$kafkaName:$kafkaInternalPort",
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

  protected def avroRegistryContainer: DockerContainer =
    DockerContainer(
      s"confluentinc/cp-schema-registry:${ConfluentPlatformTest.confluentVersion}",
      name = Some(registryName)
    ).withEnv(
        s"SCHEMA_REGISTRY_HOST_NAME=$registryName",
        s"SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS=PLAINTEXT://$kafkaName:$kafkaInternalPort",
        "SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL=PLAINTEXT",
        s"SCHEMA_REGISTRY_LISTENERS=http://$registryName:$registryInternalPort"
      )
      .withHostname(registryName)
      .withPorts(registryInternalPort -> Some(registryExposedPort))
      .withLinks(ContainerLink(kafkaContainer, kafkaName))
      .withReadyChecker(DockerReadyChecker.LogLineContains("Server started, listening for requests..."))

  abstract override def dockerContainers: List[DockerContainer] =
    avroRegistryContainer :: kafkaContainer :: super.dockerContainers
}
