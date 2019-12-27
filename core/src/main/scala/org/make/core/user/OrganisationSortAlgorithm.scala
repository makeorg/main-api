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

import com.sksamuel.elastic4s.ElasticApi
import com.sksamuel.elastic4s.script.Script
import com.sksamuel.elastic4s.searches.SearchRequest
import com.sksamuel.elastic4s.searches.queries.Query
import com.sksamuel.elastic4s.searches.sort.{ScriptSort, ScriptSortType, SortOrder}
import org.make.core.question.QuestionId
import org.make.core.user.indexed.OrganisationElasticsearchFieldNames

sealed trait OrganisationSortAlgorithm {
  def sortDefinition(request: SearchRequest): SearchRequest
}

/*
 * This algorithm sorts organisations by their participation.
 * - +1 point per proposal
 * - +1 point per vote
 */
case class ParticipationAlgorithm(questionId: QuestionId) extends OrganisationSortAlgorithm {

  override def sortDefinition(request: SearchRequest): SearchRequest = {
    val query: Query = request.query.getOrElse(ElasticApi.boolQuery())
    request
      .query(
        ElasticApi
          .must(
            query,
            ElasticApi
              .nestedQuery(
                path = OrganisationElasticsearchFieldNames.countsByQuestion,
                query = ElasticApi.must(
                  ElasticApi
                    .termQuery(OrganisationElasticsearchFieldNames.countsByQuestionQuestionId, questionId.value),
                  ElasticApi.scriptQuery(
                    Script(
                      "doc['countsByQuestion.proposalsCount'].value + doc['countsByQuestion.votesCount'].value > 0"
                    )
                  )
                )
              )
          )
      )
      .sortBy(
        ScriptSort(
          script = Script("doc['proposalsCount'].value + doc['votesCount'].value"),
          scriptSortType = ScriptSortType.NUMBER,
          order = Some(SortOrder.DESC),
          nestedFilter = Some(
            ElasticApi
              .nestedQuery(
                path = OrganisationElasticsearchFieldNames.countsByQuestion,
                query =
                  ElasticApi.termQuery(OrganisationElasticsearchFieldNames.countsByQuestionQuestionId, questionId.value)
              )
          )
        )
      )

  }
}

object ParticipationAlgorithm {
  val shortName: String = "participation"
}

case object OrganisationAlgorithmSelector {
  val sortAlgorithmsName: Seq[String] = Seq(ParticipationAlgorithm.shortName)

  def select(sortAlgorithm: Option[String], questionId: Option[QuestionId]): Option[OrganisationSortAlgorithm] =
    sortAlgorithm match {
      case Some(ParticipationAlgorithm.shortName) if questionId.isDefined =>
        Some(ParticipationAlgorithm(questionId.get))
      case _ => None
    }

}
