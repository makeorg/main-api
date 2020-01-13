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

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.make.api.ItMakeTest
import org.make.api.docker.DockerZookeeperService
import org.make.api.technical.TimeSettings
import org.make.api.technical.healthcheck.HealthCheckCommands.CheckStatus

import scala.concurrent.ExecutionContext

class ZookeeperHealthCheckActorIT
    extends TestKit(ZookeeperHealthCheckActorIT.actorSystem)
    with ImplicitSender
    with ItMakeTest
    with DockerZookeeperService {

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

  override val zookeeperExposedPort: Int = ZookeeperHealthCheckActorIT.zookeeperExposedPort
  override val zookeeperName: String = "zookeeper-health-check"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout
  feature("Check Zookeeper status") {
    scenario("write current timestamp in zookeeper ephemeral path and read it") {
      Given("a zookeeper health check actor")
      val actorSystem = system
      val healthCheckExecutionContext = ExecutionContext.Implicits.global
      val healthCheckZookeeper: ActorRef = actorSystem.actorOf(
        ZookeeperHealthCheckActor.props(healthCheckExecutionContext),
        ZookeeperHealthCheckActor.name
      )

      When("I send a message to check the status of zookeeper")
      healthCheckZookeeper ! CheckStatus
      Then("I get the status")
      val msg: HealthCheckResponse = expectMsgType[HealthCheckResponse](timeout.duration)
      And("status is \"OK\"")
      msg should be(HealthCheckSuccess("zookeeper", "OK"))
    }
  }
}

object ZookeeperHealthCheckActorIT {
  val zookeeperExposedPort = 12182
  // This configuration cannot be dynamic, port values _must_ match reality
  val configuration: String =
    s"""
      |akka.log-dead-letters-during-shutdown = off
      |make-api.zookeeper.url = "localhost:$zookeeperExposedPort"
    """.stripMargin

  val actorSystem = ActorSystem("ZookeeperHealthCheckActorIT", ConfigFactory.parseString(configuration))

}
