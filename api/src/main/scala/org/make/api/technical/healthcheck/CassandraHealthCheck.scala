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
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom
import com.typesafe.config.Config
import org.make.api.technical.healthcheck.HealthCheck.Status

import scala.concurrent.{ExecutionContext, Future}

class CassandraHealthCheck(proposalJournal: CassandraReadJournal, config: Config) extends HealthCheck {

  override val techno: String = "cassandra"

  val keyspace: String = config.getString("make-api.event-sourcing.proposals.journal.keyspace")
  val table: String = config.getString("make-api.event-sourcing.proposals.journal.table")

  override def healthCheck()(implicit ctx: ExecutionContext): Future[Status] = {
    proposalJournal.session
      .selectOne(selectFrom(keyspace, table).column("persistence_id").limit(1).build())
      .map { row =>
        if (row.isDefined) {
          Status.OK
        } else {
          Status.NOK()
        }
      }
  }
}
