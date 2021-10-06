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

import org.make.api.technical.healthcheck.HealthCheck.Status
import org.make.api.{DatabaseTest, ItMakeTest}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.DurationInt

class CockroachHealthCheckIT extends ItMakeTest with DatabaseTest {

  override protected val databaseName: String = CockroachHealthCheckIT.databaseName
  override val cockroachExposedPort: Int = CockroachHealthCheckIT.cockroachExposedPort

  override protected val adminEmail: String = "admin@make.org"

  implicit val ctx: ExecutionContext = global

  Feature("Check Cockroach status") {
    Scenario("read record where email is admin@make.org") {
      val hc = new CockroachHealthCheck("admin@make.org")

      whenReady(hc.healthCheck(), Timeout(30.seconds)) { res =>
        res should be(Status.OK)
      }
    }
  }
}

object CockroachHealthCheckIT {
  val cockroachExposedPort = 40000
  val databaseName = "healthcheck"
}
