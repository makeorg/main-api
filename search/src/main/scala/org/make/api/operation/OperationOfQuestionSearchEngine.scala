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

import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
import com.sksamuel.elastic4s.requests.searches.queries.compound.BoolQuery
import grizzled.slf4j.Logging
import io.circe.{Json, Printer}
import org.make.api.technical.elasticsearch.{ElasticsearchClientComponent, ElasticsearchConfigurationComponent, _}
import org.make.api.views.Highlights
import org.make.core.CirceFormatters
import org.make.core.elasticsearch.IndexationStatus
import org.make.core.operation.indexed.{
  IndexedOperationOfQuestion,
  OperationOfQuestionElasticsearchFieldName,
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
  def count(query: OperationOfQuestionSearchQuery): Future[Long]
  def searchOperationOfQuestions(query: OperationOfQuestionSearchQuery): Future[OperationOfQuestionSearchResult]
  def indexOperationOfQuestion(record: IndexedOperationOfQuestion, maybeIndex: Option[Index]): Future[IndexationStatus]
  def indexOperationOfQuestions(
    records: Seq[IndexedOperationOfQuestion],
    maybeIndex: Option[Index]
  ): Future[IndexationStatus]
  def highlights(): Future[Highlights]
}

object OperationOfQuestionSearchEngine {
  val operationOfQuestionIndexName: String = "operation-of-question"
}

trait DefaultOperationOfQuestionSearchEngineComponent
    extends OperationOfQuestionSearchEngineComponent
    with CirceFormatters
    with Logging {
  self: ElasticsearchConfigurationComponent with ElasticsearchClientComponent =>

  override lazy val elasticsearchOperationOfQuestionAPI: OperationOfQuestionSearchEngine =
    new DefaultOperationOfQuestionSearchEngine

  class DefaultOperationOfQuestionSearchEngine extends OperationOfQuestionSearchEngine {

    private lazy val client = elasticsearchClient.client

    private val operationOfQuestionAlias: Index =
      elasticsearchConfiguration.operationOfQuestionAliasName

    // TODO remove once elastic4s-circe upgrades to circe 0.14
    private implicit val printer: Json => String = Printer.noSpaces.print

    override def findOperationOfQuestionById(questionId: QuestionId): Future[Option[IndexedOperationOfQuestion]] = {
      client
        .executeAsFuture(get(operationOfQuestionAlias, questionId.value))
        .map(_.toOpt[IndexedOperationOfQuestion])
    }

    override def count(query: OperationOfQuestionSearchQuery): Future[Long] = {
      val request = search(operationOfQuestionAlias)
        .bool(BoolQuery(must = OperationOfQuestionSearchFilters.getOperationOfQuestionSearchFilters(query)))
        .limit(0)
        .trackTotalHits(true)

      client.executeAsFuture(request).map { response =>
        response.totalHits
      }
    }

    override def searchOperationOfQuestions(
      query: OperationOfQuestionSearchQuery
    ): Future[OperationOfQuestionSearchResult] = {
      val searchFilters = OperationOfQuestionSearchFilters.getOperationOfQuestionSearchFilters(query)
      val request: SearchRequest = search(operationOfQuestionAlias)
        .bool(BoolQuery(must = searchFilters))
        .sortBy(
          Seq(
            OperationOfQuestionSearchFilters.getSort(query),
            Some(FieldSort(field = OperationOfQuestionElasticsearchFieldName.endDate.field, order = SortOrder.DESC)),
            Some(FieldSort(field = OperationOfQuestionElasticsearchFieldName.startDate.field, order = SortOrder.DESC))
          ).flatten
        )
        .size(OperationOfQuestionSearchFilters.getLimitSearch(query))
        .from(OperationOfQuestionSearchFilters.getSkipSearch(query))
        .trackTotalHits(true)

      val requestWithAlgorithm = query.sortAlgorithm match {
        case Some(algorithm) => algorithm.sortDefinition(request)
        case _               => request
      }

      client
        .executeAsFuture(requestWithAlgorithm)
        .map { response =>
          OperationOfQuestionSearchResult(total = response.totalHits, results = response.to[IndexedOperationOfQuestion])
        }
    }

    override def indexOperationOfQuestion(
      record: IndexedOperationOfQuestion,
      maybeIndex: Option[Index]
    ): Future[IndexationStatus] = {
      val index = maybeIndex.getOrElse(operationOfQuestionAlias)
      client
        .executeAsFuture(indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.questionId.value))
        .map(_ => IndexationStatus.Completed)
        .recover {
          case e: Exception =>
            logger.error(s"Indexing upserted operation of question ${record.questionId} failed", e)
            IndexationStatus.Failed(e)
        }
    }

    override def indexOperationOfQuestions(
      records: Seq[IndexedOperationOfQuestion],
      maybeIndex: Option[Index]
    ): Future[IndexationStatus] = {
      val index = maybeIndex.getOrElse(operationOfQuestionAlias)
      client
        .executeAsFuture(bulk(records.map { record =>
          indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.questionId.value)
        }))
        .map { _ =>
          IndexationStatus.Completed
        }
        .recover {
          case e: Exception =>
            logger.error(s"Indexing ${records.size} operations of questions failed", e)
            IndexationStatus.Failed(e)
        }
    }

    override def highlights(): Future[Highlights] = {
      client
        .executeAsFuture(
          search(operationOfQuestionAlias)
            .aggregations(
              sumAgg(
                OperationOfQuestionElasticsearchFieldName.participantsCount.field,
                OperationOfQuestionElasticsearchFieldName.participantsCount.field
              ),
              sumAgg(
                OperationOfQuestionElasticsearchFieldName.proposalsCount.field,
                OperationOfQuestionElasticsearchFieldName.proposalsCount.field
              )
            )
            .trackTotalHits(true)
        )
        .map { response =>
          Highlights(
            participantsCount = response.aggregations
              .sum(OperationOfQuestionElasticsearchFieldName.participantsCount.field)
              .valueOpt
              .fold(0)(_.toInt),
            proposalsCount = response.aggregations
              .sum(OperationOfQuestionElasticsearchFieldName.proposalsCount.field)
              .valueOpt
              .fold(0)(_.toInt),
            partnersCount = 0
          )
        }
    }
  }
}
