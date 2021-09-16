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

package org.make.api.technical.healthcheck

import com.typesafe.config.{Config, ConfigFactory}
import org.make.api.ItMakeTest
import org.make.api.docker.DockerZookeeperService
import org.make.api.technical.healthcheck.HealthCheck.Status
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.DurationInt

class ZookeeperHealthCheckIT extends ItMakeTest with DockerZookeeperService {

  override val zookeeperExposedPort: Int = ZookeeperHealthCheckIT.zookeeperExposedPort
  override val zookeeperName: String = "zookeeper-health-check"

  implicit val ctx: ExecutionContext = global

  Feature("Check Zookeeper status") {
    Scenario("write current timestamp in zookeeper ephemeral path and read it") {
      val hc = new ZookeeperHealthCheck(ZookeeperHealthCheckIT.configuration)

      whenReady(hc.healthCheck(), Timeout(30.seconds)) { res =>
        res should be(Status.OK)
      }
    }
  }
}

object ZookeeperHealthCheckIT {
  val zookeeperExposedPort = 12182
  // This configuration cannot be dynamic, port values _must_ match reality
  val configuration: Config =
    ConfigFactory.parseString(s"""
      |make-api.zookeeper.url = "localhost:$zookeeperExposedPort"
    """.stripMargin)
}
