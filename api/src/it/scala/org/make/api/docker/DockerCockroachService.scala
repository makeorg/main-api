package org.make.api.docker

import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientConfig}
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory
import com.whisk.docker.impl.dockerjava.{Docker, DockerJavaExecutorFactory}
import com.whisk.docker.{DockerContainer, DockerFactory, DockerKit, DockerReadyChecker}

trait DockerCockroachService extends DockerKit {

  private val defaultCockroachPort = 26257
  protected def cockroachExposedPort: Int

  private def cockroachContainer: DockerContainer =
    DockerContainer(image = "cockroachdb/cockroach:v2.0.2", name = Some(getClass.getSimpleName))
      .withPorts(defaultCockroachPort -> Some(cockroachExposedPort))
      .withReadyChecker(DockerReadyChecker.LogLineContains("CockroachDB node starting at"))
      .withCommand("start", "--insecure")

  abstract override def dockerContainers: List[DockerContainer] =
    cockroachContainer :: super.dockerContainers

  private val dockerClientConfig: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build()

  private val client: Docker = new Docker(dockerClientConfig, new JerseyDockerCmdExecFactory())
  override implicit val dockerFactory: DockerFactory = new DockerJavaExecutorFactory(client)
}
