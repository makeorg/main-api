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

package org.make.api.migrations

import org.make.api.MakeApi
import org.make.api.migrations.InsertFixtureData.FixtureDataLine
import org.make.core.RequestContext
import org.make.core.operation.OperationId
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId

import scala.concurrent.Future

object VffGBData extends InsertFixtureData {
  var operationId: OperationId = _
  var localRequestContext: RequestContext = _
  override def requestContext: RequestContext = localRequestContext

  override def initialize(api: MakeApi): Future[Unit] = {
    api.operationService.findOneBySlug(VffOperation.operationSlug).flatMap {
      case Some(operation) =>
        Future.successful {
          operationId = operation.operationId
          localRequestContext = RequestContext.empty.copy(operationId = Some(operationId))
        }
      case None =>
        Future.failed(new IllegalStateException(s"Unable to find an operation with slug ${VffOperation.operationSlug}"))
    }
  }

  override def extractDataLine(line: String): Option[InsertFixtureData.FixtureDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(email, content, tags, country, language) =>
        Some(
          FixtureDataLine(
            email = email,
            content = content,
            theme = None,
            operation = Some(operationId),
            tags = tags.split('|').toSeq.map(TagId.apply),
            labels = Seq.empty,
            country = Country(country),
            language = Language(language)
          )
        )
      case _ => None
    }
  }

  override val dataResource: String = "fixtures/proposals_vff-gb.csv"
  override val runInProduction: Boolean = false

}
