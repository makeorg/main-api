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
import org.make.api.docker.DockerSwiftAllInOne
import org.make.api.technical.TimeSettings
import org.make.api.technical.healthcheck.HealthCheckCommands.CheckStatus
import org.make.api.{ActorSystemComponent, ItMakeTest}

import scala.concurrent.ExecutionContext

class SwiftHealthCheckActorIT
    extends TestKit(SwiftHealthCheckActorIT.actorSystem)
    with ItMakeTest
    with ImplicitSender
    with ActorSystemComponent
    with DockerSwiftAllInOne {

  override val externalPort: Option[Int] = Some(SwiftHealthCheckActorIT.port)
  override val actorSystem: ActorSystem = SwiftHealthCheckActorIT.actorSystem

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  feature("Check Swift status") {
    ignore("list swift container") {
      Given("a swift health check actor")
      val healthCheckExecutionContext = ExecutionContext.Implicits.global
      val healthCheckSwift: ActorRef =
        actorSystem.actorOf(SwiftHealthCheckActor.props(healthCheckExecutionContext), SwiftHealthCheckActor.name)

      When("I send a message to check the status of swift")
      healthCheckSwift ! CheckStatus
      Then("I get the status")
      val msg: HealthCheckResponse = expectMsgType[HealthCheckResponse](timeout.duration)
      And("status is \"OK\"")
      msg should be(HealthCheckSuccess("swift", "OK"))
    }
  }
}

object SwiftHealthCheckActorIT {
  val port = 60000
  val bucket: String = "healthcheck"
  val configuration: String =
    s"""
       |make-openstack {
       |  authentication {
       |    keystone-version = "keystone-V1"
       |    base-url = "http://localhost:$port/auth/v1.0"
       |    tenant-name = "test"
       |    username = "tester"
       |    password = "testing"
       |  }
       |
       |  storage {
       |    init-containers = ["$bucket"]
       |  }
       |}
     """.stripMargin

  val actorSystem: ActorSystem = {
    val config = ConfigFactory.load(ConfigFactory.parseString(configuration))
    ActorSystem(classOf[SwiftHealthCheckActorIT].getSimpleName, config)
  }
}
