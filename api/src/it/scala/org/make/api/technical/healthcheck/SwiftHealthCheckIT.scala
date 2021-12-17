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

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.util
import com.typesafe.config.ConfigFactory
import org.make.api.MakeUnitTest
import org.make.api.docker.DockerSwiftAllInOne
import org.make.api.extensions.SwiftClientExtension
import org.make.api.technical.TimeSettings
import org.make.swift.SwiftClient
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

class SwiftHealthCheckIT
    extends ScalaTestWithActorTestKit(SwiftHealthCheckIT.actorSystem)
    with MakeUnitTest
    with DockerSwiftAllInOne {

  override val externalPort: Option[Int] = Some(SwiftHealthCheckIT.port)

  implicit override val timeout: util.Timeout = TimeSettings.defaultTimeout
  implicit val ctx: ExecutionContext = global

  lazy val client: SwiftClient = SwiftClientExtension(system).swiftClient

  Feature("Check Swift status") {
    ignore("list swift container") {
      Await.result(client.init(), atMost = 30.seconds)

      val hc = new SwiftHealthCheck(client)

      whenReady(hc.healthCheck(), Timeout(30.seconds)) { res =>
        res should be("OK")
      }
    }
  }
}

object SwiftHealthCheckIT {
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

  val actorSystem: ActorSystem[Nothing] =
    ActorSystem[Nothing](
      Behaviors.empty,
      "SwiftHealthCheckIT",
      ConfigFactory.load(ConfigFactory.parseString(configuration))
    )
}
