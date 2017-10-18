package org.make.core.sequence

import com.sksamuel.elastic4s.ElasticApi
import com.sksamuel.elastic4s.http.ElasticDsl
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.FieldSortDefinition
import org.elasticsearch.search.sort.SortOrder
import org.make.core.common.indexed.Sort
import org.make.core.reference.{TagId, ThemeId}
import org.make.core.sequence.indexed.SequenceElasticsearchFieldNames
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

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

object SearchQuery {
  implicit val searchQueryFormatted: RootJsonFormat[SearchQuery] =
    DefaultJsonProtocol.jsonFormat4(SearchQuery.apply)
}

// toDo: manage translation search on title and slug
/**
  * The class holding the filters
  *
  * @param tags    List of Tags to filters
  * @param themes  The Theme to filter
  * @param title   Text to search into the sequence
  * @param status  The Status of sequence
  * @param context The Context of sequence
  */
case class SearchFilters(tags: Option[TagsSearchFilter] = None,
                         themes: Option[ThemesSearchFilter] = None,
                         title: Option[TitleSearchFilter] = None,
                         slug: Option[SlugSearchFilter] = None,
                         status: Option[StatusSearchFilter] = None,
                         searchable: Option[Boolean] = None,
                         context: Option[ContextSearchFilter] = None)

object SearchFilters extends ElasticDsl {

  implicit val searchFilterFormatted: RootJsonFormat[SearchFilters] =
    DefaultJsonProtocol.jsonFormat7(SearchFilters.apply)

  def parse(tags: Option[TagsSearchFilter] = None,
            themes: Option[ThemesSearchFilter] = None,
            title: Option[TitleSearchFilter] = None,
            slug: Option[SlugSearchFilter] = None,
            status: Option[StatusSearchFilter] = None,
            searchable: Option[Boolean] = None,
            context: Option[ContextSearchFilter] = None): Option[SearchFilters] = {

    (tags, themes, title, slug, status, searchable, context) match {
      case (None, None, None, None, None, None, None) => None
      case _                                          => Some(SearchFilters(tags, themes, title, slug, status, searchable, context))
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
      buildThemesSearchFilter(searchQuery),
      buildTagsSearchFilter(searchQuery),
      buildTitleSearchFilter(searchQuery),
      buildSlugSearchFilter(searchQuery),
      buildStatusSearchFilter(searchQuery),
      buildContextOperationSearchFilter(searchQuery),
      buildContextSourceSearchFilter(searchQuery),
      buildContextLocationSearchFilter(searchQuery),
      buildContextQuestionSearchFilter(searchQuery),
      buildSearchableFilter(searchQuery)
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

  def buildThemesSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    searchQuery.filters.flatMap {
      _.themes match {
        case Some(ThemesSearchFilter(Seq(themeId))) =>
          Some(ElasticApi.termQuery(SequenceElasticsearchFieldNames.themeId, themeId))
        case Some(ThemesSearchFilter(themes)) =>
          Some(ElasticApi.termsQuery(SequenceElasticsearchFieldNames.themes, themes))
        case _ => None
      }
    }
  }

  def buildTagsSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    searchQuery.filters.flatMap {
      _.tags match {
        case Some(TagsSearchFilter(Seq(tagId))) =>
          Some(ElasticApi.termQuery(SequenceElasticsearchFieldNames.tagId, tagId))
        case Some(TagsSearchFilter(tags)) =>
          Some(ElasticApi.termsQuery(SequenceElasticsearchFieldNames.tags, tags))
        case _ => None
      }
    }
  }

  def buildContextOperationSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    val operationFilter: Option[QueryDefinition] = for {
      filters   <- searchQuery.filters
      context   <- filters.context
      operation <- context.operation
    } yield ElasticApi.matchQuery(SequenceElasticsearchFieldNames.contextOperation, operation)

