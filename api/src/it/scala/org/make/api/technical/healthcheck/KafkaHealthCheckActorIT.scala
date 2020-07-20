/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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
import org.make.api.KafkaTest

import scala.concurrent.ExecutionContext

class KafkaHealthCheckActorIT extends TestKit(KafkaHealthCheckActorIT.actorSystem) with KafkaTest with ImplicitSender {

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  Feature("Check Kafka status") {
    Scenario("Kafka cluster is available") {
      Given("a Kafka health check actor")
      val healthCheckExecutionContext = ExecutionContext.Implicits.global
      val healthCheckKafka: ActorRef =
        system.actorOf(KafkaHealthCheckActor.props(healthCheckExecutionContext), KafkaHealthCheckActor.name)

      When("I send a message to check the status of Kafka")
      healthCheckKafka ! CheckStatus

      Then("I get the status")
      val msg: HealthCheckResponse = expectMsgType[HealthCheckResponse](timeout.duration)

      And("status is \"OK\"")
      msg should be(HealthCheckSuccess("kafka", "OK"))
    }
  }

}

object KafkaHealthCheckActorIT {

  // This configuration cannot be dynamic, port values _must_ match reality
  val configuration: String =
    """
      |akka.log-dead-letters-during-shutdown = off
      |make-api {
      |  kafka {
      |    connection-string = "127.0.0.1:29092"
      |    poll-timeout = 1000
      |    schema-registry = "http://localhost:28082"
      |    topics {
      |      users = "users"
      |      emails = "emails"
      |      proposals = "proposals"
      |      mailjet-events = "mailjet-events"
      |      duplicates-predicted = "duplicates-predicted"
      |      sequences = "sequences"
      |      tracking-events = "tracking-events"
      |      ideas = "ideas"
      |      predictions = "predictions"
      |    }
      |  }
      |}
    """.stripMargin

  val actorSystem: ActorSystem = ActorSystem("KafkaHealthCheckActorIT", ConfigFactory.parseString(configuration))

}
