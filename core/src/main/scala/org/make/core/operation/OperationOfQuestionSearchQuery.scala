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

package org.make.core
package operation

import java.time.{ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeFormatter

import cats.data.NonEmptyList
import com.sksamuel.elastic4s.{ElasticApi, Operator}
import com.sksamuel.elastic4s.http.ElasticDsl
import com.sksamuel.elastic4s.searches.queries.Query
import com.sksamuel.elastic4s.searches.sort.{FieldSort, SortOrder}
import com.sksamuel.elastic4s.searches.suggestion.Fuzziness
import org.make.core.operation.indexed.OperationOfQuestionElasticsearchFieldName
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}

final case class OperationOfQuestionSearchQuery(
  filters: Option[OperationOfQuestionSearchFilters] = None,
  limit: Option[Int] = None,
  skip: Option[Int] = None,
  sort: Option[OperationOfQuestionElasticsearchFieldName] = None,
  order: Option[Order] = None,
  sortAlgorithm: Option[SortAlgorithm] = None
)

final case class OperationOfQuestionSearchFilters(
  questionIds: Option[QuestionIdsSearchFilter] = None,
  question: Option[QuestionContentSearchFilter] = None,
  description: Option[DescriptionSearchFilter] = None,
  country: Option[CountrySearchFilter] = None,
  language: Option[LanguageSearchFilter] = None,
  startDate: Option[StartDateSearchFilter] = None,
  endDate: Option[EndDateSearchFilter] = None,
  operationKinds: Option[OperationKindsSearchFilter] = None,
  featured: Option[FeaturedSearchFilter] = None,
  status: Option[StatusSearchFilter] = None,
  hasResults: Option[HasResultsSearchFilter.type] = None
)

object OperationOfQuestionSearchFilters extends ElasticDsl {

  def parse(
    questionIds: Option[QuestionIdsSearchFilter],
    question: Option[QuestionContentSearchFilter],
    description: Option[DescriptionSearchFilter] = None,
    country: Option[CountrySearchFilter] = None,
    language: Option[LanguageSearchFilter] = None,
    startDate: Option[StartDateSearchFilter] = None,
    endDate: Option[EndDateSearchFilter] = None,
    operationKind: Option[OperationKindsSearchFilter] = None,
    featured: Option[FeaturedSearchFilter] = None,
    status: Option[StatusSearchFilter] = None
  ): Option[OperationOfQuestionSearchFilters] = {
    (questionIds, question, description, country, language, startDate, endDate, operationKind, featured, status) match {
      case (None, None, None, None, None, None, None, None, None, None) => None
      case _ =>
        Some(
          OperationOfQuestionSearchFilters(
            questionIds,
            question,
            description,
            country,
            language,
            startDate,
            endDate,
            operationKind,
            featured,
            status
          )
        )
    }
  }

  def getOperationOfQuestionSearchFilters(
    operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery
  ): Seq[Query] = {
    Seq(
      buildQuestionIdsSearchFilter(operationOfQuestionSearchQuery),
      buildQuestionContentSearchFilter(operationOfQuestionSearchQuery),
      buildDescriptionSearchFilter(operationOfQuestionSearchQuery),
      buildCountrySearchFilter(operationOfQuestionSearchQuery),
      buildLanguageSearchFilter(operationOfQuestionSearchQuery),
      buildStartDateSearchFilter(operationOfQuestionSearchQuery),
      buildEndDateSearchFilter(operationOfQuestionSearchQuery),
      buildOperationKindSearchFilter(operationOfQuestionSearchQuery),
      buildFeaturedSearchFilter(operationOfQuestionSearchQuery),
      buildStatusSearchFilter(operationOfQuestionSearchQuery),
      buildHasResultsSearchFilter(operationOfQuestionSearchQuery)
    ).flatten
  }

