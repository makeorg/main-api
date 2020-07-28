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

package org.make.core.operation

import java.time.{ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeFormatter

import com.sksamuel.elastic4s.{ElasticApi, Operator}
import com.sksamuel.elastic4s.http.ElasticDsl
import com.sksamuel.elastic4s.searches.queries.Query
import com.sksamuel.elastic4s.searches.sort.{FieldSort, SortOrder}
import com.sksamuel.elastic4s.searches.suggestion.Fuzziness
import org.make.core.operation.indexed.OperationOfQuestionElasticsearchFieldNames
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}

case class OperationOfQuestionSearchQuery(
  filters: Option[OperationOfQuestionSearchFilters] = None,
  limit: Option[Int] = None,
  skip: Option[Int] = None,
  sort: Option[String] = None,
  order: Option[String] = None,
  sortAlgorithm: Option[SortAlgorithm] = None
)

case class OperationOfQuestionSearchFilters(
  questionIds: Option[QuestionIdsSearchFilter] = None,
  question: Option[QuestionContentSearchFilter] = None,
  description: Option[DescriptionSearchFilter] = None,
  country: Option[CountrySearchFilter] = None,
  language: Option[LanguageSearchFilter] = None,
  startDate: Option[StartDateSearchFilter] = None,
  endDate: Option[EndDateSearchFilter] = None,
  operationKinds: Option[OperationKindsSearchFilter] = None,
  featured: Option[FeaturedSearchFilter] = None,
  status: Option[StatusSearchFilter] = None
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
      buildStatusSearchFilter(operationOfQuestionSearchQuery)
    ).flatten
  }

  def getSkipSearch(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Int =
    operationOfQuestionSearchQuery.skip
      .getOrElse(0)

  def getLimitSearch(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Int =
    operationOfQuestionSearchQuery.limit
      .getOrElse(10)

  def getSort(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[FieldSort] = {
    val order = operationOfQuestionSearchQuery.order.map {
      case asc if asc.toLowerCase == "asc"    => SortOrder.ASC
      case desc if desc.toLowerCase == "desc" => SortOrder.DESC
    }

    operationOfQuestionSearchQuery.sort.map { sort =>
      val sortFieldName: String = if (sort == OperationOfQuestionElasticsearchFieldNames.question) {
        OperationOfQuestionElasticsearchFieldNames.questionKeyword
      } else {
        sort
      }
      FieldSort(field = sortFieldName, order = order.getOrElse(SortOrder.ASC))
    }
  }

  def buildQuestionIdsSearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.questionIds match {
        case Some(QuestionIdsSearchFilter(Seq(questionId))) =>
          Some(ElasticApi.termQuery(OperationOfQuestionElasticsearchFieldNames.questionId, questionId.value))
        case Some(QuestionIdsSearchFilter(questionIds)) =>
          Some(ElasticApi.termsQuery(OperationOfQuestionElasticsearchFieldNames.questionId, questionIds.map(_.value)))
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
          Some(OperationOfQuestionElasticsearchFieldNames.question) -> 3d,
          OperationOfQuestionElasticsearchFieldNames.questionLanguageSubfield(language) -> 2d,
          OperationOfQuestionElasticsearchFieldNames.questionLanguageSubfield(language, stemmed = true) -> 1.5d
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
          Some(ElasticApi.termQuery(OperationOfQuestionElasticsearchFieldNames.description, description))
        case None => None
      }
    }
  }

  def buildCountrySearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.country match {
        case Some(CountrySearchFilter(country)) =>
          Some(ElasticApi.termsQuery(OperationOfQuestionElasticsearchFieldNames.country, country.value))
        case _ => None
      }
    }
  }

  def buildLanguageSearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.language match {
        case Some(LanguageSearchFilter(language)) =>
          Some(ElasticApi.termsQuery(OperationOfQuestionElasticsearchFieldNames.language, language.value))
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
            ElasticApi.termQuery(OperationOfQuestionElasticsearchFieldNames.startDate, startDate.format(dateFormatter))
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
          Some(ElasticApi.termQuery(OperationOfQuestionElasticsearchFieldNames.endDate, endDate.format(dateFormatter)))
        case None => None
      }
    }
  }

  def buildOperationKindSearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.operationKinds match {
        case Some(OperationKindsSearchFilter(Seq(operationKind))) =>
          Some(ElasticApi.termQuery(OperationOfQuestionElasticsearchFieldNames.operationKind, operationKind.value))
        case Some(OperationKindsSearchFilter(operationKinds)) =>
          Some(
            ElasticApi
              .termsQuery(OperationOfQuestionElasticsearchFieldNames.operationKind, operationKinds.map(_.value))
          )
        case None =>
          Some(
            ElasticApi.termsQuery(
              OperationOfQuestionElasticsearchFieldNames.operationKind,
              Seq(
                OperationKind.PublicConsultation.value,
                OperationKind.GreatCause.value,
                OperationKind.BusinessConsultation.value
              )
            )
          )
      }
    }
  }

  def buildFeaturedSearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.featured match {
        case Some(FeaturedSearchFilter(featured)) =>
          Some(ElasticApi.termQuery(OperationOfQuestionElasticsearchFieldNames.featured, featured))
        case _ => None
      }
    }
  }

  def buildStatusSearchFilter(operationOfQuestionSearchQuery: OperationOfQuestionSearchQuery): Option[Query] = {
    operationOfQuestionSearchQuery.filters.flatMap {
      _.status match {
        case Some(StatusSearchFilter(status)) =>
          Some(ElasticApi.termQuery(OperationOfQuestionElasticsearchFieldNames.status, status.entryName.toLowerCase))
        case _ => None
      }
    }
  }

}

case class QuestionIdsSearchFilter(questionIds: Seq[QuestionId])
case class QuestionContentSearchFilter(text: String, fuzzy: Option[Fuzziness] = None)
case class DescriptionSearchFilter(description: String)
case class CountrySearchFilter(country: Country)
case class LanguageSearchFilter(language: Language)
case class StartDateSearchFilter(startDate: ZonedDateTime)
case class EndDateSearchFilter(endDate: ZonedDateTime)
case class OperationKindsSearchFilter(operationKinds: Seq[OperationKind])
case class FeaturedSearchFilter(featured: Boolean)
case class StatusSearchFilter(status: OperationOfQuestion.Status)
