package org.make.core.idea

import com.sksamuel.elastic4s.ElasticApi
import com.sksamuel.elastic4s.http.ElasticDsl
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.FieldSortDefinition
import org.elasticsearch.search.sort.SortOrder
import org.make.core.idea.indexed.IdeaElasticsearchFieldNames
import org.make.core.operation.OperationId
import org.make.core.reference.ThemeId

/**
  * The class holding the entire search query
  *
  * @param filters idea of search filters
  * @param limit   number of items to fetch
  * @param skip    number of items to skip
  */
case class IdeaSearchQuery(filters: Option[IdeaSearchFilters] = None,
                           limit: Option[Int] = None,
                           skip: Option[Int] = None,
                           sort: Option[String] = None,
                           order: Option[String] = None,
                           language: Option[String] = None)

/**
  * The class holding the filters
  *
  * @param name        Name to search into idea
  * @param operationId The OperationId to filter
  * @param question    Question to filter
  * @param country     Country to filter
  * @param language    Language to filter
  */
case class IdeaSearchFilters(name: Option[NameSearchFilter] = None,
                             operationId: Option[OperationIdSearchFilter] = None,
                             themeId: Option[ThemeIdSearchFilter] = None,
                             question: Option[QuestionSearchFilter] = None,
                             country: Option[CountrySearchFilter] = None,
                             language: Option[LanguageSearchFilter] = None,
                             status: Option[StatusSearchFilter] = None)

object IdeaSearchFilters extends ElasticDsl {

  def parse(name: Option[NameSearchFilter] = None,
            operationId: Option[OperationIdSearchFilter] = None,
            themeId: Option[ThemeIdSearchFilter] = None,
            question: Option[QuestionSearchFilter] = None,
            country: Option[CountrySearchFilter] = None,
            language: Option[LanguageSearchFilter] = None): Option[IdeaSearchFilters] = {

    (name, operationId, themeId, question, country, language) match {
      case (None, None, None, None, None, None) => None
      case _ =>
        Some(IdeaSearchFilters(name, operationId, themeId, question, country, language))
    }
  }

  /**
    * Build elasticsearch search filters from searchQuery
    *
    * @param ideaSearchQuery search query
    *
    * @return sequence of query definitions
    */
  def getIdeaSearchFilters(ideaSearchQuery: IdeaSearchQuery): Seq[QueryDefinition] =
    Seq(
      buildNameSearchFilter(ideaSearchQuery),
      buildOperationIdSearchFilter(ideaSearchQuery),
      buildThemeIdSearchFilter(ideaSearchQuery),
      buildQuestionSearchFilter(ideaSearchQuery),
      buildCountrySearchFilter(ideaSearchQuery),
      buildLanguageSearchFilter(ideaSearchQuery),
      buildStatusSearchFilter(ideaSearchQuery)
    ).flatten

  def getSkipSearch(ideaSearchQuery: IdeaSearchQuery): Int =
    ideaSearchQuery.skip
      .getOrElse(0)

  def getLimitSearch(ideaSearchQuery: IdeaSearchQuery): Int =
    ideaSearchQuery.limit
      .getOrElse(-1) // TODO get default value from configurations

  def getSort(ideaSearchQuery: IdeaSearchQuery): Option[FieldSortDefinition] = {
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
      FieldSortDefinition(field = sortFieldName, order = order.getOrElse(SortOrder.ASC))
    }
  }

  def buildNameSearchFilter(ideaSearchQuery: IdeaSearchQuery): Option[QueryDefinition] = {
    def languageOmission(boostedLanguage: String): Float =
      if (ideaSearchQuery.language.contains(boostedLanguage)) 1 else 0

    val query: Option[QueryDefinition] = for {
      filters                            <- ideaSearchQuery.filters
      NameSearchFilter(text, maybeFuzzy) <- filters.name
    } yield {
      val fieldsBoosts = Map(
        IdeaElasticsearchFieldNames.name -> 3F,
        IdeaElasticsearchFieldNames.nameFr -> 2F * languageOmission("fr"),
        IdeaElasticsearchFieldNames.nameEn -> 2F * languageOmission("en"),
        IdeaElasticsearchFieldNames.nameIt -> 2F * languageOmission("it"),
        IdeaElasticsearchFieldNames.nameGeneral -> 1F
      ).filter { case (_, boost) => boost != 0 }
      maybeFuzzy match {
        case Some(fuzzy) =>
          ElasticApi
            .should(
              multiMatchQuery(text)
                .fields(fieldsBoosts)
                .boost(2F),
              multiMatchQuery(text)
                .fields(fieldsBoosts)
                .fuzziness(fuzzy)
                .boost(1F)
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

  def buildOperationIdSearchFilter(ideaSearchQuery: IdeaSearchQuery): Option[QueryDefinition] = {
    ideaSearchQuery.filters.flatMap {
      _.operationId match {
        case Some(OperationIdSearchFilter(operationId)) =>
          Some(ElasticApi.termQuery(IdeaElasticsearchFieldNames.operationId, operationId.value))
        case _ => None
      }
    }
  }

  def buildThemeIdSearchFilter(ideaSearchQuery: IdeaSearchQuery): Option[QueryDefinition] = {
    ideaSearchQuery.filters.flatMap {
      _.themeId match {
        case Some(ThemeIdSearchFilter(themeId)) =>
          Some(ElasticApi.termQuery(IdeaElasticsearchFieldNames.themeId, themeId.value))
        case _ => None
      }
    }
  }

  def buildQuestionSearchFilter(ideaSearchQuery: IdeaSearchQuery): Option[QueryDefinition] = {
    ideaSearchQuery.filters.flatMap {
      _.question match {
        case Some(QuestionSearchFilter(question)) =>
          Some(ElasticApi.termQuery(IdeaElasticsearchFieldNames.question, question))
        case _ => None
      }
    }
  }

  def buildCountrySearchFilter(ideaSearchQuery: IdeaSearchQuery): Option[QueryDefinition] = {
    ideaSearchQuery.filters.flatMap {
      _.country match {
        case Some(CountrySearchFilter(country)) =>
          Some(ElasticApi.termsQuery(IdeaElasticsearchFieldNames.country, country))
        case _ => None
      }
    }
  }

  def buildLanguageSearchFilter(ideaSearchQuery: IdeaSearchQuery): Option[QueryDefinition] = {
    ideaSearchQuery.filters.flatMap {
      _.language match {
        case Some(LanguageSearchFilter(language)) =>
          Some(ElasticApi.termsQuery(IdeaElasticsearchFieldNames.language, language))
        case _ => None
      }
    }
  }

  def buildStatusSearchFilter(ideaSearchQuery: IdeaSearchQuery): Option[QueryDefinition] = {
    val query: Option[QueryDefinition] = ideaSearchQuery.filters.flatMap {
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

case class NameSearchFilter(text: String, fuzzy: Option[String] = None)

case class OperationIdSearchFilter(operationId: OperationId)
case class ThemeIdSearchFilter(themeId: ThemeId)

case class QuestionSearchFilter(question: String)
case class CountrySearchFilter(country: String)
case class LanguageSearchFilter(language: String)

case class StatusSearchFilter(status: Seq[IdeaStatus])

case class ContextSearchFilter(operation: Option[OperationId] = None,
                               source: Option[String] = None,
                               location: Option[String] = None,
                               question: Option[String] = None)

case class SlugSearchFilter(slug: String)

case class IdeaSearchFilter(ideaId: IdeaId)

case class Limit(value: Int)

case class Skip(value: Int)
