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
import org.make.core.proposal.indexed.{ProposalElasticsearchFieldNames, SequencePool}
import org.make.core.question.QuestionId
import org.make.core.reference.{LabelId, Language}
import org.make.core.tag.TagId
import org.make.core.user.{UserId, UserType}

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
case class SearchQuery(
  filters: Option[SearchFilters] = None,
  excludes: Option[SearchFilters] = None,
  sort: Option[IndexedSort] = None,
  limit: Option[Int] = None,
  skip: Option[Int] = None,
  language: Option[Language] = None,
  sortAlgorithm: Option[SortAlgorithm] = None
) {
  def getSeed: Option[Int] =
    sortAlgorithm.flatMap {
      case algorithm: RandomBaseAlgorithm => Some(algorithm.seed)
      case _                              => None
    }
}

case class SearchFilters(
  proposal: Option[ProposalSearchFilter] = None,
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
  sequenceSegmentPool: Option[SequencePoolSearchFilter] = None,
  operationKinds: Option[OperationKindsSearchFilter] = None,
  questionIsOpen: Option[QuestionIsOpenSearchFilter] = None,
  segment: Option[SegmentSearchFilter] = None,
  userTypes: Option[UserTypesSearchFilter] = None
)

object SearchFilters extends ElasticDsl {

