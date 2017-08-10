package org.make.api.technical.elasticsearch

import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.FieldSortDefinition
import io.circe.generic.auto._
import io.circe.parser._
import org.elasticsearch.search.sort.SortOrder

trait SearchQueryBuilderComponent {

  def searchQueryBuilder: SearchQueryBuilder

  class SearchQueryBuilder {

    /**
      * Build elasticsearch search query from query in json string format
      * @param jsonString json string representing the query
      */
    def buildSearchQueryFromJson(jsonString: String): SearchQuery = {
      parse(jsonString).flatMap(_.as[SearchQuery]) match {
        case Right(searchQuery) => searchQuery
        case Left(failure) =>
          throw new IllegalArgumentException(failure.toString)
      }
    }

    /**
      * Build elasticsearch search filters from searchQuery
      * @param searchQuery search query
      * @return sequence of query definitions
      */
    def getSearchFilters(searchQuery: SearchQuery): Seq[QueryDefinition] =
      Seq(
        SearchFilterBuilder.buildThemeSearchFilter(searchQuery.filter),
        SearchFilterBuilder.buildTagSearchFilter(searchQuery.filter),
        SearchFilterBuilder.buildContentSearchFilter(searchQuery.filter)
      ).flatten

    def getSortOption(searchQuery: SearchQuery): Seq[FieldSortDefinition] =
      searchQuery.options
        .flatMap(_.sort)
        .map(sort => Seq(FieldSortDefinition(field = sort.field, order = SortOrderBuilder(sort.mode))))
        .getOrElse(Seq.empty)

    def getSkipSearchOption(searchQuery: SearchQuery): Int =
      searchQuery.options
        .flatMap(_.skip)
        .map(skip => skip.value)
        .getOrElse(0)

    def getLimitSearchOption(searchQuery: SearchQuery): Int =
      searchQuery.options
        .flatMap(_.limit)
        .map(limit => limit.value)
        .getOrElse(10) // TODO get default value from configurations
  }
}

object SearchFilterBuilder {

  def buildThemeSearchFilter(filter: SearchFilter): Option[QueryDefinition] =
    filter.theme match {
      case Some(ThemeSearchFilter(id)) =>
        if (id.length == 1) {
          Some(termQuery(ProposalElasticsearchFieldNames.themeId, id.head))
        } else {
          Some(termsQuery(ProposalElasticsearchFieldNames.themeId, id))
        }
      case _ => None
    }

  def buildTagSearchFilter(filter: SearchFilter): Option[QueryDefinition] =
    filter.theme match {
      case Some(ThemeSearchFilter(id)) =>
        if (id.length == 1) {
          Some(termQuery(ProposalElasticsearchFieldNames.tagId, id.head))
        } else {
          Some(termsQuery(ProposalElasticsearchFieldNames.tagId, id))
        }
      case _ => None
    }

  def buildContentSearchFilter(filter: SearchFilter): Option[QueryDefinition] =
    // TODO complete fuzzy search
    filter.content match {
      case Some(ContentSearchFilter(text, _)) =>
        Some(matchQuery(ProposalElasticsearchFieldNames.content, text))
      case _ => None
    }

}

object SortOrderBuilder {
  def apply(maybeMode: Option[String]): SortOrder = maybeMode match {
    case Some(mode) if mode.toLowerCase == "asc"  => SortOrder.ASC
    case Some(mode) if mode.toLowerCase == "desc" => SortOrder.DESC
    case None                                     => SortOrder.ASC
    case Some(mode) =>
      throw new IllegalArgumentException(s"sort mode $mode not supported")
  }
}
