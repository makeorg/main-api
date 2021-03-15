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
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom
import org.make.api.technical.{ActorReadJournalComponent, ShortenedNames}

import scala.concurrent.{ExecutionContext, Future}

class CassandraHealthCheckActor(healthCheckExecutionContext: ExecutionContext)
    extends HealthCheck
    with ShortenedNames
    with ActorReadJournalComponent {

  override val techno: String = "cassandra"

  val keyspace: String =
    context.system.settings.config.getString("make-api.event-sourcing.proposals.journal.keyspace")
  val table: String =
    context.system.settings.config.getString("make-api.event-sourcing.proposals.journal.table")

  override def healthCheck(): Future[String] = {
    proposalJournal.session
      .selectOne(selectFrom(keyspace, table).column("persistence_id").limit(1).build())
      .map { row =>
        if (row.isDefined) {
          "OK"
        } else {
          "NOK"
        }
      }(healthCheckExecutionContext)
  }

}

object CassandraHealthCheckActor extends HealthCheckActorDefinition {
  override val name: String = "cassandra-health-check"

  override def props(healthCheckExecutionContext: ExecutionContext): Props =
    Props(new CassandraHealthCheckActor(healthCheckExecutionContext))
}
