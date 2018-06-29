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

package org.make.api.technical

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Origin
import com.typesafe.scalalogging.StrictLogging
import kamon.akka.http.AkkaHttp.OperationNameGenerator

class MakeOperationNameGenerator extends OperationNameGenerator with StrictLogging {

  logger.info("creating make name generator for akka-http")

  override def serverOperationName(request: HttpRequest): String = {
    originFromHeaders(request).map(origin => "origin-" + origin).getOrElse("origin-unknown")
  }

  // Copied from kamon-akka-http
  override def clientOperationName(request: HttpRequest): String = {
    request.uri.copy(rawQueryString = None, fragment = None).toString()
  }

  private def originFromHeaders(request: HttpRequest): Option[String] = {
    request.header[Origin].flatMap(_.origins.headOption.map(_.host.host.address()))
  }

}