  //noinspection ScalaStyle
  def parse(
    proposals: Option[ProposalSearchFilter] = None,
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
    sequenceSegmentPool: Option[SequencePoolSearchFilter] = None,
    operationKinds: Option[OperationKindsSearchFilter] = None,
    questionIsOpen: Option[QuestionIsOpenSearchFilter] = None,
    segment: Option[SegmentSearchFilter] = None,
    userTypes: Option[UserTypesSearchFilter] = None
  ): Option[SearchFilters] = {

    Seq(
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
      sequenceSegmentPool,
      operationKinds,
      questionIsOpen,
      segment,
      userTypes
    ).flatten match {
      case Seq() => None
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
            sequenceSegmentPool,
            operationKinds,
            questionIsOpen,
            segment,
            userTypes
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
      buildProposalSearchFilter(searchQuery.filters),
      buildInitialProposalSearchFilter(searchQuery.filters),
      buildTagsSearchFilter(searchQuery.filters),
      buildLabelsSearchFilter(searchQuery.filters),
      buildOperationSearchFilter(searchQuery.filters),
      buildTrendingSearchFilter(searchQuery.filters),
      buildContentSearchFilter(searchQuery),
      buildStatusSearchFilter(searchQuery.filters),
      buildContextOperationSearchFilter(searchQuery.filters),
      buildContextSourceSearchFilter(searchQuery.filters),
      buildContextLocationSearchFilter(searchQuery.filters),
      buildContextQuestionSearchFilter(searchQuery.filters),
      buildSlugSearchFilter(searchQuery.filters),
      buildIdeaSearchFilter(searchQuery.filters),
      buildLanguageSearchFilter(searchQuery.filters),
      buildCountrySearchFilter(searchQuery.filters),
      buildUserSearchFilter(searchQuery.filters),
      buildQuestionSearchFilter(searchQuery.filters),
      buildMinVotesCountSearchFilter(searchQuery.filters),
      buildToEnrichSearchFilter(searchQuery.filters),
      buildMinScoreSearchFilter(searchQuery.filters),
      buildCreatedAtSearchFilter(searchQuery.filters),
      buildSequencePoolSearchFilter(searchQuery.filters),
      buildSequenceSegmentPoolSearchFilter(searchQuery.filters),
      buildOperationKindSearchFilter(searchQuery.filters),
      buildQuestionIsOpenSearchFilter(searchQuery.filters),
      buildSegmentSearchFilter(searchQuery.filters),
      buildUserTypesSearchFilter(searchQuery.filters)
    ).flatten

  def getExcludeFilters(searchQuery: SearchQuery): Seq[Query] = {
    Seq(buildProposalSearchFilter(searchQuery.excludes)).flatten
  }

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

  def buildProposalSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.proposal match {
        case Some(ProposalSearchFilter(Seq(proposalId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.id, proposalId.value))
        case Some(ProposalSearchFilter(proposalIds)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.id, proposalIds.map(_.value)))
        case _ => None
      }
    }
  }

  def buildQuestionSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.question match {
        case Some(QuestionSearchFilter(Seq(questionId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.questionId, questionId.value))
        case Some(QuestionSearchFilter(questionIds)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.questionId, questionIds.map(_.value)))
        case _ => None
      }
    }
  }

  def buildUserSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.user match {
        case Some(UserSearchFilter(userId)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.userId, userId.value))
        case _ => None
      }
    }
  }

  def buildInitialProposalSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.initialProposal.map { initialProposal =>
        ElasticApi.termQuery(
          field = ProposalElasticsearchFieldNames.initialProposal,
          value = initialProposal.isInitialProposal
        )
      }
    }
  }

  def buildTagsSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.tags match {
        case Some(TagsSearchFilter(Seq(tagId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.tagId, tagId.value))
        case Some(TagsSearchFilter(tags)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.tagId, tags.map(_.value)))
        case _ => None
      }
    }
  }

  def buildLabelsSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.labels match {
        case Some(LabelsSearchFilter(labels)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.labels, labels.map(_.value)))
        case _ => None
      }
    }
  }

  def buildOperationSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
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
  def buildTrendingSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.trending match {
        case Some(TrendingSearchFilter(trending)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.trending, trending))
        case _ => None
      }
    }
  }

  def buildContextOperationSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    val operationFilter: Option[Query] = for {
      filters   <- filters
      context   <- filters.context
      operation <- context.operation
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.contextOperation, operation.value)

    operationFilter
  }

  def buildContextSourceSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    val sourceFilter: Option[Query] = for {
      filters <- filters
      context <- filters.context
      source  <- context.source
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.contextSource, source)

    sourceFilter
  }

  def buildContextLocationSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    val locationFilter: Option[Query] = for {
      filters  <- filters
      context  <- filters.context
      location <- context.location
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.contextLocation, location)

    locationFilter
  }

  def buildContextQuestionSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    val questionFilter: Option[Query] = for {
      filters  <- filters
      context  <- filters.context
      question <- context.question
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldNames.contextQuestion, question)

    questionFilter
  }

  def buildSlugSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    val slugFilter: Option[Query] = for {
      filters    <- filters
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
          ProposalElasticsearchFieldNames.content -> 3d,
          ProposalElasticsearchFieldNames.contentFr -> 2d * languageOmission("fr"),
          ProposalElasticsearchFieldNames.contentFrStemmed -> 1.5d * languageOmission("fr"),
          ProposalElasticsearchFieldNames.contentEn -> 2d * languageOmission("en"),
          ProposalElasticsearchFieldNames.contentEnStemmed -> 1.5d * languageOmission("en"),
          ProposalElasticsearchFieldNames.contentIt -> 2d * languageOmission("it"),
          ProposalElasticsearchFieldNames.contentItStemmed -> 1.5d * languageOmission("it"),
          ProposalElasticsearchFieldNames.contentDe -> 2d * languageOmission("de"),
          ProposalElasticsearchFieldNames.contentDeStemmed -> 1.5d * languageOmission("de"),
          ProposalElasticsearchFieldNames.contentBg -> 2d * languageOmission("bg"),
          ProposalElasticsearchFieldNames.contentBgStemmed -> 1.5d * languageOmission("bg"),
          ProposalElasticsearchFieldNames.contentCs -> 2d * languageOmission("cs"),
          ProposalElasticsearchFieldNames.contentCsStemmed -> 1.5d * languageOmission("cs"),
          ProposalElasticsearchFieldNames.contentDa -> 2d * languageOmission("da"),
          ProposalElasticsearchFieldNames.contentDaStemmed -> 1.5d * languageOmission("da"),
          ProposalElasticsearchFieldNames.contentNl -> 2d * languageOmission("nl"),
          ProposalElasticsearchFieldNames.contentNlStemmed -> 1.5d * languageOmission("nl"),
          ProposalElasticsearchFieldNames.contentFi -> 2d * languageOmission("fi"),
          ProposalElasticsearchFieldNames.contentFiStemmed -> 1.5d * languageOmission("fi"),
          ProposalElasticsearchFieldNames.contentEl -> 2d * languageOmission("el"),
          ProposalElasticsearchFieldNames.contentElStemmed -> 1.5d * languageOmission("el"),
          ProposalElasticsearchFieldNames.contentHu -> 2d * languageOmission("hu"),
          ProposalElasticsearchFieldNames.contentHuStemmed -> 1.5d * languageOmission("hu"),
          ProposalElasticsearchFieldNames.contentLv -> 2d * languageOmission("lv"),
          ProposalElasticsearchFieldNames.contentLvStemmed -> 1.5d * languageOmission("lv"),
          ProposalElasticsearchFieldNames.contentLt -> 2d * languageOmission("lt"),
          ProposalElasticsearchFieldNames.contentLtStemmed -> 1.5d * languageOmission("lt"),
          ProposalElasticsearchFieldNames.contentPt -> 2d * languageOmission("pt"),
          ProposalElasticsearchFieldNames.contentPtStemmed -> 1.5d * languageOmission("pt"),
          ProposalElasticsearchFieldNames.contentRo -> 2d * languageOmission("ro"),
          ProposalElasticsearchFieldNames.contentRoStemmed -> 1.5d * languageOmission("ro"),
          ProposalElasticsearchFieldNames.contentEs -> 2d * languageOmission("es"),
          ProposalElasticsearchFieldNames.contentEsStemmed -> 1.5d * languageOmission("es"),
          ProposalElasticsearchFieldNames.contentSv -> 2d * languageOmission("sv"),
          ProposalElasticsearchFieldNames.contentSvStemmed -> 1.5d * languageOmission("sv"),
          ProposalElasticsearchFieldNames.contentPl -> 2d * languageOmission("pl"),
          ProposalElasticsearchFieldNames.contentPlStemmed -> 1.5d * languageOmission("pl"),
          ProposalElasticsearchFieldNames.contentHr -> 2d * languageOmission("hr"),
          ProposalElasticsearchFieldNames.contentEt -> 2d * languageOmission("et"),
          ProposalElasticsearchFieldNames.contentMt -> 2d * languageOmission("mt"),
          ProposalElasticsearchFieldNames.contentSk -> 2d * languageOmission("sk"),
          ProposalElasticsearchFieldNames.contentSl -> 2d * languageOmission("sl"),
          ProposalElasticsearchFieldNames.contentGeneral -> 1d
        ).filter { case (_, boost) => boost != 0 }
      functionScoreQuery(multiMatchQuery(text).fields(fieldsBoosts).fuzziness("Auto:4,7").operator(Operator.AND))
        .functions(
          WeightScore(
            weight = 2d,
            filter = Some(MatchQuery(field = ProposalElasticsearchFieldNames.questionIsOpen, value = true))
          )
        )

    }
  }

  def buildStatusSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    val query: Option[Query] = filters.flatMap {
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

  def buildIdeaSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.idea match {
        case Some(IdeaSearchFilter(Seq(idea))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.ideaId, idea.value))
        case Some(IdeaSearchFilter(ideas)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.ideaId, ideas.map(_.value)))
        case _ => None
      }
    }
  }

  def buildLanguageSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.language match {
        case Some(LanguageSearchFilter(language)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.language, language.value))
        case _ => None
      }
    }
  }

  def buildCountrySearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.country match {
        case Some(CountrySearchFilter(country)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.country, country.value))
        case _ => None
      }
    }
  }

  def buildMinVotesCountSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.minVotesCount match {
        case Some(MinVotesCountSearchFilter(minVotesCount)) =>
          Some(ElasticApi.rangeQuery(ProposalElasticsearchFieldNames.votesCount).gte(minVotesCount))
        case _ => None
      }
    }
  }

  def buildToEnrichSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.toEnrich match {
        case Some(ToEnrichSearchFilter(toEnrich)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.toEnrich, toEnrich))
        case _ => None
      }
    }
  }

  def buildMinScoreSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.minScore match {
        case Some(MinScoreSearchFilter(minScore)) =>
          Some(ElasticApi.rangeQuery(ProposalElasticsearchFieldNames.scoreUpperBound).gte(minScore))
        case _ => None
      }
    }
  }

  def buildCreatedAtSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
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

  def buildSequencePoolSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.sequencePool match {
        case Some(SequencePoolSearchFilter(sequencePool)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.sequencePool, sequencePool.shortName))
        case _ => None
      }
    }
  }

  def buildSequenceSegmentPoolSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.sequenceSegmentPool match {
        case Some(SequencePoolSearchFilter(sequencePool)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.sequenceSegmentPool, sequencePool.shortName))
        case _ => None
      }
    }
  }

