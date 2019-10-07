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

package org.make.core.proposal

import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}

import com.sksamuel.elastic4s.{ElasticApi, Operator}
import com.sksamuel.elastic4s.http.ElasticDsl
import com.sksamuel.elastic4s.searches.queries.funcscorer.WeightScore
import com.sksamuel.elastic4s.searches.queries.matches.MatchQuery
import com.sksamuel.elastic4s.searches.queries.{Query, RangeQuery}
import com.sksamuel.elastic4s.searches.sort.{FieldSort, SortOrder}
import org.make.core.Validation.{validate, validateField}
import org.make.core.common.indexed.{Sort => IndexedSort}
import org.make.core.idea.{CountrySearchFilter, IdeaId, LanguageSearchFilter}
import org.make.core.operation.{OperationId, OperationKind}
import org.make.core.proposal.indexed.ProposalElasticsearchFieldNames
import org.make.core.question.QuestionId
import org.make.core.reference.{LabelId, Language}
import org.make.core.tag.TagId
import org.make.core.user.UserId

/**
  * The class holding the entire search query
  *
  * @param filters        sequence of search filters
  * @param sort           sequence of sorts options
  * @param limit          number of items to fetch
  * @param skip           number of items to skip
  * @param language       language to boost the query for. NOT A FILTER.
  * @param sortAlgorithm algorithm used for sorting
  */
case class SearchQuery(filters: Option[SearchFilters] = None,
                       sort: Option[IndexedSort] = None,
                       limit: Option[Int] = None,
                       skip: Option[Int] = None,
                       language: Option[Language] = None,
                       sortAlgorithm: Option[SortAlgorithm] = None) {
  def getSeed: Option[Int] =
    sortAlgorithm.flatMap {
      case algorithm: RandomBaseAlgorithm => Some(algorithm.seed)
      case _                              => None
    }
}

case class SearchFilters(proposal: Option[ProposalSearchFilter] = None,
                         initialProposal: Option[InitialProposalFilter] = None,
                         tags: Option[TagsSearchFilter] = None,
                         labels: Option[LabelsSearchFilter] = None,
                         operation: Option[OperationSearchFilter] = None,
                         question: Option[QuestionSearchFilter] = None,
                         trending: Option[TrendingSearchFilter] = None,
                         content: Option[ContentSearchFilter] = None,
                         status: Option[StatusSearchFilter] = None,
                         context: Option[ContextSearchFilter] = None,
                         slug: Option[SlugSearchFilter] = None,
                         idea: Option[IdeaSearchFilter] = None,
                         language: Option[LanguageSearchFilter] = None,
                         country: Option[CountrySearchFilter] = None,
                         user: Option[UserSearchFilter] = None,
                         minVotesCount: Option[MinVotesCountSearchFilter] = None,
                         toEnrich: Option[ToEnrichSearchFilter] = None,
                         minScore: Option[MinScoreSearchFilter] = None,
                         createdAt: Option[CreatedAtSearchFilter] = None,
                         sequencePool: Option[SequencePoolSearchFilter] = None,
                         operationKinds: Option[OperationKindsSearchFilter] = None,
                         questionIsOpen: Option[QuestionIsOpenSearchFilter] = None)

object SearchFilters extends ElasticDsl {

