package org.make.api.docker

import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientConfig}
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory
import com.whisk.docker.impl.dockerjava.{Docker, DockerJavaExecutorFactory}
import com.whisk.docker.{DockerContainer, DockerFactory, DockerKit, DockerReadyChecker}

trait DockerZookeeperService extends DockerKit {
  private val dockerClientConfig: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
  private val client: Docker = new Docker(dockerClientConfig, new JerseyDockerCmdExecFactory())
  override implicit val dockerFactory: DockerFactory = new DockerJavaExecutorFactory(client)

  final val zookeeperInternalPort: Int = 2181
  private val defaultZookeeperExposedPort: Int = 32181
  def zookeeperExposedPort: Int = defaultZookeeperExposedPort
  def zookeeperName: String = "zookeeper"

  protected def zookeeperContainer: DockerContainer =
    DockerContainer(s"confluentinc/cp-zookeeper:${ConfluentPlatformTest.confluentVersion}", name = Some(zookeeperName))
      .withEnv(s"ZOOKEEPER_CLIENT_PORT=$zookeeperInternalPort")
      .withPorts(zookeeperInternalPort -> Some(zookeeperExposedPort))
      .withReadyChecker(DockerReadyChecker.LogLineContains(s"binding to port 0.0.0.0/0.0.0.0:$zookeeperInternalPort"))

  abstract override def dockerContainers: List[DockerContainer] =
    zookeeperContainer :: super.dockerContainers
}