  def getSkipSearch(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Int =
    operationOfQuestionSearchQuery.skip
      .getOrElse(0)

  def getLimitSearch(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Int =
    operationOfQuestionSearchQuery.limit
      .getOrElse(10)

  def getSort(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[FieldSort] = {
    val order = operationOfQuestionSearchQuery.order.map(_.sortOrder)

    operationOfQuestionSearchQuery.sort.map { sort =>
      val sortFieldName = if (sort == OperationOfQuestionElasticsearchFieldName.question) {
        OperationOfQuestionElasticsearchFieldName.questionKeyword
      } else {
        sort
      }
      FieldSort(field = sortFieldName.field, order = order.getOrElse(SortOrder.Asc))
    }
  }

  def buildQuestionIdsSearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.questionIds match {
        case Some(QuestionIdsSearchFilter(Seq(questionId))) =>
          Some(ElasticApi.termQuery(OperationOfQuestionElasticsearchFieldName.questionId.field, questionId.value))
        case Some(QuestionIdsSearchFilter(questionIds)) =>
          Some(
            ElasticApi.termsQuery(OperationOfQuestionElasticsearchFieldName.questionId.field, questionIds.map(_.value))
          )
        case _ => None
      }
    }
  }

  def buildQuestionContentSearchFilter(
    operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery
  ): Option[Query] = {
    val query: Option[Query] = for {
      filters                                       <- operationOfQuestionSearchQuery.filters
      QuestionContentSearchFilter(text, maybeFuzzy) <- filters.question
    } yield {
      val language = filters.language.map(_.language).getOrElse(Language("fr"))
      val fieldsBoosts: Map[String, Double] =
        Map(
          Some(OperationOfQuestionElasticsearchFieldName.question.field) -> 3d,
          OperationOfQuestionElasticsearchFieldName.questionLanguageSubfield(language) -> 2d,
          OperationOfQuestionElasticsearchFieldName.questionLanguageSubfield(language, stemmed = true) -> 1.5d
        ).collect {
          case (Some(key), value) => key -> value
        }
      multiMatchQuery(text).fields(fieldsBoosts).fuzziness("Auto:4,7").operator(Operator.AND)
    }

    query
  }

  def buildDescriptionSearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.description match {
        case Some(DescriptionSearchFilter(description)) =>
          Some(ElasticApi.termQuery(OperationOfQuestionElasticsearchFieldName.description.field, description))
        case None => None
      }
    }
  }

  def buildCountrySearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.country match {
        case Some(CountrySearchFilter(country)) =>
          Some(ElasticApi.termsQuery(OperationOfQuestionElasticsearchFieldName.country.field, country.value))
        case _ => None
      }
    }
  }

  def buildLanguageSearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.language match {
        case Some(LanguageSearchFilter(language)) =>
          Some(ElasticApi.termsQuery(OperationOfQuestionElasticsearchFieldName.language.field, language.value))
        case _ => None
      }
    }
  }

  def buildStartDateSearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.startDate match {
        case Some(StartDateSearchFilter(startDate)) =>
          val dateFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
          Some(
            ElasticApi
              .termQuery(OperationOfQuestionElasticsearchFieldName.startDate.field, startDate.format(dateFormatter))
          )
        case None => None
      }
    }
  }

  def buildEndDateSearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.endDate match {
        case Some(EndDateSearchFilter(endDate)) =>
          val dateFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
          Some(
            ElasticApi.termQuery(OperationOfQuestionElasticsearchFieldName.endDate.field, endDate.format(dateFormatter))
          )
        case None => None
      }
    }
  }

  def buildOperationKindSearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.operationKinds match {
        case Some(OperationKindsSearchFilter(Seq(operationKind))) =>
          Some(ElasticApi.termQuery(OperationOfQuestionElasticsearchFieldName.operationKind.field, operationKind.value))
        case Some(OperationKindsSearchFilter(operationKinds)) =>
          Some(
            ElasticApi
              .termsQuery(OperationOfQuestionElasticsearchFieldName.operationKind.field, operationKinds.map(_.value))
          )
        case None =>
          Some(
            ElasticApi.termsQuery(
              OperationOfQuestionElasticsearchFieldName.operationKind.field,
              OperationKind.publicKinds.map(_.value)
            )
          )
      }
    }
  }

  def buildFeaturedSearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.featured match {
        case Some(FeaturedSearchFilter(featured)) =>
          Some(ElasticApi.termQuery(OperationOfQuestionElasticsearchFieldName.featured.field, featured))
        case _ => None
      }
    }
  }

  def buildStatusSearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.status match {
        case Some(StatusSearchFilter(NonEmptyList(status, Nil))) =>
          Some(
            ElasticApi.termQuery(OperationOfQuestionElasticsearchFieldName.status.field, status.entryName.toLowerCase)
          )
        case Some(StatusSearchFilter(statuses)) =>
          Some(
            ElasticApi.termsQuery(
              OperationOfQuestionElasticsearchFieldName.status.field,
              statuses.map(_.entryName.toLowerCase).toList
            )
          )
        case _ => None
      }
    }
  }

  def buildHasResultsSearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.hasResults match {
        case Some(HasResultsSearchFilter) =>
          Some(ElasticApi.existsQuery(OperationOfQuestionElasticsearchFieldName.resultsLink.field))
        case _ => None
      }
    }
  }

}

final case class QuestionIdsSearchFilter(questionIds: Seq[QuestionId])
final case class QuestionContentSearchFilter(text: String, fuzzy: Option[Fuzziness] = None)
final case class DescriptionSearchFilter(description: String)
final case class CountrySearchFilter(country: Country)
final case class LanguageSearchFilter(language: Language)
final case class StartDateSearchFilter(startDate: ZonedDateTime)
final case class EndDateSearchFilter(endDate: ZonedDateTime)
final case class OperationKindsSearchFilter(operationKinds: Seq[OperationKind])
final case class FeaturedSearchFilter(featured: Boolean)
case object HasResultsSearchFilter
final case class StatusSearchFilter(status: NonEmptyList[OperationOfQuestion.Status])
object StatusSearchFilter {
  def apply(head: OperationOfQuestion.Status, tail: OperationOfQuestion.Status*): StatusSearchFilter =
    StatusSearchFilter(NonEmptyList.of(head, tail: _*))
}
