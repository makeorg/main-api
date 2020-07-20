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
import io.confluent.kafka.schemaregistry.client.rest.RestService
import org.make.api.ItMakeTest
import org.make.api.docker.DockerKafkaService
import org.make.api.technical.TimeSettings
import org.make.api.technical.healthcheck.HealthCheckCommands.CheckStatus

import scala.concurrent.ExecutionContext

class AvroHealthCheckActorIT
    extends TestKit(AvroHealthCheckActorIT.actorSystem)
    with ImplicitSender
    with ItMakeTest
    with DockerKafkaService {

  override val kafkaExposedPort: Int = 29592
  override val registryExposedPort: Int = 28581
  override val zookeeperExposedPort: Int = 32581

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  Feature("Check Avro status") {
    Scenario("get all subjects") {
      val schemaRegistryUrl: String = system.settings.config.getString("make-api.kafka.schema-registry")
      val restService = new RestService(schemaRegistryUrl)
      restService.registerSchema("{\"type\": \"string\"}", "test-schema")
      Given("a avro health check actor and a registered schema")
      val actorSystem = system
      val healthCheckExecutionContext = ExecutionContext.Implicits.global
      val healthCheckAvro: ActorRef =
        actorSystem.actorOf(AvroHealthCheckActor.props(healthCheckExecutionContext), AvroHealthCheckActor.name)

      When("I send a message to check the status of avro")
      healthCheckAvro ! CheckStatus
      Then("I get the status")
      val msg: HealthCheckResponse = expectMsgType[HealthCheckResponse](timeout.duration)
      And("status is \"OK\"")
      msg should be(HealthCheckSuccess("avro", "OK"))
    }
  }
}

object AvroHealthCheckActorIT {
  // This configuration cannot be dynamic, port values _must_ match reality
  val configuration: String =
    """
      |akka.log-dead-letters-during-shutdown = off
      |make-api {
      |  kafka {
      |    connection-string = "127.0.0.1:29592"
      |    poll-timeout = 1000
      |    schema-registry = "http://localhost:28581"
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

  val actorSystem = ActorSystem("AvroHealthCheckActorIT", ConfigFactory.parseString(configuration))
}
