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

import grizzled.slf4j.Logging
import kamon.instrumentation.http.{HttpMessage, HttpOperationNameGenerator}
import kamon.Kamon
import kamon.trace.Span
import org.make.api.technical.tracing.Tracing
import com.typesafe.config.ConfigFactory

class MakeClientOperationNameGenerator extends HttpOperationNameGenerator with Logging {

  private val esConfig =
    ConfigFactory
      .load("default-application.conf")
      .getConfig("make-api.elasticSearch")

  private val connectionString = esConfig.getString("connection-string")

  logger.info("Creating the Make name generator for akka-http")

  override def name(request: HttpMessage.Request): Option[String] = {
    val operationName: Option[String] = Tracing.entrypoint
    val spanName: String = Kamon.currentSpan().operationName()

    if (spanName == Span.Empty.operationName() || operationName.contains(spanName)) {
      Some(createOperationNameFromRequest(request))
    } else {
      Some(spanName)
    }
  }

  private def createOperationNameFromRequest(request: HttpMessage.Request): String = {
    // ES query parameters are part of its path. We need to clean it up by only keeping the relevant
    // part so as not to blow up the granularity of operation names.
    if (connectionString.contains(request.host)) {
      // The .tail here removes the leading '/'
      "elasticsearch" ++ request.path.tail.split("/").headOption.fold("")('-' +: _)
    } else {
      val resolvedPort =
        if (request.port != 80 && request.port != 443 && request.port > 0) {
          s":${request.port}"
        } else {
          ""
        }
      s"${request.host}$resolvedPort${request.path}"
    }
  }
}
