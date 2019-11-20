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

package org.make.core.user

import com.sksamuel.elastic4s.script.Script
import com.sksamuel.elastic4s.searches.SearchRequest
import com.sksamuel.elastic4s.searches.sort.{ScriptSort, ScriptSortType, SortOrder}

sealed trait OrganisationSortAlgorithm {
  def sortDefinition(request: SearchRequest): SearchRequest
}

/*
 * This algorithm sorts organisations by their participation.
 * - +1 point per proposal
 * - +1 point per vote
 */
case object ParticipationAlgorithm extends OrganisationSortAlgorithm {

  override def sortDefinition(request: SearchRequest): SearchRequest = {
    request
      .sortBy(
        ScriptSort(
          Script("doc['proposalsCount'].value + doc['votesCount'].value"),
          ScriptSortType.NUMBER,
          order = Some(SortOrder.DESC)
        )
      )
  }

  val shortName: String = "participation"
}

case object OrganisationAlgorithmSelector {
  val sortAlgorithmsName: Seq[String] = Seq(ParticipationAlgorithm.shortName)

  def select(sortAlgorithm: Option[String]): Option[OrganisationSortAlgorithm] = sortAlgorithm match {
    case Some(ParticipationAlgorithm.shortName) => Some(ParticipationAlgorithm)
    case _                                      => None
  }

}
