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

package org.make.api.question

import org.make.core.Order
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.technical.Pagination.{End, Start}

final case class SearchQuestionRequest(
  maybeQuestionIds: Option[Seq[QuestionId]] = None,
  maybeOperationIds: Option[Seq[OperationId]] = None,
  country: Option[Country] = None,
  language: Option[Language] = None,
  maybeSlug: Option[String] = None,
  skip: Option[Start] = None,
  end: Option[End] = None,
  sort: Option[String] = None,
  order: Option[Order] = None
)
