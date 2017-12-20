package org.make.core.proposal

import com.sksamuel.elastic4s.ElasticApi
import com.sksamuel.elastic4s.http.ElasticDsl
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.FieldSortDefinition
import org.elasticsearch.search.sort.SortOrder
import org.make.core.Validation.{validate, validateField}
import org.make.core.common.indexed.{Sort => IndexedSort}
import org.make.core.operation.OperationId
import org.make.core.proposal.indexed.ProposalElasticsearchFieldNames
import org.make.core.reference.{LabelId, TagId, ThemeId}
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
                       sorts: Seq[IndexedSort] = Seq.empty,
                       limit: Option[Int] = None,
                       skip: Option[Int] = None)

object SearchQuery {
  implicit val searchQueryFormatted: RootJsonFormat[SearchQuery] =
    DefaultJsonProtocol.jsonFormat4(SearchQuery.apply)
}

/**
  * The class holding the filters
  *
  * @param theme   The Theme to filter
  * @param tags    List of Tags to filters
  * @param labels  List of Tags to filters
  * @param content Text to search into the proposal
  * @param status  The Status of proposal
  */
case class SearchFilters(proposal: Option[ProposalSearchFilter] = None,
                         theme: Option[ThemeSearchFilter] = None,
                         tags: Option[TagsSearchFilter] = None,
                         labels: Option[LabelsSearchFilter] = None,
                         operation: Option[OperationSearchFilter] = None,
                         trending: Option[TrendingSearchFilter] = None,
                         content: Option[ContentSearchFilter] = None,
                         status: Option[StatusSearchFilter] = None,
                         context: Option[ContextSearchFilter] = None,
                         slug: Option[SlugSearchFilter] = None,
                         idea: Option[IdeaSearchFilter] = None)

object SearchFilters extends ElasticDsl {

  implicit val searchFilterFormatted: RootJsonFormat[SearchFilters] =
    DefaultJsonProtocol.jsonFormat11(SearchFilters.apply)

  def parse(proposals: Option[ProposalSearchFilter] = None,
            themes: Option[ThemeSearchFilter] = None,
            tags: Option[TagsSearchFilter] = None,
            labels: Option[LabelsSearchFilter] = None,
            operation: Option[OperationSearchFilter] = None,
            trending: Option[TrendingSearchFilter] = None,
            content: Option[ContentSearchFilter] = None,
            status: Option[StatusSearchFilter] = None,
            slug: Option[SlugSearchFilter] = None,
            context: Option[ContextSearchFilter] = None,
            idea: Option[IdeaSearchFilter] = None): Option[SearchFilters] = {

    (proposals, themes, tags, labels, operation, trending, content, status, slug, context, idea) match {
      case (None, None, None, None, None, None, None, None, None, None, None) => None
      case _ =>
        Some(SearchFilters(proposals, themes, tags, labels, operation, trending, content, status, context, slug, idea))
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
      buildProposalSearchFilter(searchQuery),
      buildThemeSearchFilter(searchQuery),
      buildTagsSearchFilter(searchQuery),
      buildLabelsSearchFilter(searchQuery),
      buildOperationSearchFilter(searchQuery),
      buildTrendingSearchFilter(searchQuery),
      buildContentSearchFilter(searchQuery),
      buildStatusSearchFilter(searchQuery),
      buildContextOperationSearchFilter(searchQuery),
      buildContextSourceSearchFilter(searchQuery),
      buildContextLocationSearchFilter(searchQuery),
      buildContextQuestionSearchFilter(searchQuery),
      buildSlugSearchFilter(searchQuery),
      buildIdeaSearchFilter(searchQuery)
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

  def buildProposalSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    searchQuery.filters.flatMap {
      _.proposal match {
        case Some(ProposalSearchFilter(Seq(proposalId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.id, proposalId))
        case Some(ProposalSearchFilter(proposalIds)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.id, proposalIds))
        case _ => None
      }
    }
  }

  def buildThemeSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    searchQuery.filters.flatMap {
      _.theme match {
        case Some(ThemeSearchFilter(Seq(themeId))) =>
          Some(ElasticApi.matchQuery(ProposalElasticsearchFieldNames.themeId, themeId))
        case Some(ThemeSearchFilter(themeIds)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.themeId, themeIds))
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
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.tagId, tags))
        case _ => None
      }
    }
  }

  def buildLabelsSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    searchQuery.filters.flatMap {
      _.labels match {
        case Some(LabelsSearchFilter(labels)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.labels, labels))
        case _ => None
      }
    }
  }

  def buildOperationSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    val operationFilter: Option[QueryDefinition] = for {
      filters   <- searchQuery.filters
      operation <- filters.operation
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.operationId, operation.operationId)

    operationFilter
  }

  def buildTrendingSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    searchQuery.filters.flatMap {
      _.trending match {
        case Some(TrendingSearchFilter(trending)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.trending, trending))
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

  def buildSlugSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    val slugFilter: Option[QueryDefinition] = for {
      filters    <- searchQuery.filters
      slugFilter <- filters.slug
    } yield ElasticApi.termQuery(ProposalElasticsearchFieldNames.slug, slugFilter.slug)

    slugFilter
  }

  def buildContentSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {

    val query: Option[QueryDefinition] = for {
      filters                               <- searchQuery.filters
      ContentSearchFilter(text, maybeFuzzy) <- filters.content
    } yield {
      maybeFuzzy match {
        case Some(fuzzy) =>
          ElasticApi
            .should(
              multiMatchQuery(text)
                .fields(
                  Map(
                    ProposalElasticsearchFieldNames.content -> 2F,
                    ProposalElasticsearchFieldNames.contentStemmed -> 1F
                  )
                )
                .boost(2F),
              multiMatchQuery(text)
                .fields(
                  Map(
                    ProposalElasticsearchFieldNames.content -> 2F,
                    ProposalElasticsearchFieldNames.contentStemmed -> 1F
                  )
                )
                .fuzziness(fuzzy)
                .boost(1F)
            )
        case None =>
          ElasticApi
            .multiMatchQuery(text)
            .fields(
              Map(ProposalElasticsearchFieldNames.content -> 2F, ProposalElasticsearchFieldNames.contentStemmed -> 1F)
            )
      }
    }

    query match {
      case None => None
      case _    => query
    }
  }

  def buildStatusSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    val query: Option[QueryDefinition] = searchQuery.filters.flatMap {
      _.status.map {
        case StatusSearchFilter(Seq(proposalStatus)) =>
          ElasticApi.matchQuery(ProposalElasticsearchFieldNames.status, proposalStatus.shortName)
        case StatusSearchFilter(proposalStatuses) =>
          ElasticApi.termsQuery(ProposalElasticsearchFieldNames.status, proposalStatuses.map(_.shortName))
      }
    }

    query match {
      case None =>
        Some(ElasticApi.matchQuery(ProposalElasticsearchFieldNames.status, ProposalStatus.Accepted.shortName))
      case _ => query
    }
  }

  def buildIdeaSearchFilter(searchQuery: SearchQuery): Option[QueryDefinition] = {
    searchQuery.filters.flatMap {
      _.idea match {
        case Some(IdeaSearchFilter(idea)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.ideaId, idea))
        case _ => None
      }
    }
  }
}

