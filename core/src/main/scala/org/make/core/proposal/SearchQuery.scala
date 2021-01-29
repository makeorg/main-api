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
import org.make.core.idea.IdeaId
import org.make.core.operation.{OperationId, OperationKind}
import org.make.core.proposal.indexed.{ProposalElasticsearchFieldName, SequencePool, Zone}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, LabelId, Language}
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
final case class SearchQuery(
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

final case class SearchFilters(
  proposal: Option[ProposalSearchFilter] = None,
  initialProposal: Option[InitialProposalFilter] = None,
  tags: Option[TagsSearchFilter] = None,
  labels: Option[LabelsSearchFilter] = None,
  operation: Option[OperationSearchFilter] = None,
  question: Option[QuestionSearchFilter] = None,
  content: Option[ContentSearchFilter] = None,
  status: Option[StatusSearchFilter] = None,
  context: Option[ContextSearchFilter] = None,
  slug: Option[SlugSearchFilter] = None,
  idea: Option[IdeaSearchFilter] = None,
  language: Option[LanguageSearchFilter] = None,
  country: Option[CountrySearchFilter] = None,
  users: Option[UserSearchFilter] = None,
  minVotesCount: Option[MinVotesCountSearchFilter] = None,
  toEnrich: Option[ToEnrichSearchFilter] = None,
  minScore: Option[MinScoreSearchFilter] = None,
  createdAt: Option[CreatedAtSearchFilter] = None,
  sequencePool: Option[SequencePoolSearchFilter] = None,
  sequenceSegmentPool: Option[SequencePoolSearchFilter] = None,
  operationKinds: Option[OperationKindsSearchFilter] = None,
  questionIsOpen: Option[QuestionIsOpenSearchFilter] = None,
  segment: Option[SegmentSearchFilter] = None,
  userTypes: Option[UserTypesSearchFilter] = None,
  zone: Option[ZoneSearchFilter] = None,
  segmentZone: Option[ZoneSearchFilter] = None,
  minScoreLowerBound: Option[MinScoreLowerBoundSearchFilter] = None,
  keywords: Option[KeywordsSearchFilter] = None
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
    userTypes: Option[UserTypesSearchFilter] = None,
    zone: Option[ZoneSearchFilter] = None,
    segmentZone: Option[ZoneSearchFilter] = None,
    minScoreLowerBound: Option[MinScoreLowerBoundSearchFilter] = None,
    keywords: Option[KeywordsSearchFilter] = None
  ): Option[SearchFilters] = {

    Seq[Option[Any]](
      proposals,
      initialProposal,
      tags,
      labels,
      operation,
      question,
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
      userTypes,
      zone,
      segmentZone,
      minScoreLowerBound,
      keywords
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
            userTypes,
            zone,
            segmentZone,
            minScoreLowerBound,
            keywords
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
      buildUserTypesSearchFilter(searchQuery.filters),
      buildSegmentZoneSearchFilter(searchQuery.filters),
      buildZoneSearchFilter(searchQuery.filters),
      buildMinScoreLowerBoundSearchFilter(searchQuery.filters),
      buildKeywordsSearchFilter(searchQuery.filters)
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
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.id.field, proposalId.value))
        case Some(ProposalSearchFilter(proposalIds)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldName.id.field, proposalIds.map(_.value)))
        case _ => None
      }
    }
  }

  def buildQuestionSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.question match {
        case Some(QuestionSearchFilter(Seq(questionId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.questionId.field, questionId.value))
        case Some(QuestionSearchFilter(questionIds)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldName.questionId.field, questionIds.map(_.value)))
        case _ => None
      }
    }
  }

  def buildUserSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.users match {
        case Some(UserSearchFilter(Seq(userId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.userId.field, userId.value))
        case Some(UserSearchFilter(userIds)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldName.userId.field, userIds.map(_.value)))
        case _ => None
      }
    }
  }

  def buildInitialProposalSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.initialProposal.map { initialProposal =>
        ElasticApi.termQuery(
          field = ProposalElasticsearchFieldName.initialProposal.field,
          value = initialProposal.isInitialProposal
        )
      }
    }
  }

  def buildTagsSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.tags match {
        case Some(TagsSearchFilter(Seq(tagId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.tagId.field, tagId.value))
        case Some(TagsSearchFilter(tags)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldName.tagId.field, tags.map(_.value)))
        case _ => None
      }
    }
  }

  def buildLabelsSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.labels match {
        case Some(LabelsSearchFilter(labels)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldName.labels.field, labels.map(_.value)))
        case _ => None
      }
    }
  }

  def buildOperationSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.operation match {
        case Some(OperationSearchFilter(Seq(operationId))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.operationId.field, operationId.value))
        case Some(OperationSearchFilter(operationIds)) =>
          Some(
            ElasticApi
              .termsQuery(ProposalElasticsearchFieldName.operationId.field, operationIds.map(_.value))
          )
        case _ => None
      }
    }
  }

  def buildContextOperationSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    val operationFilter: Option[Query] = for {
      filters   <- filters
      context   <- filters.context
      operation <- context.operation
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldName.contextOperation.field, operation.value)

    operationFilter
  }

  def buildContextSourceSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    val sourceFilter: Option[Query] = for {
      filters <- filters
      context <- filters.context
      source  <- context.source
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldName.contextSource.field, source)

    sourceFilter
  }

  def buildContextLocationSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    val locationFilter: Option[Query] = for {
      filters  <- filters
      context  <- filters.context
      location <- context.location
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldName.contextLocation.field, location)

    locationFilter
  }

  def buildContextQuestionSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    val questionFilter: Option[Query] = for {
      filters  <- filters
      context  <- filters.context
      question <- context.question
    } yield ElasticApi.matchQuery(ProposalElasticsearchFieldName.contextQuestion.field, question)

    questionFilter
  }

  def buildSlugSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    val slugFilter: Option[Query] = for {
      filters    <- filters
      slugFilter <- filters.slug
    } yield ElasticApi.termQuery(ProposalElasticsearchFieldName.slug.field, slugFilter.slug)

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
          ProposalElasticsearchFieldName.content -> 3d,
          ProposalElasticsearchFieldName.contentFr -> 2d * languageOmission("fr"),
          ProposalElasticsearchFieldName.contentFrStemmed -> 1.5d * languageOmission("fr"),
          ProposalElasticsearchFieldName.contentEn -> 2d * languageOmission("en"),
          ProposalElasticsearchFieldName.contentEnStemmed -> 1.5d * languageOmission("en"),
          ProposalElasticsearchFieldName.contentIt -> 2d * languageOmission("it"),
          ProposalElasticsearchFieldName.contentItStemmed -> 1.5d * languageOmission("it"),
          ProposalElasticsearchFieldName.contentDe -> 2d * languageOmission("de"),
          ProposalElasticsearchFieldName.contentDeStemmed -> 1.5d * languageOmission("de"),
          ProposalElasticsearchFieldName.contentBg -> 2d * languageOmission("bg"),
          ProposalElasticsearchFieldName.contentBgStemmed -> 1.5d * languageOmission("bg"),
          ProposalElasticsearchFieldName.contentCs -> 2d * languageOmission("cs"),
          ProposalElasticsearchFieldName.contentCsStemmed -> 1.5d * languageOmission("cs"),
          ProposalElasticsearchFieldName.contentDa -> 2d * languageOmission("da"),
          ProposalElasticsearchFieldName.contentDaStemmed -> 1.5d * languageOmission("da"),
          ProposalElasticsearchFieldName.contentNl -> 2d * languageOmission("nl"),
          ProposalElasticsearchFieldName.contentNlStemmed -> 1.5d * languageOmission("nl"),
          ProposalElasticsearchFieldName.contentFi -> 2d * languageOmission("fi"),
          ProposalElasticsearchFieldName.contentFiStemmed -> 1.5d * languageOmission("fi"),
          ProposalElasticsearchFieldName.contentEl -> 2d * languageOmission("el"),
          ProposalElasticsearchFieldName.contentElStemmed -> 1.5d * languageOmission("el"),
          ProposalElasticsearchFieldName.contentHu -> 2d * languageOmission("hu"),
          ProposalElasticsearchFieldName.contentHuStemmed -> 1.5d * languageOmission("hu"),
          ProposalElasticsearchFieldName.contentLv -> 2d * languageOmission("lv"),
          ProposalElasticsearchFieldName.contentLvStemmed -> 1.5d * languageOmission("lv"),
          ProposalElasticsearchFieldName.contentLt -> 2d * languageOmission("lt"),
          ProposalElasticsearchFieldName.contentLtStemmed -> 1.5d * languageOmission("lt"),
          ProposalElasticsearchFieldName.contentPt -> 2d * languageOmission("pt"),
          ProposalElasticsearchFieldName.contentPtStemmed -> 1.5d * languageOmission("pt"),
          ProposalElasticsearchFieldName.contentRo -> 2d * languageOmission("ro"),
          ProposalElasticsearchFieldName.contentRoStemmed -> 1.5d * languageOmission("ro"),
          ProposalElasticsearchFieldName.contentEs -> 2d * languageOmission("es"),
          ProposalElasticsearchFieldName.contentEsStemmed -> 1.5d * languageOmission("es"),
          ProposalElasticsearchFieldName.contentSv -> 2d * languageOmission("sv"),
          ProposalElasticsearchFieldName.contentSvStemmed -> 1.5d * languageOmission("sv"),
          ProposalElasticsearchFieldName.contentPl -> 2d * languageOmission("pl"),
          ProposalElasticsearchFieldName.contentPlStemmed -> 1.5d * languageOmission("pl"),
          ProposalElasticsearchFieldName.contentHr -> 2d * languageOmission("hr"),
          ProposalElasticsearchFieldName.contentEt -> 2d * languageOmission("et"),
          ProposalElasticsearchFieldName.contentMt -> 2d * languageOmission("mt"),
          ProposalElasticsearchFieldName.contentSk -> 2d * languageOmission("sk"),
          ProposalElasticsearchFieldName.contentSl -> 2d * languageOmission("sl"),
          ProposalElasticsearchFieldName.contentGeneral -> 1d
        ).filter { case (_, boost) => boost != 0 }.map { case (field, boost) => (field.field, boost) }
      functionScoreQuery(multiMatchQuery(text).fields(fieldsBoosts).fuzziness("Auto:4,7").operator(Operator.AND))
        .functions(
          WeightScore(
            weight = 2d,
            filter = Some(MatchQuery(field = ProposalElasticsearchFieldName.questionIsOpen.field, value = true))
          )
        )

    }
  }

  def buildStatusSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    val query: Option[Query] = filters.flatMap {
      _.status.map {
        case StatusSearchFilter(Seq(proposalStatus)) =>
          ElasticApi.termQuery(ProposalElasticsearchFieldName.status.field, proposalStatus.value)
        case StatusSearchFilter(proposalStatuses) =>
          ElasticApi.termsQuery(ProposalElasticsearchFieldName.status.field, proposalStatuses.map(_.value))
      }
    }

    query match {
      case None =>
        Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.status.field, ProposalStatus.Accepted.value))
      case _ => query
    }
  }

  def buildIdeaSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.idea match {
        case Some(IdeaSearchFilter(Seq(idea))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.ideaId.field, idea.value))
        case Some(IdeaSearchFilter(ideas)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldName.ideaId.field, ideas.map(_.value)))
        case _ => None
      }
    }
  }

  def buildLanguageSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.language match {
        case Some(LanguageSearchFilter(language)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.questionLanguage.field, language.value))
        case _ => None
      }
    }
  }

  def buildCountrySearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.country match {
        case Some(CountrySearchFilter(country)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.questionCountries.field, country.value))
        case _ => None
      }
    }
  }

  def buildMinVotesCountSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.minVotesCount match {
        case Some(MinVotesCountSearchFilter(minVotesCount)) =>
          Some(ElasticApi.rangeQuery(ProposalElasticsearchFieldName.votesCount.field).gte(minVotesCount))
        case _ => None
      }
    }
  }

  def buildToEnrichSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.toEnrich match {
        case Some(ToEnrichSearchFilter(toEnrich)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.toEnrich.field, toEnrich))
        case _ => None
      }
    }
  }

  def buildMinScoreSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.minScore match {
        case Some(MinScoreSearchFilter(minScore)) =>
          Some(ElasticApi.rangeQuery(ProposalElasticsearchFieldName.scoreUpperBound.field).gte(minScore))
        case _ => None
      }
    }
  }

  def buildCreatedAtSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.createdAt match {
        case Some(CreatedAtSearchFilter(maybeBefore, maybeAfter)) =>
          val createdAtRangeQuery: RangeQuery = ElasticApi.rangeQuery(ProposalElasticsearchFieldName.createdAt.field)
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
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.sequencePool.field, sequencePool.value))
        case _ => None
      }
    }
  }

  def buildSequenceSegmentPoolSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.sequenceSegmentPool match {
        case Some(SequencePoolSearchFilter(sequencePool)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.sequenceSegmentPool.field, sequencePool.value))
        case _ => None
      }
    }
  }

