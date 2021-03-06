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

import com.whisk.docker.{DockerContainer, DockerReadyChecker}
import org.scalatest.Suite

import scala.concurrent.duration._

trait DockerCassandraService extends DockerBaseTest {
  self: Suite =>

  private val defaultCassandraPort = 9042
  protected def cassandraExposedPort: Int

  override val StartContainersTimeout: FiniteDuration = 1.minute

  private def cassandraContainer: DockerContainer =
    DockerContainer(image = "cassandra:3.10", name = Some(s"${getClass.getSimpleName}-cassandra"))
      .withPorts(defaultCassandraPort -> Some(cassandraExposedPort))
      .withReadyChecker(DockerReadyChecker.LogLineContains("Starting listening for CQL clients"))

  abstract override def dockerContainers: List[DockerContainer] =
    cassandraContainer :: super.dockerContainers

}
