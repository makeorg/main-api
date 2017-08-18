package org.make.api
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientConfig}
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory
import com.whisk.docker.impl.dockerjava.{Docker, DockerJavaExecutorFactory}
import com.whisk.docker.{DockerContainer, DockerFactory, DockerKit, DockerReadyChecker}

import scala.concurrent.duration._

trait DockerElasticsearchService extends DockerKit {

  val defaultElasticsearchHttpHost = "0.0.0.0"
  val defaultElasticsearchHttpPort = 9200
  val defaultElasticsearchClientPort = 9300
  val defaultElasticsearchPortExposed = 9700

  val defaultElasticsearchIndex = "proposals"
  val defaultElasticsearchDocType = "proposal"

  private val elasticSearchContainer = DockerContainer("docker.elastic.co/elasticsearch/elasticsearch:5.4.0")
    .withPorts(defaultElasticsearchHttpPort -> Some(defaultElasticsearchPortExposed))
    .withEnv("xpack.security.enabled=false")
    .withReadyChecker(
      DockerReadyChecker
        .HttpResponseCode(defaultElasticsearchHttpPort, "/")
        .within(100.millis)
        .looped(20, 1250.millis)
    )

  abstract override def dockerContainers: List[DockerContainer] =
    elasticSearchContainer :: super.dockerContainers

  private val dockerClientConfig: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build()

  private val client: Docker = new Docker(dockerClientConfig, new JerseyDockerCmdExecFactory())

  override implicit val dockerFactory: DockerFactory = new DockerJavaExecutorFactory(client)
}