  //noinspection ScalaStyle
  def parse(proposals: Option[ProposalSearchFilter] = None,
            initialProposal: Option[InitialProposalFilter] = None,
            tags: Option[TagsSearchFilter] = None,
            labels: Option[LabelsSearchFilter] = None,
            operation: Option[OperationSearchFilter] = None,
            question: Option[QuestionSearchFilter] = None,
            trending: Option[TrendingSearchFilter] = None,
            content: Option[ContentSearchFilter] = None,
            status: Option[StatusSearchFilter] = None,
            slug: Option[SlugSearchFilter] = None,
            context: Option[ContextSearchFilter] = None,
            idea: Option[IdeaSearchFilter] = None,
            language: Option[LanguageSearchFilter] = None,
            country: Option[CountrySearchFilter] = None,
            user: Option[UserSearchFilter] = None,
            minVotesCount: Option[MinVotesCountSearchFilter] = None,
            toEnrich: Option[ToEnrichSearchFilter] = None,
            minScore: Option[MinScoreSearchFilter] = None,
            createdAt: Option[CreatedAtSearchFilter] = None,
            sequencePool: Option[SequencePoolSearchFilter] = None,
            operationKinds: Option[OperationKindsSearchFilter] = None,
            questionIsOpen: Option[QuestionIsOpenSearchFilter] = None): Option[SearchFilters] = {

    (
      proposals,
      initialProposal,
      tags,
      labels,
      operation,
      question,
      trending,
      content,
      status,
      slug,
      context,
      idea,
      language,
      country,
      user,
      minVotesCount,
      toEnrich,
      minScore,
      createdAt,
      sequencePool,
      operationKinds,
      questionIsOpen
    ) match {
      case (
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None
          ) =>
        None
      case _ =>
        Some(
          SearchFilters(
            proposals,
            initialProposal,
            tags,
            labels,
            operation,
            question,
            trending,
            content,
            status,
            context,
            slug,
            idea,
            language,
            country,
            user,
            minVotesCount,
            toEnrich,
            minScore,
            createdAt,
            sequencePool,
            operationKinds,
            questionIsOpen
          )
        )
    }
  }

  /**
    * Build elasticsearch search filters from searchQuery
    *
    * @param searchQuery search query
    * @return sequence of query definitions
    */
  def getSearchFilters(searchQuery: SearchQuery): Seq[Query] =
    Seq(
      buildProposalSearchFilter(searchQuery),
      buildInitialProposalSearchFilter(searchQuery),
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
      buildIdeaSearchFilter(searchQuery),
      buildLanguageSearchFilter(searchQuery),
      buildCountrySearchFilter(searchQuery),
      buildUserSearchFilter(searchQuery),
      buildQuestionSearchFilter(searchQuery),
      buildMinVotesCountSearchFilter(searchQuery),
      buildToEnrichSearchFilter(searchQuery),
      buildMinScoreSearchFilter(searchQuery),
      buildCreatedAtSearchFilter(searchQuery),
      buildSequencePoolSearchFilter(searchQuery),
      buildOperationKindSearchFilter(searchQuery),
      buildQuestionIsOpenSearchFilter(searchQuery)
    ).flatten

  def getSort(searchQuery: SearchQuery): Option[FieldSort] =
    searchQuery.sort.flatMap { sort =>
      sort.field.map { field =>
        FieldSort(field = field, order = sort.mode.getOrElse(SortOrder.ASC))
      }
    }

  def getSkipSearch(searchQuery: SearchQuery): Int =
    searchQuery.skip
      .getOrElse(0)

  def getLimitSearch(searchQuery: SearchQuery): Int =
    searchQuery.limit
      .getOrElse(10) // TODO get default value from configurations

