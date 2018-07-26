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

import scala.concurrent.duration.DurationInt

trait DockerElasticsearchService extends DockerKit {

  val defaultElasticsearchHttpPort = 9200
  val defaultElasticsearchClientPort = 9300

  // Port range: [30000-30999]
  def elasticsearchExposedPort: Int

  val defaultElasticsearchProposalIndex = "proposals"
  val defaultElasticsearchProposalDocType = "proposal"

  private def elasticSearchContainer =
    DockerContainer("makeorg/make-elasticsearch:6.3.0")
      .withPorts(defaultElasticsearchHttpPort -> Some(elasticsearchExposedPort))
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
