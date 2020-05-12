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

package org.make.core.idea

import com.sksamuel.elastic4s.ElasticApi
import com.sksamuel.elastic4s.http.ElasticDsl
import com.sksamuel.elastic4s.searches.queries.Query
import com.sksamuel.elastic4s.searches.sort.{FieldSort, SortOrder}
import com.sksamuel.elastic4s.searches.suggestion.Fuzziness
import org.make.core.idea.indexed.IdeaElasticsearchFieldNames
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}

/**
  * The class holding the entire search query
  *
  * @param filters idea of search filters
  * @param limit   number of items to fetch
  * @param skip    number of items to skip
  */
case class IdeaSearchQuery(
  filters: Option[IdeaSearchFilters] = None,
  limit: Option[Int] = None,
  skip: Option[Int] = None,
  sort: Option[String] = None,
  order: Option[String] = None,
  language: Option[Language] = None
)

/**
  * The class holding the filters
  *
  * @param name        Name to search into idea
  * @param questionId  The questionId to filter
  */
case class IdeaSearchFilters(
  name: Option[NameSearchFilter] = None,
  questionId: Option[QuestionIdSearchFilter] = None,
  status: Option[StatusSearchFilter] = None
)

object IdeaSearchFilters extends ElasticDsl {

  def parse(
    name: Option[NameSearchFilter] = None,
    questionId: Option[QuestionIdSearchFilter] = None
  ): Option[IdeaSearchFilters] = {

    (name, questionId) match {
      case (None, None) => None
      case _ =>
        Some(IdeaSearchFilters(name, questionId))
    }
  }

  /**
    * Build elasticsearch search filters from searchQuery
    *
    * @param ideaSearchQuery search query
    *
    * @return sequence of query definitions
    */
  def getIdeaSearchFilters(ideaSearchQuery: IdeaSearchQuery): Seq[Query] =
    Seq(
      buildNameSearchFilter(ideaSearchQuery),
      buildQuestionIdSearchFilter(ideaSearchQuery),
      buildStatusSearchFilter(ideaSearchQuery)
    ).flatten

  def getSkipSearch(ideaSearchQuery: IdeaSearchQuery): Int =
    ideaSearchQuery.skip
      .getOrElse(0)

  def getLimitSearch(ideaSearchQuery: IdeaSearchQuery): Int =
    ideaSearchQuery.limit
      .getOrElse(-1) // TODO get default value from configurations

  def getSort(ideaSearchQuery: IdeaSearchQuery): Option[FieldSort] = {
    val order = ideaSearchQuery.order.map {
      case asc if asc.toLowerCase == "asc"    => SortOrder.ASC
      case desc if desc.toLowerCase == "desc" => SortOrder.DESC
    }

    ideaSearchQuery.sort.map { sort =>
      val sortFieldName: String = if (sort == "name") {
        "name.keyword"
      } else {
        sort
      }
      FieldSort(field = sortFieldName, order = order.getOrElse(SortOrder.ASC))
    }
  }

