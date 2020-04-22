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

import akka.actor.Props
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future}

class CockroachHealthCheckActor(healthCheckExecutionContext: ExecutionContext) extends HealthCheck with ShortenedNames {

  override val techno: String = "cockroach"

  override def healthCheck(): Future[String] = {
    implicit val cxt: EC = healthCheckExecutionContext

    val futureResults: Future[List[Map[String, Any]]] = Future(NamedDB("READ").retryableTx { implicit session =>
      sql"select first_name from make_user where email='admin@make.org'".map(_.toMap).list.apply()
    })

    futureResults
      .map(_.length)
      .map {
        case 1 => "OK"
        case other =>
          log.warning(s"""Unexpected result in cockroach health check: expected "1" result but got "$other"""")
          "NOK"
      }
  }
}

object CockroachHealthCheckActor extends HealthCheckActorDefinition {
  override val name: String = "cockroach-health-check"

  override def props(healthCheckExecutionContext: ExecutionContext): Props =
    Props(new CockroachHealthCheckActor(healthCheckExecutionContext))
}