  def buildProposalSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.proposal match {
        case Some(ProposalSearchFilter(Seq(proposalId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.id, proposalId.value))
        case Some(ProposalSearchFilter(proposalIds)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.id, proposalIds.map(_.value)))
        case _ => None
      }
    }
  }

  def buildQuestionSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.question match {
        case Some(QuestionSearchFilter(Seq(questionId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.questionId, questionId.value))
        case Some(QuestionSearchFilter(questionIds)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.questionId, questionIds.map(_.value)))
        case _ => None
      }
    }
  }

  def buildUserSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.user match {
        case Some(UserSearchFilter(userId)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.userId, userId.value))
        case _ => None
      }
    }
  }

  def buildInitialProposalSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.initialProposal.map { initialProposal =>
        ElasticApi.termQuery(
          field = ProposalElasticsearchFieldNames.initialProposal,
          value = initialProposal.isInitialProposal
        )
      }
    }
  }

  def buildTagsSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.tags match {
        case Some(TagsSearchFilter(Seq(tagId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.tagId, tagId.value))
        case Some(TagsSearchFilter(tags)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.tagId, tags.map(_.value)))
        case _ => None
      }
    }
  }

  def buildLabelsSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.labels match {
        case Some(LabelsSearchFilter(labels)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.labels, labels.map(_.value)))
        case _ => None
      }
    }
  }

  def buildOperationSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.operation match {
        case Some(OperationSearchFilter(Seq(operationId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.operationId, operationId.value))
        case Some(OperationSearchFilter(operationIds)) =>
          Some(
            ElasticApi
              .termsQuery(ProposalElasticsearchFieldNames.operationId, operationIds.map(_.value))
          )
        case _ => None
      }
    }
  }

  @Deprecated
  def buildTrendingSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.trending match {
        case Some(TrendingSearchFilter(trending)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.trending, trending))
        case _ => None
      }
    }
  }

  def buildContextOperationSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    val operationFilter: Option[Query] = for {
      filters   <- searchQuery.filters
      context   <- filters.context
      operation <- context.operation
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.contextOperation, operation.value)

    operationFilter
  }

  def buildContextSourceSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    val sourceFilter: Option[Query] = for {
      filters <- searchQuery.filters
      context <- filters.context
      source  <- context.source
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.contextSource, source)

    sourceFilter
  }

  def buildContextLocationSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    val locationFilter: Option[Query] = for {
      filters  <- searchQuery.filters
      context  <- filters.context
      location <- context.location
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.contextLocation, location)

    locationFilter
  }

  def buildContextQuestionSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    val questionFilter: Option[Query] = for {
      filters  <- searchQuery.filters
      context  <- filters.context
      question <- context.question
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.contextQuestion, question)

    questionFilter
  }

  def buildSlugSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    val slugFilter: Option[Query] = for {
      filters    <- searchQuery.filters
      slugFilter <- filters.slug
    } yield ElasticApi.termQuery(ProposalElasticsearchFieldNames.slug, slugFilter.slug)

    slugFilter
  }

  def buildContentSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    def languageOmission(boostedLanguage: String): Double =
      if (searchQuery.language.contains(Language(boostedLanguage))) 1 else 0

    for {
      filters                   <- searchQuery.filters
      ContentSearchFilter(text) <- filters.content
    } yield {
      val fieldsBoosts =
        Map(
          ProposalElasticsearchFieldNames.content -> 3D,
          ProposalElasticsearchFieldNames.contentFr -> 2D * languageOmission("fr"),
          ProposalElasticsearchFieldNames.contentFrStemmed -> 1.5D * languageOmission("fr"),
          ProposalElasticsearchFieldNames.contentEn -> 2D * languageOmission("en"),
          ProposalElasticsearchFieldNames.contentEnStemmed -> 1.5D * languageOmission("en"),
          ProposalElasticsearchFieldNames.contentIt -> 2D * languageOmission("it"),
          ProposalElasticsearchFieldNames.contentItStemmed -> 1.5D * languageOmission("it"),
          ProposalElasticsearchFieldNames.contentDe -> 2D * languageOmission("de"),
          ProposalElasticsearchFieldNames.contentDeStemmed -> 1.5D * languageOmission("de"),
          ProposalElasticsearchFieldNames.contentBg -> 2D * languageOmission("bg"),
          ProposalElasticsearchFieldNames.contentBgStemmed -> 1.5D * languageOmission("bg"),
          ProposalElasticsearchFieldNames.contentCs -> 2D * languageOmission("cs"),
          ProposalElasticsearchFieldNames.contentCsStemmed -> 1.5D * languageOmission("cs"),
          ProposalElasticsearchFieldNames.contentDa -> 2D * languageOmission("da"),
          ProposalElasticsearchFieldNames.contentDaStemmed -> 1.5D * languageOmission("da"),
          ProposalElasticsearchFieldNames.contentNl -> 2D * languageOmission("nl"),
          ProposalElasticsearchFieldNames.contentNlStemmed -> 1.5D * languageOmission("nl"),
          ProposalElasticsearchFieldNames.contentFi -> 2D * languageOmission("fi"),
          ProposalElasticsearchFieldNames.contentFiStemmed -> 1.5D * languageOmission("fi"),
          ProposalElasticsearchFieldNames.contentEl -> 2D * languageOmission("el"),
          ProposalElasticsearchFieldNames.contentElStemmed -> 1.5D * languageOmission("el"),
          ProposalElasticsearchFieldNames.contentHu -> 2D * languageOmission("hu"),
          ProposalElasticsearchFieldNames.contentHuStemmed -> 1.5D * languageOmission("hu"),
          ProposalElasticsearchFieldNames.contentLv -> 2D * languageOmission("lv"),
          ProposalElasticsearchFieldNames.contentLvStemmed -> 1.5D * languageOmission("lv"),
          ProposalElasticsearchFieldNames.contentLt -> 2D * languageOmission("lt"),
          ProposalElasticsearchFieldNames.contentLtStemmed -> 1.5D * languageOmission("lt"),
          ProposalElasticsearchFieldNames.contentPt -> 2D * languageOmission("pt"),
          ProposalElasticsearchFieldNames.contentPtStemmed -> 1.5D * languageOmission("pt"),
          ProposalElasticsearchFieldNames.contentRo -> 2D * languageOmission("ro"),
          ProposalElasticsearchFieldNames.contentRoStemmed -> 1.5D * languageOmission("ro"),
          ProposalElasticsearchFieldNames.contentEs -> 2D * languageOmission("es"),
          ProposalElasticsearchFieldNames.contentEsStemmed -> 1.5D * languageOmission("es"),
          ProposalElasticsearchFieldNames.contentSv -> 2D * languageOmission("sv"),
          ProposalElasticsearchFieldNames.contentSvStemmed -> 1.5D * languageOmission("sv"),
          ProposalElasticsearchFieldNames.contentPl -> 2D * languageOmission("pl"),
          ProposalElasticsearchFieldNames.contentPlStemmed -> 1.5D * languageOmission("pl"),
          ProposalElasticsearchFieldNames.contentHr -> 2D * languageOmission("hr"),
          ProposalElasticsearchFieldNames.contentEt -> 2D * languageOmission("et"),
          ProposalElasticsearchFieldNames.contentMt -> 2D * languageOmission("mt"),
          ProposalElasticsearchFieldNames.contentSk -> 2D * languageOmission("sk"),
          ProposalElasticsearchFieldNames.contentSl -> 2D * languageOmission("sl"),
          ProposalElasticsearchFieldNames.contentGeneral -> 1D
        ).filter { case (_, boost) => boost != 0 }
      functionScoreQuery(multiMatchQuery(text).fields(fieldsBoosts).fuzziness("Auto:4,7").operator(Operator.AND))
        .functions(
          WeightScore(
            weight = 2D,
            filter = Some(MatchQuery(field = ProposalElasticsearchFieldNames.questionIsOpen, value = true))
          )
        )

    }
  }

  def buildStatusSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    val query: Option[Query] = searchQuery.filters.flatMap {
      _.status.map {
        case StatusSearchFilter(Seq(proposalStatus)) =>
          ElasticApi.termQuery(ProposalElasticsearchFieldNames.status, proposalStatus.shortName)
        case StatusSearchFilter(proposalStatuses) =>
          ElasticApi.termsQuery(ProposalElasticsearchFieldNames.status, proposalStatuses.map(_.shortName))
      }
    }

    query match {
      case None =>
        Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.status, ProposalStatus.Accepted.shortName))
      case _ => query
    }
  }

  def buildIdeaSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.idea match {
        case Some(IdeaSearchFilter(idea)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.ideaId, idea.value))
        case _ => None
      }
    }
  }

  def buildLanguageSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.language match {
        case Some(LanguageSearchFilter(language)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.language, language.value))
        case _ => None
      }
    }
  }

  def buildCountrySearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.country match {
        case Some(CountrySearchFilter(country)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.country, country.value))
        case _ => None
      }
    }
  }

  def buildMinVotesCountSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.minVotesCount match {
        case Some(MinVotesCountSearchFilter(minVotesCount)) =>
          Some(ElasticApi.rangeQuery(ProposalElasticsearchFieldNames.votesCount).gte(minVotesCount))
        case _ => None
      }
    }
  }

  def buildToEnrichSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.toEnrich match {
        case Some(ToEnrichSearchFilter(toEnrich)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.toEnrich, toEnrich))
        case _ => None
      }
    }
  }

  def buildMinScoreSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.minScore match {
        case Some(MinScoreSearchFilter(minScore)) =>
          Some(ElasticApi.rangeQuery(ProposalElasticsearchFieldNames.scoreUpperBound).gte(minScore))
        case _ => None
      }
    }
  }

  def buildCreatedAtSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.createdAt match {
        case Some(CreatedAtSearchFilter(maybeBefore, maybeAfter)) =>
          val createdAtRangeQuery: RangeQuery = ElasticApi.rangeQuery(ProposalElasticsearchFieldNames.createdAt)
          val dateFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
          (
            maybeBefore.map(before => before.format(dateFormatter)),
            maybeAfter.map(after   => after.format(dateFormatter))
          ) match {
            case (Some(before), Some(after)) => Some(createdAtRangeQuery.lt(before).gt(after))
            case (Some(before), None)        => Some(createdAtRangeQuery.lt(before))
            case (None, Some(after))         => Some(createdAtRangeQuery.gte(after))
            case _                           => None
          }
        case _ => None
      }
    }
  }

  def buildSequencePoolSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.sequencePool match {
        case Some(SequencePoolSearchFilter(sequencePool)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.sequencePool, sequencePool))
        case _ => None
      }
    }
  }

