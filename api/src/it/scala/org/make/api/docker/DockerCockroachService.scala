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

import java.util.concurrent.atomic.AtomicInteger

trait DockerCockroachService extends DockerBaseTest {
  self: Suite =>

  private val defaultCockroachPort = 26257
  protected val cockroachExposedPort: Int = DockerCockroachService.CockroachPortProvider.getAndIncrement()

  private def cockroachContainer: DockerContainer =
    DockerContainer(image = "cockroachdb/cockroach:v19.1.1", name = Some(s"${getClass.getSimpleName}-cockroach"))
      .withPorts(defaultCockroachPort -> Some(cockroachExposedPort))
      .withReadyChecker(DockerReadyChecker.LogLineContains("CockroachDB node starting at"))
      .withCommand("start", "--insecure")

  abstract override def dockerContainers: List[DockerContainer] =
    cockroachContainer :: super.dockerContainers
}

object DockerCockroachService {
  val CockroachPortProvider: AtomicInteger = new AtomicInteger(40000)
}
