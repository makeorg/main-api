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

case class SearchFilters(theme: Option[ThemeSearchFilter] = None,
                         tag: Option[TagSearchFilter] = None,
                         content: Option[ContentSearchFilter] = None,
                         status: Option[StatusSearchFilter] = None)

object SearchFilters extends ElasticDsl {

  def parse(theme: Option[ThemeSearchFilter] = None,
            tag: Option[TagSearchFilter] = None,
            content: Option[ContentSearchFilter] = None,
            status: Option[StatusSearchFilter] = None): Option[SearchFilters] = {
    if (Seq(theme, tag, content, status).exists(_.isDefined)) {
      Some(SearchFilters(theme, tag, content, status))
    } else {
      None
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
      buildTagSearchFilter(searchQuery),
      Some(buildContentSearchFilter(searchQuery)),
      Some(buildStatusSearchFilter(searchQuery))
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
        case Some(ThemeSearchFilter(themes)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.themeId, themes))
        case _ => None
      }
    }
  }

  def buildTagSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    searchQuery.filters.flatMap {
      _.tag match {
        case Some(TagSearchFilter(Seq(themeId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.tagId, themeId))
        case Some(TagSearchFilter(themes)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.tagId, themes))
        case _ => None
      }
    }
  }

  /*
   * TODO complete fuzzy search. potential hint:
   * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#_fuzziness
   */
  def buildContentSearchFilter(searchQuery: SearchQuery): QueryDefinition = {

    val query: Option[QueryDefinition] = for {
      filters                              <- searchQuery.filters
      ContentSearchFilter(text, fuzziness) <- filters.content
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.content, text)
    query.getOrElse(ElasticApi.matchAllQuery)
  }

  def buildStatusSearchFilter(searchQuery: SearchQuery): QueryDefinition = {
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
    query.getOrElse(ElasticApi.matchQuery(ProposalElasticsearchFieldNames.status, ProposalStatus.Accepted.shortName))
  }
}

case class ThemeSearchFilter(themeIds: Seq[String]) {
  validate(validateField("id", themeIds.nonEmpty, "ids cannot be empty in theme search filters"))
}

case class TagSearchFilter(tagIds: Seq[String]) {
  validate(validateField("id", tagIds.nonEmpty, "ids cannot be empty in tag search filters"))
}

case class ContentSearchFilter(text: String, fuzzy: Option[Int] = None)

case class StatusSearchFilter(status: ProposalStatus)

case class Sort(field: Option[String], mode: Option[SortOrder])

case class Limit(value: Int)

case class Skip(value: Int)