  def buildNameSearchFilter(ideaSearchQuery: IdeaSearchQuery): Option[Query] = {
    def languageOmission(boostedLanguage: String): Double =
      if (ideaSearchQuery.language.contains(Language(boostedLanguage))) 1 else 0

    val query = for {
      filters                            <- ideaSearchQuery.filters
      NameSearchFilter(text, maybeFuzzy) <- filters.name
    } yield {
      val fieldsBoosts: Map[String, Double] = Map(
        IdeaElasticsearchFieldNames.name -> 3d,
        IdeaElasticsearchFieldNames.nameFr -> 2d * languageOmission("fr"),
        IdeaElasticsearchFieldNames.nameEn -> 2d * languageOmission("en"),
        IdeaElasticsearchFieldNames.nameIt -> 2d * languageOmission("it"),
        IdeaElasticsearchFieldNames.nameDe -> 2d * languageOmission("de"),
        IdeaElasticsearchFieldNames.nameBg -> 2d * languageOmission("bg"),
        IdeaElasticsearchFieldNames.nameCs -> 2d * languageOmission("cs"),
        IdeaElasticsearchFieldNames.nameDa -> 2d * languageOmission("da"),
        IdeaElasticsearchFieldNames.nameNl -> 2d * languageOmission("nl"),
        IdeaElasticsearchFieldNames.nameFi -> 2d * languageOmission("fi"),
        IdeaElasticsearchFieldNames.nameEl -> 2d * languageOmission("el"),
        IdeaElasticsearchFieldNames.nameHu -> 2d * languageOmission("hu"),
        IdeaElasticsearchFieldNames.nameLv -> 2d * languageOmission("lv"),
        IdeaElasticsearchFieldNames.nameLt -> 2d * languageOmission("lt"),
        IdeaElasticsearchFieldNames.namePt -> 2d * languageOmission("pt"),
        IdeaElasticsearchFieldNames.nameRo -> 2d * languageOmission("ro"),
        IdeaElasticsearchFieldNames.nameEs -> 2d * languageOmission("es"),
        IdeaElasticsearchFieldNames.nameSv -> 2d * languageOmission("sv"),
        IdeaElasticsearchFieldNames.nameHr -> 2d * languageOmission("hr"),
        IdeaElasticsearchFieldNames.nameEt -> 2d * languageOmission("et"),
        IdeaElasticsearchFieldNames.nameMt -> 2d * languageOmission("mt"),
        IdeaElasticsearchFieldNames.nameSk -> 2d * languageOmission("sk"),
        IdeaElasticsearchFieldNames.nameSl -> 2d * languageOmission("sl"),
        IdeaElasticsearchFieldNames.nameGeneral -> 1d
      ).filter { case (_, boost) => boost != 0 }
      maybeFuzzy match {
        case Some(fuzzy) =>
          ElasticApi
            .should(
              multiMatchQuery(text)
                .fields(fieldsBoosts)
                .boost(2f),
              multiMatchQuery(text)
                .fields(fieldsBoosts)
                .fuzziness(fuzzy)
                .boost(1f)
            )
        case None =>
          ElasticApi
            .multiMatchQuery(text)
            .fields(fieldsBoosts)
      }
    }

    query match {
      case None => None
      case _    => query
    }
  }

  def buildQuestionIdSearchFilter(ideaSearchQuery: IdeaSearchQuery): Option[Query] = {
    ideaSearchQuery.filters.flatMap {
      _.questionId match {
        case Some(QuestionIdSearchFilter(questionId)) =>
          Some(ElasticApi.termQuery(IdeaElasticsearchFieldNames.questionId, questionId.value))
        case _ => None
      }
    }
  }

  def buildStatusSearchFilter(ideaSearchQuery: IdeaSearchQuery): Option[Query] = {
    val query: Option[Query] = ideaSearchQuery.filters.flatMap {
      _.status.map {
        case StatusSearchFilter(Seq(status)) =>
          ElasticApi.termQuery(IdeaElasticsearchFieldNames.status, status.shortName)
        case StatusSearchFilter(status) =>
          ElasticApi.termsQuery(IdeaElasticsearchFieldNames.status, status.map(_.shortName))
        case _ =>
          ElasticApi.termsQuery(IdeaElasticsearchFieldNames.status, IdeaStatus.Activated.shortName)
      }
    }

    query match {
      case None => None
      case _    => query
    }
  }

}

case class NameSearchFilter(text: String, fuzzy: Option[Fuzziness] = None)
case class QuestionIdSearchFilter(questionId: QuestionId)
case class OperationIdSearchFilter(operationId: OperationId)
case class QuestionSearchFilter(question: String)
case class CountrySearchFilter(country: Country)
case class LanguageSearchFilter(language: Language)
case class StatusSearchFilter(status: Seq[IdeaStatus])
case class ContextSearchFilter(
  operation: Option[OperationId] = None,
  source: Option[String] = None,
  location: Option[String] = None,
  question: Option[String] = None
)
case class SlugSearchFilter(slug: String)
case class IdeaSearchFilter(ideaId: IdeaId)
case class Limit(value: Int)

case class Skip(value: Int)
