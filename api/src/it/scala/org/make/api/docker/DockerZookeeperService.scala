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
