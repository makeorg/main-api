package org.make.api.docker

import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientConfig}
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory
import com.whisk.docker.impl.dockerjava.{Docker, DockerJavaExecutorFactory}
import com.whisk.docker.{DockerContainer, DockerFactory, DockerKit, DockerReadyChecker}

trait DockerCockroachService extends DockerKit {

  val defaultCockroachPort = 26257
  // toDo: use random port to avoid collisions with parallel execution test
  val defaultCockroachPortExposed = 36257

  private val cockroachContainer = DockerContainer("cockroachdb/cockroach:v1.1.0")
    .withPorts(defaultCockroachPort -> Some(defaultCockroachPortExposed))
    .withReadyChecker(DockerReadyChecker.LogLineContains("CockroachDB node starting at"))
    .withCommand("start", "--insecure")

  abstract override def dockerContainers: List[DockerContainer] =
    cockroachContainer :: super.dockerContainers

  private val dockerClientConfig: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build()

  private val client: Docker = new Docker(dockerClientConfig, new JerseyDockerCmdExecFactory())
  override implicit val dockerFactory: DockerFactory = new DockerJavaExecutorFactory(client)
}
