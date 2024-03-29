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

import com.sksamuel.elastic4s.{ElasticClient, Handler}
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

package object elasticsearch extends Logging {
  implicit class RichHttpClient(val self: ElasticClient) extends AnyVal {
    def executeAsFuture[T, U](
      request: T
    )(implicit exec: Handler[T, U], executionContext: ExecutionContext, manifest: Manifest[U]): Future[U] = {

      logger.debug(self.show(request).replace('\n', ' '))
      self.execute(request).flatMap { result =>
        if (result.isSuccess) {
          Future.successful(result.result)
        } else {
          Future.failed(new ElasticException(result.error, self.show(request)))
        }
      }
    }
  }
}