//TODO: To avoid a pattern matching to precise, can't we just use `termsQuery` ?
  def buildOperationKindSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.operationKinds match {
        case Some(OperationKindsSearchFilter(Seq(operationKind))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.operationKind, operationKind.shortName))
        case Some(OperationKindsSearchFilter(operationKinds)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.operationKind, operationKinds.map(_.shortName)))
        case _ => None
      }
    }
  }

  def buildQuestionIsOpenSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.questionIsOpen match {
        case Some(QuestionIsOpenSearchFilter(isOpen)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.questionIsOpen, isOpen))
        case _ => None
      }
    }
  }

  def buildSegmentSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.segment match {
        case Some(SegmentSearchFilter(segment)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.segment, segment))
        case _ => None
      }
    }
  }

  def buildUserTypesSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.userTypes match {
        case Some(UserTypesSearchFilter(Seq(userType))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldNames.authorUserType, userType.shortName))
        case Some(UserTypesSearchFilter(userTypes)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldNames.authorUserType, userTypes.map(_.shortName)))
        case _ => None
      }
    }
  }
}

final case class ProposalSearchFilter(proposalIds: Seq[ProposalId])

final case class UserSearchFilter(userId: UserId)

final case class InitialProposalFilter(isInitialProposal: Boolean)

