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
  * @param filter  sequence of search filters
  * @param options sequence of search options
  */
case class SearchQuery(filter: Option[SearchFilter] = None, options: Option[SearchOptions] = None)

case class SearchFilter(theme: Option[ThemeSearchFilter] = None,
                        tag: Option[TagSearchFilter] = None,
                        content: Option[ContentSearchFilter] = None,
                        status: Option[StatusSearchFilter] = None)

object SearchFilter extends ElasticDsl {

  def parseSearchFilter(theme: Option[ThemeSearchFilter] = None,
                        tag: Option[TagSearchFilter] = None,
                        content: Option[ContentSearchFilter] = None,
                        status: Option[StatusSearchFilter] = None): Option[SearchFilter] =
    if (Seq(theme, tag, content, status).exists(_.isDefined)) {
      Some(SearchFilter(theme, tag, content, status))
    } else { None }

  /**
    * Build elasticsearch search filters from searchQuery
    *
    * @param searchQuery search query
    * @return sequence of query definitions
    */
  def getSearchFilters(searchQuery: SearchQuery): Seq[QueryDefinition] =
    Seq(
      buildThemeSearchFilter(searchQuery.filter),
      buildTagSearchFilter(searchQuery.filter),
      Some(buildContentSearchFilter(searchQuery.filter)),
      Some(buildStatusSearchFilter(searchQuery.filter))
    ).flatten

  def getSortOption(searchQuery: SearchQuery): Seq[FieldSortDefinition] =
    searchQuery.options
      .map(_.sort)
      .getOrElse(Seq.empty)
      .map(sort => FieldSortDefinition(field = sort.field, order = sort.mode.getOrElse(SortOrder.ASC)))

  def getSkipSearchOption(searchQuery: SearchQuery): Int =
    searchQuery.options
      .flatMap(_.skip)
      .map(_.value)
      .getOrElse(0)

  def getLimitSearchOption(searchQuery: SearchQuery): Int =
    searchQuery.options
      .flatMap(_.limit)
      .map(_.value)
      .getOrElse(10) // TODO get default value from configurations

  def buildThemeSearchFilter(maybeFilter: Option[SearchFilter]): Option[QueryDefinition] = maybeFilter.flatMap {
    _.theme match {
      case Some(ThemeSearchFilter(Seq(themeId))) =>
        Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.themeId, themeId))
      case Some(ThemeSearchFilter(themes)) =>
        Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.themeId, themes))
      case _ => None
    }
  }

  def buildTagSearchFilter(maybeFilter: Option[SearchFilter]): Option[QueryDefinition] = maybeFilter.flatMap {
    _.tag match {
      case Some(TagSearchFilter(Seq(themeId))) =>
        Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.tagId, themeId))
      case Some(TagSearchFilter(themes)) =>
        Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.tagId, themes))
      case _ => None
    }
  }

  /*
   * TODO complete fuzzy search. potential hint:
   * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#_fuzziness
   */
  def buildContentSearchFilter(maybeFilter: Option[SearchFilter]): QueryDefinition = {
    val query: Option[QueryDefinition] = for {
      filter                               <- maybeFilter
      ContentSearchFilter(text, fuzziness) <- filter.content
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.content, text)
    query.getOrElse(ElasticApi.matchAllQuery)
  }

  def buildStatusSearchFilter(maybeFilter: Option[SearchFilter]): QueryDefinition = {
    val query: Option[QueryDefinition] = maybeFilter.flatMap {
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
    query.getOrElse(ElasticApi.matchQuery(ProposalElasticsearchFieldNames.status, ProposalStatus.Accepted.shortName))
  }
}

case class ThemeSearchFilter(id: Seq[String]) {
  validate(validateField("id", id.nonEmpty, "ids cannot be empty in theme search filters"))
}

case class TagSearchFilter(id: Seq[String]) {
  validate(validateField("id", id.nonEmpty, "ids cannot be empty in tag search filters"))
}

case class ContentSearchFilter(text: String, fuzzy: Option[Int] = None)

case class StatusSearchFilter(status: ProposalStatus)

/**
  * Search option that allows modifying the search response
  * @param sort sorting results by a field and a mode
  * @param limit limiting the number of search responses
  * @param skip skipping a number of results, used for pagination
  */
case class SearchOptions(sort: Seq[SortOption], limit: Option[LimitOption], skip: Option[SkipOption])

object SearchOptions {
  def parseSearchOptions(sort: Seq[SortOption],
                         limit: Option[LimitOption],
                         skip: Option[SkipOption]): Option[SearchOptions] =
    if (Seq(sort.headOption, limit, skip).exists(_.isDefined)) {
      Some(SearchOptions(sort, limit, skip))
    } else { None }
}

case class SortOption(field: String, mode: Option[SortOrder])

case class LimitOption(value: Int)

case class SkipOption(value: Int)
