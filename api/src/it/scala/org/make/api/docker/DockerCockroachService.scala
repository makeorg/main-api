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