final case class QuestionSearchFilter(questionIds: Seq[QuestionId])

final case class TagsSearchFilter(tagIds: Seq[TagId]) {
  validate(validateField("tagId", "mandatory", tagIds.nonEmpty, "ids cannot be empty in tag search filters"))
}

final case class LabelsSearchFilter(labelIds: Seq[LabelId]) {
  validate(validateField("labelIds", "mandatory", labelIds.nonEmpty, "ids cannot be empty in label search filters"))
}

final case class OperationSearchFilter(operationIds: Seq[OperationId])

final case class TrendingSearchFilter(trending: String) {
  validate(validateField("trending", "mandatory", trending.nonEmpty, "trending cannot be empty in search filters"))
}

final case class CreatedAtSearchFilter(before: Option[ZonedDateTime], after: Option[ZonedDateTime])

final case class ContentSearchFilter(text: String)

final case class StatusSearchFilter(status: Seq[ProposalStatus])

final case class ContextSearchFilter(
  operation: Option[OperationId] = None,
  source: Option[String] = None,
  location: Option[String] = None,
  question: Option[String] = None
)

final case class SlugSearchFilter(slug: String)

final case class IdeaSearchFilter(ideaIds: Seq[IdeaId])

final case class Limit(value: Int)

final case class Skip(value: Int)

final case class MinVotesCountSearchFilter(minVotesCount: Int)
final case class ToEnrichSearchFilter(toEnrich: Boolean)
final case class MinScoreSearchFilter(minScore: Float)
final case class SequencePoolSearchFilter(sequencePool: SequencePool)
final case class OperationKindsSearchFilter(kinds: Seq[OperationKind])
final case class QuestionIsOpenSearchFilter(isOpen: Boolean)
final case class SegmentSearchFilter(segment: String)
final case class UserTypesSearchFilter(userTypes: Seq[UserType])