case class ProposalSearchFilter(proposalIds: Seq[ProposalId])

object ProposalSearchFilter {
  implicit val proposalSearchFilterFormatted: RootJsonFormat[ProposalSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(ProposalSearchFilter.apply)
}

case class ThemeSearchFilter(themeIds: Seq[ThemeId]) {
  validate(validateField("ThemeId", themeIds.nonEmpty, "ids cannot be empty in theme search filters"))
}

object ThemeSearchFilter {
  implicit val themeSearchFilterFormatted: RootJsonFormat[ThemeSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(ThemeSearchFilter.apply)
}

case class TagsSearchFilter(tagIds: Seq[TagId]) {
  validate(validateField("tagId", tagIds.nonEmpty, "ids cannot be empty in tag search filters"))
}

object TagsSearchFilter {
  implicit val tagsSearchFilterFormatted: RootJsonFormat[TagsSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(TagsSearchFilter.apply)
}

case class LabelsSearchFilter(labelIds: Seq[LabelId]) {
  validate(validateField("labelIds", labelIds.nonEmpty, "ids cannot be empty in label search filters"))
}

object LabelsSearchFilter {
  implicit val labelsSearchFilterFormatted: RootJsonFormat[LabelsSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(LabelsSearchFilter.apply)
}

case class OperationSearchFilter(operationId: OperationId)

object OperationSearchFilter {
  implicit val operationSearchFilterFormatted: RootJsonFormat[OperationSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(OperationSearchFilter.apply)
}

case class TrendingSearchFilter(trending: String) {
  validate(validateField("trending", trending.nonEmpty, "trending cannot be empty in search filters"))
}

object TrendingSearchFilter {
  implicit val trendingSearchFilterFormatted: RootJsonFormat[TrendingSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(TrendingSearchFilter.apply)
}

case class ContentSearchFilter(text: String, fuzzy: Option[String] = None)

object ContentSearchFilter {
  implicit val contentSearchFilterFormatted: RootJsonFormat[ContentSearchFilter] =
    DefaultJsonProtocol.jsonFormat2(ContentSearchFilter.apply)
}

case class StatusSearchFilter(status: Seq[ProposalStatus])

object StatusSearchFilter {
  implicit val statusSearchFilterFormatted: RootJsonFormat[StatusSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(StatusSearchFilter.apply)
}

case class ContextSearchFilter(operation: Option[OperationId] = None,
                               source: Option[String] = None,
                               location: Option[String] = None,
                               question: Option[String] = None)

object ContextSearchFilter {
  implicit val contextSearchFilterFormatted: RootJsonFormat[ContextSearchFilter] =
    DefaultJsonProtocol.jsonFormat4(ContextSearchFilter.apply)
}

case class SlugSearchFilter(slug: String)

object SlugSearchFilter {
  implicit val format: RootJsonFormat[SlugSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(SlugSearchFilter.apply)
}

case class IdeaSearchFilter(ideaId: String)

object IdeaSearchFilter {
  implicit val format: RootJsonFormat[IdeaSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(IdeaSearchFilter.apply)
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
