package org.make.api.docker

import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientConfig}
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory
import com.whisk.docker.impl.dockerjava.{Docker, DockerJavaExecutorFactory}
import com.whisk.docker.{DockerContainer, DockerFactory, DockerKit, DockerReadyChecker}

trait DockerZookeeperService extends DockerKit {
  private val dockerClientConfig: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
  private val client: Docker = new Docker(dockerClientConfig, new JerseyDockerCmdExecFactory())
  override implicit val dockerFactory: DockerFactory = new DockerJavaExecutorFactory(client)

  val zookeeperInternalPort = 2181
  // toDo: use random port to avoid collisions with parallel execution test
  val zookeeperExposedPort = 32181
  val zookeeperName = "zookeeper"

  protected val zookeeperContainer: DockerContainer =
    DockerContainer(s"confluentinc/cp-zookeeper:${ConfluentPlatformTest.confluentVersion}", name = Some(zookeeperName))
      .withEnv("ZOOKEEPER_CLIENT_PORT=2181")
      .withPorts(zookeeperInternalPort -> Some(zookeeperExposedPort))
      .withReadyChecker(DockerReadyChecker.LogLineContains("binding to port 0.0.0.0/0.0.0.0:2181"))

  abstract override def dockerContainers: List[DockerContainer] =
    zookeeperContainer :: super.dockerContainers
}
