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

import com.typesafe.config.{Config, ConfigFactory}
import org.make.api.KafkaTest
import org.make.api.extensions.KafkaConfiguration
import org.make.api.technical.healthcheck.HealthCheck.Status
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.DurationInt

class KafkaHealthCheckIT extends KafkaTest {

  implicit val ctx: ExecutionContext = global

  val kafkaConfiguration: KafkaConfiguration = new KafkaConfiguration(KafkaHealthCheckIT.configuration)

  Feature("Check Kafka status") {
    Scenario("Kafka cluster is available") {
      val hc = new KafkaHealthCheck(kafkaConfiguration)

      whenReady(hc.healthCheck(), Timeout(30.seconds)) { res =>
        res should be(Status.OK)
      }
    }
  }

}

object KafkaHealthCheckIT {

  // This configuration cannot be dynamic, port values _must_ match reality
  val configuration: Config =
    ConfigFactory.parseString("""
      |connection-string = "127.0.0.1:29092"
      |poll-timeout = 1000
      |schema-registry = "http://localhost:28082"
      |topics {
      |  users = "users"
      |  emails = "emails"
      |  proposals = "proposals"
      |  mailjet-events = "mailjet-events"
      |  duplicates-predicted = "duplicates-predicted"
      |  sequences = "sequences"
      |  tracking-events = "tracking-events"
      |  ideas = "ideas"
      |  predictions = "predictions"
      |}
    """.stripMargin)
}