//TODO: To avoid a pattern matching to precise, can't we just use `termsQuery` ?
  def buildOperationKindSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.operationKinds match {
        case Some(OperationKindsSearchFilter(Seq(operationKind))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.operationKind.field, operationKind.value))
        case Some(OperationKindsSearchFilter(operationKinds)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldName.operationKind.field, operationKinds.map(_.value)))
        case _ => None
      }
    }
  }

  def buildQuestionIsOpenSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.questionIsOpen match {
        case Some(QuestionIsOpenSearchFilter(isOpen)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.questionIsOpen.field, isOpen))
        case _ => None
      }
    }
  }

  def buildSegmentSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.segment match {
        case Some(SegmentSearchFilter(segment)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.segment.field, segment))
        case _ => None
      }
    }
  }

  def buildUserTypesSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.userTypes match {
        case Some(UserTypesSearchFilter(Seq(userType))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.authorUserType.field, userType.value))
        case Some(UserTypesSearchFilter(userTypes)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldName.authorUserType.field, userTypes.map(_.value)))
        case _ => None
      }
    }
  }

  def buildZoneSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.zone match {
        case Some(ZoneSearchFilter(zone)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.zone.field, zone.entryName.toLowerCase()))
        case _ => None
      }
    }
  }

  def buildSegmentZoneSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.segmentZone match {
        case Some(ZoneSearchFilter(zone)) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.segmentZone.field, zone.entryName.toLowerCase()))
        case _ => None
      }
    }
  }

  def buildMinScoreLowerBoundSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.minScoreLowerBound match {
        case Some(MinScoreLowerBoundSearchFilter(minLowerBound)) =>
          Some(ElasticApi.rangeQuery(ProposalElasticsearchFieldName.scoreLowerBound.field).gte(minLowerBound))
        case _ => None
      }
    }
  }

  def buildKeywordsSearchFilter(filters: Option[SearchFilters]): Option[Query] = {
    filters.flatMap {
      _.keywords match {
        case Some(KeywordsSearchFilter(Seq(keywordKey))) =>
          Some(ElasticApi.termQuery(ProposalElasticsearchFieldName.keywordKey.field, keywordKey.value))
        case Some(KeywordsSearchFilter(keywordsKeys)) =>
          Some(ElasticApi.termsQuery(ProposalElasticsearchFieldName.keywordKey.field, keywordsKeys.map(_.value)))
        case _ => None
      }
    }
  }
}

