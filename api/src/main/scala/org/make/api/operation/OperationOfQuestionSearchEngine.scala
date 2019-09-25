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

package org.make.api.operation

import akka.Done
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.searches.SearchRequest
import com.sksamuel.elastic4s.searches.queries.BoolQuery
import com.sksamuel.elastic4s.searches.sort.{FieldSort, SortOrder}
import com.sksamuel.elastic4s.{IndexAndType, RefreshPolicy}
import org.make.api.technical.elasticsearch.{ElasticsearchClientComponent, ElasticsearchConfigurationComponent, _}
import org.make.core.CirceFormatters
import org.make.core.operation.indexed.{
  IndexedOperationOfQuestion,
  OperationOfQuestionElasticsearchFieldNames,
  OperationOfQuestionSearchResult
}
import org.make.core.operation.{OperationOfQuestionSearchFilters, OperationOfQuestionSearchQuery}
import org.make.core.question.QuestionId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OperationOfQuestionSearchEngineComponent {
  def elasticsearchOperationOfQuestionAPI: OperationOfQuestionSearchEngine
}

trait OperationOfQuestionSearchEngine {
  def findOperationOfQuestionById(questionId: QuestionId): Future[Option[IndexedOperationOfQuestion]]
  def searchOperationOfQuestions(query: OperationOfQuestionSearchQuery): Future[OperationOfQuestionSearchResult]
  def indexOperationOfQuestion(record: IndexedOperationOfQuestion, maybeIndex: Option[IndexAndType]): Future[Done]
  def indexOperationOfQuestions(records: Seq[IndexedOperationOfQuestion],
                                maybeIndex: Option[IndexAndType]): Future[Done]
  def updateOperationOfQuestion(record: IndexedOperationOfQuestion, maybeIndex: Option[IndexAndType]): Future[Done]
}

object OperationOfQuestionSearchEngine {
  val operationOfQuestionIndexName = "operation-of-question"
}

trait DefaultOperationOfQuestionSearchEngineComponent
    extends OperationOfQuestionSearchEngineComponent
    with CirceFormatters {
  self: ElasticsearchConfigurationComponent with ElasticsearchClientComponent =>

  override lazy val elasticsearchOperationOfQuestionAPI: OperationOfQuestionSearchEngine =
    new DefaultOperationOfQuestionSearchEngine

  class DefaultOperationOfQuestionSearchEngine extends OperationOfQuestionSearchEngine {

    private lazy val client = elasticsearchClient.client

    private val operationOfQuestionAlias: IndexAndType =
      elasticsearchConfiguration.operationOfQuestionAliasName / OperationOfQuestionSearchEngine.operationOfQuestionIndexName

    override def findOperationOfQuestionById(questionId: QuestionId): Future[Option[IndexedOperationOfQuestion]] = {
      client
        .executeAsFuture(get(id = questionId.value).from(operationOfQuestionAlias))
        .map(_.toOpt[IndexedOperationOfQuestion])
    }

    override def searchOperationOfQuestions(
      query: OperationOfQuestionSearchQuery
    ): Future[OperationOfQuestionSearchResult] = {
      val searchFilters = OperationOfQuestionSearchFilters.getOperationOfQuestionSearchFilters(query)
      val request: SearchRequest = searchWithType(operationOfQuestionAlias)
        .bool(BoolQuery(must = searchFilters))
        .sortBy(
          Seq(
            OperationOfQuestionSearchFilters.getSort(query),
            Some(
              FieldSort(
                field = OperationOfQuestionElasticsearchFieldNames.endDate,
                order = SortOrder.DESC,
                missing = Some("_first")
              )
            ),
            Some(
              FieldSort(
                field = OperationOfQuestionElasticsearchFieldNames.startDate,
                order = SortOrder.DESC,
                missing = Some("_first")
              )
            )
          ).flatten
        )
        .size(OperationOfQuestionSearchFilters.getLimitSearch(query))
        .from(OperationOfQuestionSearchFilters.getSkipSearch(query))

      client
        .executeAsFuture(request)
        .map { response =>
          OperationOfQuestionSearchResult(total = response.totalHits, results = response.to[IndexedOperationOfQuestion])
        }
    }

    override def indexOperationOfQuestion(record: IndexedOperationOfQuestion,
                                          maybeIndex: Option[IndexAndType]): Future[Done] = {
      val index = maybeIndex.getOrElse(operationOfQuestionAlias)
      client
        .executeAsFuture(indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.questionId.value))
        .map { _ =>
          Done
        }
    }

    override def indexOperationOfQuestions(records: Seq[IndexedOperationOfQuestion],
                                           maybeIndex: Option[IndexAndType]): Future[Done] = {
      val index = maybeIndex.getOrElse(operationOfQuestionAlias)
      client
        .executeAsFuture(bulk(records.map { record =>
          indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.questionId.value)
        }))
        .map { _ =>
          Done
        }
    }

    override def updateOperationOfQuestion(record: IndexedOperationOfQuestion,
                                           maybeIndex: Option[IndexAndType]): Future[Done] = {
      val index = maybeIndex.getOrElse(operationOfQuestionAlias)
      client
        .executeAsFuture((update(id = record.questionId.value) in index).doc(record).refresh(RefreshPolicy.IMMEDIATE))
        .map(_ => Done)
    }
  }
}