    operationFilter
  }

  def buildContextSourceSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    val sourceFilter: Option[QueryDefinition] = for {
      filters <- searchQuery.filters
      context <- filters.context
      source  <- context.source
    } yield ElasticApi.matchQuery(SequenceElasticsearchFieldNames.contextSource, source)

    sourceFilter
  }

  def buildContextLocationSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    val locationFilter: Option[QueryDefinition] = for {
      filters  <- searchQuery.filters
      context  <- filters.context
      location <- context.location
    } yield ElasticApi.matchQuery(SequenceElasticsearchFieldNames.contextLocation, location)

    locationFilter
  }

  def buildContextQuestionSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    val questionFilter: Option[QueryDefinition] = for {
      filters  <- searchQuery.filters
      context  <- filters.context
      question <- context.question
    } yield ElasticApi.matchQuery(SequenceElasticsearchFieldNames.contextQuestion, question)

    questionFilter
  }

  /*
   * TODO complete fuzzy search. potential hint:
   * https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#_fuzziness
   */
  def buildTitleSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {

    val query: Option[QueryDefinition] = for {
      filters                        <- searchQuery.filters
      TitleSearchFilter(text, fuzzy) <- filters.title
    } yield ElasticApi.matchQuery(SequenceElasticsearchFieldNames.title, text)

    query match {
      case None => Some(ElasticApi.matchAllQuery)
      case _    => query
    }
  }

  def buildSlugSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    val query: Option[QueryDefinition] = for {
      filters                <- searchQuery.filters
      SlugSearchFilter(text) <- filters.slug
    } yield ElasticApi.matchQuery(SequenceElasticsearchFieldNames.slug, text)

    query match {
      case None => Some(ElasticApi.matchAllQuery)
      case _    => query
    }
  }

  def buildStatusSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    val query: Option[QueryDefinition] = searchQuery.filters.flatMap {
      _.status.map {
        case StatusSearchFilter(SequenceStatus.Unpublished) =>
          ElasticApi.matchQuery(SequenceElasticsearchFieldNames.status, SequenceStatus.Unpublished.shortName)
        case StatusSearchFilter(SequenceStatus.Published) =>
          ElasticApi.matchQuery(SequenceElasticsearchFieldNames.status, SequenceStatus.Published.shortName)
      }
    }

    query match {
      case None =>
        Some(ElasticApi.matchQuery(SequenceElasticsearchFieldNames.status, SequenceStatus.Published.shortName))
      case _ => query
    }
  }

  def buildSearchableFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    val query: Option[QueryDefinition] = for {
      filters    <- searchQuery.filters
      searchable <- filters.searchable
    } yield ElasticApi.matchQuery(SequenceElasticsearchFieldNames.searchable, searchable)

    query match {
      case None => Some(ElasticApi.matchAllQuery)
      case _    => query
    }
  }
}

case class ThemesSearchFilter(themeIds: Seq[ThemeId])
object ThemesSearchFilter {
  implicit val themeSearchFilterFormatted: RootJsonFormat[ThemesSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(ThemesSearchFilter.apply)

}

case class TagsSearchFilter(tagIds: Seq[TagId])
object TagsSearchFilter {
  implicit val tagsSearchFilterFormatted: RootJsonFormat[TagsSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(TagsSearchFilter.apply)

}

case class TitleSearchFilter(text: String, fuzzy: Option[Int] = None)
object TitleSearchFilter {
  implicit val titleSearchFilterFormatted: RootJsonFormat[TitleSearchFilter] =
    DefaultJsonProtocol.jsonFormat2(TitleSearchFilter.apply)

}

case class SlugSearchFilter(text: String)
object SlugSearchFilter {
  implicit val slugSearchFilterFormatted: RootJsonFormat[SlugSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(SlugSearchFilter.apply)

}

case class StatusSearchFilter(status: SequenceStatus)
object StatusSearchFilter {
  implicit val statusSearchFilterFormatted: RootJsonFormat[StatusSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(StatusSearchFilter.apply)

}

case class ContextSearchFilter(operation: Option[String],
                               source: Option[String],
                               location: Option[String],
                               question: Option[String])

object ContextSearchFilter {
  implicit val contextSearchFilterFormatted: RootJsonFormat[ContextSearchFilter] =
    DefaultJsonProtocol.jsonFormat4(ContextSearchFilter.apply)

}

case class Limit(value: Int)
object Limit {
  implicit val limitFormatted: RootJsonFormat[Limit] =
    DefaultJsonProtocol.jsonFormat1(Limit.apply)

}

case class Skip(value: Int)
object Skip {
  implicit val skipFormatted: RootJsonFormat[Skip] =
    DefaultJsonProtocol.jsonFormat1(Skip.apply)
}