final case class ProposalSearchFilter(proposalIds: Seq[ProposalId])

final case class UserSearchFilter(userIds: Seq[UserId])

final case class InitialProposalFilter(isInitialProposal: Boolean)

final case class QuestionSearchFilter(questionIds: Seq[QuestionId])

final case class TagsSearchFilter(tagIds: Seq[TagId]) {
  validate(validateField("tagId", "mandatory", tagIds.nonEmpty, "ids cannot be empty in tag search filters"))
}

final case class LabelsSearchFilter(labelIds: Seq[LabelId]) {
  validate(validateField("labelIds", "mandatory", labelIds.nonEmpty, "ids cannot be empty in label search filters"))
}

final case class OperationSearchFilter(operationIds: Seq[OperationId])

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

final case class CountrySearchFilter(country: Country)
final case class LanguageSearchFilter(language: Language)
final case class MinVotesCountSearchFilter(minVotesCount: Int)
final case class ToEnrichSearchFilter(toEnrich: Boolean)
final case class MinScoreSearchFilter(minScore: Double)
final case class SequencePoolSearchFilter(sequencePool: SequencePool)
final case class OperationKindsSearchFilter(kinds: Seq[OperationKind])
final case class QuestionIsOpenSearchFilter(isOpen: Boolean)
final case class SegmentSearchFilter(segment: String)
final case class UserTypesSearchFilter(userTypes: Seq[UserType])
final case class ZoneSearchFilter(zone: Zone)
final case class MinScoreLowerBoundSearchFilter(minLowerBound: Double)
final case class KeywordsSearchFilter(keywordsKeys: Seq[ProposalKeywordKey])
