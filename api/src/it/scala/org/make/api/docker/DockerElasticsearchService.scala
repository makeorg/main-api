package org.make.api.docker

import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientConfig}
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory
import com.whisk.docker.impl.dockerjava.{Docker, DockerJavaExecutorFactory}
import com.whisk.docker.{DockerContainer, DockerFactory, DockerKit, DockerReadyChecker}

import scala.concurrent.duration.DurationInt

trait DockerElasticsearchService extends DockerKit {

  val defaultElasticsearchHttpPort = 9200
  val defaultElasticsearchClientPort = 9300
  val defaultElasticsearchPortExposed = 9998

  val defaultElasticsearchIndex = "proposals"
  val defaultElasticsearchDocType = "proposal"

  private val elasticSearchContainer = DockerContainer("docker.elastic.co/elasticsearch/elasticsearch:5.6.3")
    .withPorts(defaultElasticsearchHttpPort -> Some(defaultElasticsearchPortExposed))
    .withEnv(
      "xpack.security.enabled=false",
      "transport.host=localhost",
      "ES_JVM_OPTIONS=-Xmx256M -Xms256M",
      "ES_JAVA_OPTS=-Xmx256M -Xms256M"
    )
    .withReadyChecker(
      DockerReadyChecker
        .HttpResponseCode(defaultElasticsearchHttpPort, "/")
        .within(100.millis)
        .looped(300, 1.second)
    )

  abstract override def dockerContainers: List[DockerContainer] =
    elasticSearchContainer :: super.dockerContainers

  private val dockerClientConfig: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build()

  private val client: Docker = new Docker(dockerClientConfig, new JerseyDockerCmdExecFactory())

  override implicit val dockerFactory: DockerFactory = new DockerJavaExecutorFactory(client)
}
