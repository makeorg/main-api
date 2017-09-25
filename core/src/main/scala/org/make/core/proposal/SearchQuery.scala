package org.make.core.proposal

import com.sksamuel.elastic4s.ElasticApi
import com.sksamuel.elastic4s.http.ElasticDsl
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.FieldSortDefinition
import org.elasticsearch.search.sort.SortOrder
import org.make.core.Validation.{validate, validateField}
import org.make.core.proposal.indexed.ProposalElasticsearchFieldNames

/**
  * The class holding the entire search query
  *
  * @param filters sequence of search filters
  * @param sorts   sequence of sorts options
  * @param limit   number of items to fetch
  * @param skip    number of items to skip
  */
case class SearchQuery(filters: Option[SearchFilters] = None,
                       sorts: Seq[Sort] = Seq.empty,
                       limit: Option[Int] = None,
                       skip: Option[Int] = None)

/**
  * The class holding the filters
  *
  * @param theme   The Theme to filter
  * @param tags    List of Tags to filters
  * @param labels  List of Tags to filters
  * @param content Text to search into the proposal
  * @param status  The Status of proposal
  */
case class SearchFilters(theme: Option[ThemeSearchFilter] = None,
                         tags: Option[TagsSearchFilter] = None,
                         labels: Option[LabelsSearchFilter] = None,
                         content: Option[ContentSearchFilter] = None,
                         status: Option[StatusSearchFilter] = None,
                         context: Option[ContextSearchFilter] = None)

object SearchFilters extends ElasticDsl {

  def parse(theme: Option[ThemeSearchFilter] = None,
            tags: Option[TagsSearchFilter] = None,
            labels: Option[LabelsSearchFilter] = None,
            content: Option[ContentSearchFilter] = None,
            status: Option[StatusSearchFilter] = None,
            context: Option[ContextSearchFilter] = None): Option[SearchFilters] = {

    (theme, tags, labels, content, status, context) match {
      case (None, None, None, None, None, None) => None
      case _                                    => Some(SearchFilters(theme, tags, labels, content, status, context))
    }
  }

  /**
    * Build elasticsearch search filters from searchQuery
    *
    * @param searchQuery search query
    * @return sequence of query definitions
    */
  def getSearchFilters(searchQuery: SearchQuery): Seq[QueryDefinition] =
    Seq(
      buildThemeSearchFilter(searchQuery),
      buildTagsSearchFilter(searchQuery),
      buildLabelsSearchFilter(searchQuery),
      buildContentSearchFilter(searchQuery),
      buildStatusSearchFilter(searchQuery),
      buildContextOperationSearchFilter(searchQuery),
      buildContextSourceSearchFilter(searchQuery),
      buildContextLocationSearchFilter(searchQuery),
      buildContextQuestionSearchFilter(searchQuery)
    ).flatten

  def getSort(searchQuery: SearchQuery): Seq[FieldSortDefinition] =
    searchQuery.sorts
      .map(sort => FieldSortDefinition(field = sort.field.get, order = sort.mode.getOrElse(SortOrder.ASC)))

  def getSkipSearch(searchQuery: SearchQuery): Int =
    searchQuery.skip
      .getOrElse(0)

  def getLimitSearch(searchQuery: SearchQuery): Int =
    searchQuery.limit
      .getOrElse(10) // TODO get default value from configurations

  def buildThemeSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    searchQuery.filters.flatMap {
      _.theme match {
        case Some(ThemeSearchFilter(Seq(themeId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.themeId, themeId))
        case Some(ThemeSearchFilter(themeId)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.themeId, themeId))
        case _ => None
      }
    }
  }

  def buildTagsSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    searchQuery.filters.flatMap {
      _.tags match {
        case Some(TagsSearchFilter(Seq(tagId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.tagId, tagId))
        case Some(TagsSearchFilter(tags)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.tags, tags))
        case _ => None
      }
    }
  }

  def buildLabelsSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    searchQuery.filters.flatMap {
      _.labels match {
        case Some(LabelsSearchFilter(Seq(labelId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.labelId, labelId))
        case Some(LabelsSearchFilter(labels)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.labels, labels))
        case _ => None
      }
    }
  }

  def buildContextOperationSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    val operationFilter: Option[QueryDefinition] = for {
      filters   <- searchQuery.filters
      context   <- filters.context
      operation <- context.operation
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.contextOperation, operation)

    operationFilter
  }

  def buildContextSourceSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    val sourceFilter: Option[QueryDefinition] = for {
      filters <- searchQuery.filters
      context <- filters.context
      source  <- context.source
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.contextSource, source)

    sourceFilter
  }

  def buildContextLocationSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    val locationFilter: Option[QueryDefinition] = for {
      filters  <- searchQuery.filters
      context  <- filters.context
      location <- context.location
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.contextLocation, location)

    locationFilter
  }

  def buildContextQuestionSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    val questionFilter: Option[QueryDefinition] = for {
      filters  <- searchQuery.filters
      context  <- filters.context
      question <- context.question
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.contextQuestion, question)

    questionFilter
  }

  /*
   * TODO complete fuzzy search. potential hint:
   * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#_fuzziness
   */
  def buildContentSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {

    val query: Option[QueryDefinition] = for {
      filters                          <- searchQuery.filters
      ContentSearchFilter(text, fuzzy) <- filters.content
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.content, text)

    query match {
      case None => Some(ElasticApi.matchAllQuery)
      case _    => query
    }
  }

  def buildStatusSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    val query: Option[QueryDefinition] = searchQuery.filters.flatMap {
      _.status.map {
        case StatusSearchFilter(ProposalStatus.Pending) =>
          ElasticApi.matchQuery(ProposalElasticsearchFieldNames.status, ProposalStatus.Pending.shortName)
        case StatusSearchFilter(ProposalStatus.Refused) =>
          ElasticApi.matchQuery(ProposalElasticsearchFieldNames.status, ProposalStatus.Refused.shortName)
        case StatusSearchFilter(ProposalStatus.Archived) =>
          ElasticApi.matchQuery(ProposalElasticsearchFieldNames.status, ProposalStatus.Archived.shortName)
        case StatusSearchFilter(ProposalStatus.Accepted) =>
          ElasticApi.matchQuery(ProposalElasticsearchFieldNames.status, ProposalStatus.Accepted.shortName)
      }
    }

    query match {
      case None =>
        Some(ElasticApi.matchQuery(ProposalElasticsearchFieldNames.status, ProposalStatus.Accepted.shortName))
      case _ => query
    }
  }
}

case class ThemeSearchFilter(themeIds: Seq[String]) {
  validate(validateField("ThemeId", themeIds.nonEmpty, "ids cannot be empty in theme search filters"))
}

case class TagsSearchFilter(tagIds: Seq[String]) {
  validate(validateField("tagId", tagIds.nonEmpty, "ids cannot be empty in tag search filters"))
}

case class LabelsSearchFilter(labelIds: Seq[String]) {
  validate(validateField("labelId", labelIds.nonEmpty, "ids cannot be empty in label search filters"))
}

case class ContentSearchFilter(text: String, fuzzy: Option[Int] = None)

case class StatusSearchFilter(status: ProposalStatus)

case class ContextSearchFilter(operation: Option[String],
                               source: Option[String],
                               location: Option[String],
                               question: Option[String])

case class Sort(field: Option[String], mode: Option[SortOrder])

case class Limit(value: Int)

case class Skip(value: Int)
