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
import org.make.api.technical.TimeSettings
import org.make.api.technical.healthcheck.HealthCheckCommands.CheckStatus
import org.make.api.{DatabaseTest, ItMakeTest}

import scala.concurrent.ExecutionContext

class CockroachHealthCheckActorIT
    extends TestKit(CockroachHealthCheckActorIT.actorSystem)
    with ImplicitSender
    with ItMakeTest
    with DatabaseTest {

  override protected def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  override protected val databaseName: String = CockroachHealthCheckActorIT.databaseName
  override val cockroachExposedPort: Int = CockroachHealthCheckActorIT.cockroachExposedPort

  override protected val adminEmail: String = "admin@make.org"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout
  feature("Check Cockroach status") {
    scenario("read record where email is admin@make.org") {
      Given("a cockroach health check actor")
      val actorSystem = system
      val healthCheckExecutionContext = ExecutionContext.Implicits.global
      val healthCheckCockroach: ActorRef = actorSystem.actorOf(
        CockroachHealthCheckActor.props(healthCheckExecutionContext),
        CockroachHealthCheckActor.name
      )

      When("I send a message to check the status of cockroach")
      healthCheckCockroach ! CheckStatus
      Then("I get the status")
      val msg: HealthCheckResponse = expectMsgType[HealthCheckResponse](timeout.duration)
      And("status is \"OK\"")
      msg should be(HealthCheckSuccess("cockroach", "OK"))
    }
  }
}

object CockroachHealthCheckActorIT {
  val cockroachExposedPort = 40000
  val databaseName = "healthcheck"

  // This configuration cannot be dynamic, port values _must_ match reality
  val configuration: String =
    s"""
       |make-api.database.jdbc-url = "jdbc:postgresql://localhost:$cockroachExposedPort/$databaseName"
    """.stripMargin

  val actorSystem = ActorSystem("CockroachHealthCheckActorIT", ConfigFactory.parseString(configuration))
}
