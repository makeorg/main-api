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

package org.make.core.operation

import com.sksamuel.elastic4s.searches.SearchRequest
import com.sksamuel.elastic4s.searches.sort.{FieldSort, SortOrder}
import enumeratum.{Enum, EnumEntry}
import org.make.core.operation.indexed.OperationOfQuestionElasticsearchFieldName._

sealed abstract class SortAlgorithm(priority: FieldSort*) extends EnumEntry {

  private val sort = priority.toSeq ++ Seq(
    FieldSort(field = endDate.field, order = SortOrder.DESC, missing = Some("_first")),
    FieldSort(field = slug.field, order = SortOrder.ASC, missing = None)
  )

  def sortDefinition(request: SearchRequest): SearchRequest = request.sortBy(sort)

}

object SortAlgorithm extends Enum[SortAlgorithm] {

  case object Chronological extends SortAlgorithm
  case object Featured extends SortAlgorithm(FieldSort(field = featured.field, order = SortOrder.DESC, missing = None))

  override def values: IndexedSeq[SortAlgorithm] = findValues

}
