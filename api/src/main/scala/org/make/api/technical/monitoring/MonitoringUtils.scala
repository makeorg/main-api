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

package org.make.api.technical.monitoring

import kamon.Kamon
import kamon.tag.TagSet
import org.make.core.RequestContext

object MonitoringUtils {

  private val DefaultAttributeValue = "unknown"

  private val formatAttribute: Option[String] => String =
    _.fold(DefaultAttributeValue)(MonitoringMessageHelper.format)

  def logRequest(operationName: String, context: RequestContext, origin: Option[String]): Unit =
    Kamon
      .counter("api-requests")
      .withTags(
        TagSet.from(
          Map(
            "source" -> formatAttribute(context.source),
            "operation" -> operationName,
            "origin" -> formatAttribute(origin),
            "application" -> formatAttribute(context.applicationName.map(_.value)),
            "location" -> formatAttribute(context.location),
            "question" -> formatAttribute(context.questionId.map(_.value))
          )
        )
      )
      .increment()
}
