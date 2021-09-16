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

import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.healthcheck.HealthCheck.Status
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future}

class CockroachHealthCheck(defaultAdminEmail: String) extends HealthCheck {

  override val techno: String = "cockroach"

  override def healthCheck()(implicit ctx: ExecutionContext): Future[Status] = {
    Future(NamedDB("READ").retryableTx { implicit session =>
      sql"select first_name from make_user where email=$defaultAdminEmail".map(_.toMap()).list().apply()
    }).map(_.length).map {
      case 1 => Status.OK
      case other =>
        Status.NOK(Some(s"""Unexpected result in cockroach health check: expected "1" result but got "$other""""))
    }
  }
}
