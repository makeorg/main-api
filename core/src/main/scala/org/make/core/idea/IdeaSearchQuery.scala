package org.make.core.idea

import com.sksamuel.elastic4s.ElasticApi
import com.sksamuel.elastic4s.http.ElasticDsl
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.FieldSortDefinition
import org.elasticsearch.search.sort.SortOrder
import org.make.core.common.indexed.{Sort => IndexedSort}
import org.make.core.idea.indexed.IdeaElasticsearchFieldNames
import org.make.core.operation.OperationId

/**
  * The class holding the entire search query
  *
  * @param filters idea of search filters
  * @param sorts   idea of sorts options
  * @param limit   number of items to fetch
  * @param skip    number of items to skip
  */
case class IdeaSearchQuery(filters: Option[IdeaSearchFilters] = None,
                       sorts: Seq[IndexedSort] = Seq.empty,
                       limit: Option[Int] = None,
                       skip: Option[Int] = None)

/**
  * The class holding the filters
  *
  * @param name        Name to search into idea
  * @param operationId The OperationId to filter
  * @param question    Question to filter
  * @param country     Country to filter
  * @param language    Language to filter
  * @param status      The Status of idea
  */
case class IdeaSearchFilters(name: Option[NameSearchFilter] = None,
                         operationId: Option[OperationIdSearchFilter] = None,
                         question: Option[QuestionSearchFilter] = None,
                         country: Option[CountrySearchFilter] = None,
                         language: Option[LanguageSearchFilter] = None,
                         status: Option[StatusSearchFilter] = None)

object IdeaSearchFilters extends ElasticDsl {

  def parse(name: Option[NameSearchFilter] = None,
            operationId: Option[OperationIdSearchFilter] = None,
            question: Option[QuestionSearchFilter] = None,
            country: Option[CountrySearchFilter] = None,
            language: Option[LanguageSearchFilter] = None,
            status: Option[StatusSearchFilter] = None): Option[IdeaSearchFilters] = {

    (name, operationId, question, country, language, status) match {
      case (None, None, None, None, None, None) => None
      case _ =>
        Some(IdeaSearchFilters(name, operationId, question, country, language, status))
    }
  }

  /**
    * Build elasticsearch search filters from searchQuery
    *
    * @param IdeaSearchQuery search query
    * @return sequence of query definitions
    */
  def getIdeaSearchFilters(ideaSearchQuery: IdeaSearchQuery): Seq[QueryDefinition] =
    Seq(
      buildNameSearchFilter(ideaSearchQuery),
      buildOperationIdSearchFilter(ideaSearchQuery),
      buildQuestionSearchFilter(ideaSearchQuery),
      buildCountrySearchFilter(ideaSearchQuery),
      buildLanguageSearchFilter(ideaSearchQuery),
      buildStatusSearchFilter(ideaSearchQuery)
    ).flatten

  def getSort(ideaSearchQuery: IdeaSearchQuery): Seq[FieldSortDefinition] =
    ideaSearchQuery.sorts
      .map(sort => FieldSortDefinition(field = sort.field.getOrElse("createdAt"), order = sort.mode.getOrElse(SortOrder.ASC)))

  def getSkipSearch(ideaSearchQuery: IdeaSearchQuery): Int =
    ideaSearchQuery.skip
      .getOrElse(0)

  def getLimitSearch(ideaSearchQuery: IdeaSearchQuery): Int =
    ideaSearchQuery.limit
      .getOrElse(10) // TODO get default value from configurations

  def buildNameSearchFilter(ideaSearchQuery: IdeaSearchQuery): Option[QueryDefinition] = {

    val query: Option[QueryDefinition] = for {
      filters                               <- ideaSearchQuery.filters
      NameSearchFilter(text, maybeFuzzy) <- filters.name
    } yield {
      maybeFuzzy match {
        case Some(fuzzy) =>
          ElasticApi
            .should(
              multiMatchQuery(text)
                .fields(
                  Map(
                    IdeaElasticsearchFieldNames.name -> 2F,
                    IdeaElasticsearchFieldNames.nameStemmed -> 1F
                  )
                )
                .boost(2F),
              multiMatchQuery(text)
                .fields(
                  Map(
                    IdeaElasticsearchFieldNames.name -> 2F,
                    IdeaElasticsearchFieldNames.nameStemmed -> 1F
                  )
                )
                .fuzziness(fuzzy)
                .boost(1F)
            )
        case None =>
          ElasticApi
            .multiMatchQuery(text)
            .fields(
              Map(IdeaElasticsearchFieldNames.name -> 2F, IdeaElasticsearchFieldNames.nameStemmed -> 1F)
            )
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
      }
    }

    query match {
      case None => None
      case _ => query
    }
  }
}

case class NameSearchFilter(text: String, fuzzy: Option[String] = None)

case class OperationIdSearchFilter(operationId: OperationId)

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
