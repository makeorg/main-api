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

import cats.Show
import com.sksamuel.elastic4s.admin.AliasExistsDefinition
import com.sksamuel.elastic4s.alias.{GetAliasesDefinition, IndicesAliasesRequestDefinition}
import com.sksamuel.elastic4s.get.GetDefinition
import com.sksamuel.elastic4s.http.{HttpClient, HttpExecutable}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

package object elasticsearch extends StrictLogging {
  implicit class RichHttpClient(val self: HttpClient) extends AnyVal {
    def executeAsFuture[T, U](request: T)(implicit exec: HttpExecutable[T, U],
                                          executionContext: ExecutionContext = ExecutionContext.Implicits.global,
                                          show: Show[T]): Future[U] = {
      logger.debug(self.show(request).replace('\n', ' '))
      self.execute(request).flatMap {
        case Right(result) => Future.successful(result.result)
        case Left(errors)  => Future.failed(new ElasticException(errors.error, self.show(request)))
      }
    }
  }

  implicit val showGetDefinition: Show[GetDefinition] = _.toString
  implicit val showGetAliasDefinition: Show[GetAliasesDefinition] = _.toString
  implicit val showIndicesAliasesRequestDefinition: Show[IndicesAliasesRequestDefinition] = _.toString
  implicit val showAliasExistsDefinition: Show[AliasExistsDefinition] = _.toString
}