//TODO: To avoid a pattern matching to precise, can't we just use `termsQuery` ?
  def buildOperationKindSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.operationKinds match {
        case Some(OperationKindsSearchFilter(Seq(operationKind))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.operationKind, operationKind.shortName))
        case Some(OperationKindsSearchFilter(operationKinds)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.operationKind, operationKinds.map(_.shortName)))
        case _ => None
      }
    }
  }

  def buildQuestionIsOpenSearchFilter(searchQuery: SearchQuery): Option[Query] = {
    searchQuery.filters.flatMap {
      _.questionIsOpen match {
        case Some(QuestionIsOpenSearchFilter(isOpen)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.questionIsOpen, isOpen))
        case _ => None
      }
    }
  }

}

case class ProposalSearchFilter(proposalIds: Seq[ProposalId])

case class UserSearchFilter(userId: UserId)

case class InitialProposalFilter(isInitialProposal: Boolean)

case class QuestionSearchFilter(questionIds: Seq[QuestionId])

case class TagsSearchFilter(tagIds: Seq[TagId]) {
  validate(validateField("tagId", "mandatory", tagIds.nonEmpty, "ids cannot be empty in tag search filters"))
}

case class LabelsSearchFilter(labelIds: Seq[LabelId]) {
  validate(validateField("labelIds", "mandatory", labelIds.nonEmpty, "ids cannot be empty in label search filters"))
}

case class OperationSearchFilter(operationIds: Seq[OperationId])

case class TrendingSearchFilter(trending: String) {
  validate(validateField("trending", "mandatory", trending.nonEmpty, "trending cannot be empty in search filters"))
}

case class CreatedAtSearchFilter(before: Option[ZonedDateTime], after: Option[ZonedDateTime])

case class ContentSearchFilter(text: String)

case class StatusSearchFilter(status: Seq[ProposalStatus])

case class ContextSearchFilter(operation: Option[OperationId] = None,
                               source: Option[String] = None,
                               location: Option[String] = None,
                               question: Option[String] = None)

case class SlugSearchFilter(slug: String)

case class IdeaSearchFilter(ideaId: IdeaId)

case class Limit(value: Int)

case class Skip(value: Int)

case class MinVotesCountSearchFilter(minVotesCount: Int)
case class ToEnrichSearchFilter(toEnrich: Boolean)
case class MinScoreSearchFilter(minScore: Float)
case class SequencePoolSearchFilter(sequencePool: String)
case class OperationKindsSearchFilter(kinds: Seq[OperationKind])
case class QuestionIsOpenSearchFilter(isOpen: Boolean)
