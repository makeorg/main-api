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

import grizzled.slf4j.Logging
import org.make.api.technical.healthcheck.HealthCheck.Status
import org.make.swift.SwiftClient

import scala.concurrent.{ExecutionContext, Future}

class SwiftHealthCheck(swiftClient: SwiftClient) extends HealthCheck with Logging {

  override val techno: String = "swift"

  override def healthCheck()(implicit ctx: ExecutionContext): Future[Status] = {
    swiftClient
      .listBuckets()
      .map {
        case Seq() =>
          Status.NOK(Some("Unexpected result in swift health check: containers list is empty"))
        case _ => Status.OK
      }
  }
}
