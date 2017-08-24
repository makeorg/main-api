package org.make.core.proposal

import com.sksamuel.elastic4s.ElasticApi
import com.sksamuel.elastic4s.http.ElasticDsl
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.FieldSortDefinition
import org.elasticsearch.search.sort.SortOrder
import org.make.core.Validation.{validate, validateField}

/**
  * The class holding the entire search query
  *
  * @param filter  sequence of search filters
  * @param options sequence of search options
  */
case class SearchQuery(filter: SearchFilter, options: Option[SearchOptions])

case class SearchFilter(theme: Option[ThemeSearchFilter] = None,
                        tag: Option[TagSearchFilter] = None,
                        content: Option[ContentSearchFilter] = None)

object SearchFilter extends ElasticDsl {

  /**
    * Build elasticsearch search filters from searchQuery
    * @param searchQuery search query
    * @return sequence of query definitions
    */
  def getSearchFilters(searchQuery: SearchQuery): Seq[QueryDefinition] =
    Seq(
      buildThemeSearchFilter(searchQuery.filter),
      buildTagSearchFilter(searchQuery.filter),
      buildContentSearchFilter(searchQuery.filter)
    ).flatten

  def getSortOption(searchQuery: SearchQuery): Seq[FieldSortDefinition] =
    searchQuery.options
      .flatMap(_.sort)
      .map(sort => Seq(FieldSortDefinition(field = sort.field, order = SortOrderHelper(sort.mode))))
      .getOrElse(Seq.empty)

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

  def buildThemeSearchFilter(filter: SearchFilter): Option[QueryDefinition] =
    filter.theme match {
      case Some(ThemeSearchFilter(Seq(themeId))) =>
        Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.themeId, themeId))
      case Some(ThemeSearchFilter(themes)) =>
        Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.themeId, themes))
      case _ => None
    }

  def buildTagSearchFilter(filter: SearchFilter): Option[QueryDefinition] =
    filter.tag match {
      case Some(TagSearchFilter(Seq(themeId))) =>
        Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.tagId, themeId))
      case Some(TagSearchFilter(themes)) =>
        Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.tagId, themes))
      case _ => None
    }

  def buildContentSearchFilter(filter: SearchFilter): Option[QueryDefinition] =
    // TODO complete fuzzy search
    filter.content match {
      case Some(ContentSearchFilter(text, fuzziness)) =>
        Some(ElasticApi.matchQuery(ProposalElasticsearchFieldNames.content, text))
      case _ => None
    }
}

case class ThemeSearchFilter(id: Seq[String]) {
  validate(validateField("id", id.nonEmpty, "ids cannot be empty in theme search filters"))
}

case class TagSearchFilter(id: Seq[String]) {
  validate(validateField("id", id.nonEmpty, "ids cannot be empty in tag search filters"))
}

case class ContentSearchFilter(text: String, fuzzy: Option[Int] = None)

/**
  * Search option that allows modifying the search response
  * @param sort sorting results by a field and a mode
  * @param limit limiting the number of search responses
  * @param skip skipping a number of results, used for pagination
  */
case class SearchOptions(sort: Option[SortOption], limit: Option[LimitOption], skip: Option[SkipOption])

case class SortOption(field: String, mode: Option[String]) {}

object SortOrderHelper {
  def apply(maybeMode: Option[String]): SortOrder = maybeMode match {
    case Some(mode) if mode.toLowerCase == "asc"  => SortOrder.ASC
    case Some(mode) if mode.toLowerCase == "desc" => SortOrder.DESC
    case None                                     => SortOrder.ASC
    case Some(mode) =>
      throw new IllegalArgumentException(s"sort mode $mode not supported")
  }
}

case class LimitOption(value: Int)

case class SkipOption(value: Int)
