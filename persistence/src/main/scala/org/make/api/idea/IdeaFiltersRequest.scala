/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.idea

import com.sksamuel.elastic4s.requests.searches.suggestion.Fuzziness
import org.make.core.{Order, RequestContext}
import org.make.core.idea.{IdeaSearchFilters, IdeaSearchQuery, NameSearchFilter, QuestionIdSearchFilter}
import org.make.core.question.QuestionId

final case class IdeaFiltersRequest(
  name: Option[String],
  questionId: Option[QuestionId],
  limit: Option[Int],
  skip: Option[Int],
  sort: Option[String],
  order: Option[Order]
) {
  def toSearchQuery(requestContext: RequestContext): IdeaSearchQuery = {
    val fuzziness = Fuzziness.Auto
    val filters: Option[IdeaSearchFilters] =
      IdeaSearchFilters.parse(name = name.map(text => {
        NameSearchFilter(text, Some(fuzziness))
      }), questionId = questionId.map(question => QuestionIdSearchFilter(question)))

    IdeaSearchQuery(
      filters = filters,
      limit = limit,
      skip = skip,
      sort = sort,
      order = order,
      language = requestContext.language
    )

  }
}

object IdeaFiltersRequest {
  val empty: IdeaFiltersRequest = IdeaFiltersRequest(None, None, None, None, None, None)
}
