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

package org.make.api.proposal

import cats.data.NonEmptyList
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.searches.{SearchRequest, SearchResponse}
import com.sksamuel.elastic4s.requests.searches.aggs.responses.bucket.{TermBucket, Terms}
import com.sksamuel.elastic4s.requests.searches.term.TermQuery
import com.sksamuel.elastic4s.requests.script.Script
import com.sksamuel.elastic4s.requests.searches.aggs.{
  AbstractAggregation,
  FilterAggregation,
  GlobalAggregation,
  MaxAggregation,
  PercentilesAggregation,
  TermsAggregation,
  TopHitsAggregation
}
import com.sksamuel.elastic4s.requests.searches.aggs.pipeline.BucketSortPipelineAgg
import com.sksamuel.elastic4s.requests.searches.aggs.responses.metrics.TopHits
import com.sksamuel.elastic4s.requests.searches.queries.{ExistsQuery, Query}
import com.sksamuel.elastic4s.requests.searches.queries.compound.BoolQuery
import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
import grizzled.slf4j.Logging
import org.make.api.question.{AvatarsAndProposalsCount, PopularTagResponse}
import org.make.api.technical.elasticsearch.{ElasticsearchConfigurationComponent, _}
import org.make.core.DateHelper._
import org.make.core.idea.IdeaId
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.VoteKey.{Agree, Disagree}
import org.make.core.proposal._
import org.make.core.proposal.indexed.{
  IndexedProposal,
  ProposalElasticsearchFieldName,
  ProposalsSearchResult,
  SequencePool,
  Zone
}
import org.make.core.question.QuestionId
import org.make.core.tag.TagId
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ProposalSearchEngineComponent {
  def elasticsearchProposalAPI: ProposalSearchEngine
}

//TODO: add multi-country
trait ProposalSearchEngine {
  def findProposalById(proposalId: ProposalId): Future[Option[IndexedProposal]]

  def searchProposals(searchQuery: SearchQuery): Future[ProposalsSearchResult]

  def countProposals(searchQuery: SearchQuery): Future[Long]

  def countProposalsByQuestion(
    maybeQuestionIds: Option[Seq[QuestionId]],
    status: Option[Seq[ProposalStatus]],
    maybeUserId: Option[UserId],
    toEnrich: Option[Boolean],
    minVotesCount: Option[Int],
    minScore: Option[Double]
  ): Future[Map[QuestionId, Long]]

  def countVotedProposals(searchQuery: SearchQuery): Future[Int]

  def proposalTrendingMode(proposal: IndexedProposal): Option[String]

  def indexProposals(records: Seq[IndexedProposal], mayBeIndex: Option[Index] = None): Future[Seq[IndexedProposal]]

  def updateProposals(records: Seq[IndexedProposal], mayBeIndex: Option[Index] = None): Future[Seq[IndexedProposal]]

  def getPopularTagsByProposal(questionId: QuestionId, size: Int): Future[Seq[PopularTagResponse]]

  def getTopProposals(
    questionId: QuestionId,
    size: Int,
    aggregationField: ProposalElasticsearchFieldName
  ): Future[Seq[IndexedProposal]]

  def countProposalsByIdea(ideaIds: Seq[IdeaId]): Future[Map[IdeaId, Long]]

  def getRandomProposalsByIdeaWithAvatar(ideaIds: Seq[IdeaId], seed: Int): Future[Map[IdeaId, AvatarsAndProposalsCount]]

  def computeTop20ConsensusThreshold(questionIds: NonEmptyList[QuestionId]): Future[Map[QuestionId, Double]]

  def getFeaturedProposals(searchQuery: SearchQuery): Future[ProposalsSearchResult]
}

object ProposalSearchEngine {
  val proposalIndexName: String = "proposal"
}

trait DefaultProposalSearchEngineComponent extends ProposalSearchEngineComponent {
  self: ElasticsearchConfigurationComponent with ElasticsearchClientComponent =>

  override lazy val elasticsearchProposalAPI: DefaultProposalSearchEngine = new DefaultProposalSearchEngine

  class DefaultProposalSearchEngine extends ProposalSearchEngine with Logging {

    private lazy val client = elasticsearchClient.client

    private val proposalAlias: Index =
      elasticsearchConfiguration.proposalAliasName

    override def findProposalById(proposalId: ProposalId): Future[Option[IndexedProposal]] = {
      client.executeAsFuture(get(proposalAlias, proposalId.value)).map(_.toOpt[IndexedProposal])
    }

    override def searchProposals(searchQuery: SearchQuery): Future[ProposalsSearchResult] =
      constructSearchQuery(searchQuery)

    override def getFeaturedProposals(searchQuery: SearchQuery): Future[ProposalsSearchResult] =
      constructSearchQuery(searchQuery.copy(sortAlgorithm = Some(Featured)))

    private def constructSearchQuery(searchQuery: SearchQuery): Future[ProposalsSearchResult] = {
      // parse json string to build search query
      val searchFilters = SearchFilters.getSearchFilters(searchQuery)
      val excludesFilters = SearchFilters.getExcludeFilters(searchQuery)
      var request: SearchRequest = search(proposalAlias)
        .bool(BoolQuery(must = searchFilters, not = excludesFilters))
        .sortBy(SearchFilters.getSort(searchQuery).toList)
        .from(SearchFilters.getSkipSearch(searchQuery))
        .trackTotalHits(true)

      request = request.size(SearchFilters.getLimitSearch(searchQuery))

      searchQuery.sortAlgorithm.foreach { sortAlgorithm =>
        request = sortAlgorithm.sortDefinition(request)
      }

      client
        .executeAsFuture(request)
        .map(response => ProposalsSearchResult(total = response.totalHits, results = response.to[IndexedProposal]))
    }

    override def countProposals(searchQuery: SearchQuery): Future[Long] = {
      // parse json string to build search query
      val searchFilters = SearchFilters.getSearchFilters(searchQuery)

      val request = search(proposalAlias)
        .bool(BoolQuery(must = searchFilters))
        .limit(0)
        .trackTotalHits(true)

      client.executeAsFuture(request).map { response =>
        response.totalHits
      }

    }

    override def countProposalsByQuestion(
      maybeQuestionIds: Option[Seq[QuestionId]],
      status: Option[Seq[ProposalStatus]],
      maybeUserId: Option[UserId],
      toEnrich: Option[Boolean],
      minVotesCount: Option[Int],
      minScore: Option[Double]
    ): Future[Map[QuestionId, Long]] = {
      val searchQuery: SearchQuery = SearchQuery(filters = Some(
        SearchFilters(
          question = maybeQuestionIds.map(QuestionSearchFilter.apply),
          status = status.map(StatusSearchFilter.apply),
          users = maybeUserId.map(userId => UserSearchFilter(Seq(userId))),
          toEnrich = toEnrich.map(ToEnrichSearchFilter.apply),
          minVotesCount = minVotesCount.map(MinVotesCountSearchFilter.apply),
          minScore = minScore.map(MinScoreSearchFilter.apply)
        )
      )
      )
      val searchFilters: Seq[Query] = SearchFilters.getSearchFilters(searchQuery)
      val request: SearchRequest = search(proposalAlias).bool(BoolQuery(must = searchFilters))
      val questionAggrSize: Int = maybeQuestionIds.map(_.length + 1).getOrElse(10000)

      val finalRequest: SearchRequest = request
        .aggregations(
          termsAgg(name = "questions", field = ProposalElasticsearchFieldName.questionId.field)
            .size(size = questionAggrSize)
            .minDocCount(min = 1)
        )
        .limit(0)
        .trackTotalHits(true)

      client.executeAsFuture(finalRequest).map { response =>
        response.aggregations
          .result[Terms]("questions")
          .buckets
          .map(termBucket => QuestionId(termBucket.key) -> termBucket.docCount)
          .toMap
      }

    }

    override def countVotedProposals(searchQuery: SearchQuery): Future[Int] = {
      // parse json string to build search query
      val searchFilters = SearchFilters.getSearchFilters(searchQuery)

      val request = search(proposalAlias)
        .bool(BoolQuery(must = searchFilters))
        .aggregations(sumAgg("total_votes", "votes.count"))
        .trackTotalHits(true)

      client.executeAsFuture(request).map { response =>
        response.aggregations.sum("total_votes").valueOpt.map(_.toInt).getOrElse(0)
      }
    }

    override def proposalTrendingMode(proposal: IndexedProposal): Option[String] = {
      val totalVotes: Int = proposal.votes.map(_.count).sum
      val agreementRate: Float = BaseVote.rate(proposal.votes, Agree).toFloat
      val disagreementRate: Float = BaseVote.rate(proposal.votes, Disagree).toFloat

      val ruleControversial: Boolean = totalVotes >= 50 && agreementRate >= 0.4f && disagreementRate >= 0.4f
      val rulePopular: Boolean = totalVotes >= 50 && agreementRate >= 0.8f

      if (rulePopular) {
        Some("popular")
      } else if (ruleControversial) {
        Some("controversial")
      } else {
        None
      }
    }

    override def indexProposals(
      proposals: Seq[IndexedProposal],
      mayBeIndex: Option[Index] = None
    ): Future[Seq[IndexedProposal]] = {
      @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
      val records = proposals
        .groupBy(_.id)
        .map {
          case (_, duplicatedProposals) =>
            val proposal = duplicatedProposals.maxBy(_.updatedAt)
            proposal.copy(trending = proposalTrendingMode(proposal))
        }
      val index = mayBeIndex.getOrElse(proposalAlias)
      client
        .executeAsFuture(bulk(records.map { record =>
          indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.id.value)
        }))
        .map(_ => records.toSeq)
    }

    override def updateProposals(
      proposals: Seq[IndexedProposal],
      mayBeIndex: Option[Index] = None
    ): Future[Seq[IndexedProposal]] = {
      @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
      val records = proposals
        .groupBy(_.id)
        .map {
          case (_, duplicatedProposals) =>
            val proposal = duplicatedProposals.maxBy(_.updatedAt)
            proposal.copy(trending = proposalTrendingMode(proposal))
        }
      val index = mayBeIndex.getOrElse(proposalAlias)
      client
        .executeAsFuture(bulk(records.map { record =>
          updateById(index, record.id.value)
            .doc(record)
            .refresh(RefreshPolicy.IMMEDIATE)
        }))
        .map(_ => records.toSeq)
    }

    override def getPopularTagsByProposal(questionId: QuestionId, size: Int): Future[Seq[PopularTagResponse]] = {
      // parse json string to build search query
      val searchQuery: SearchQuery =
        SearchQuery(filters = Some(SearchFilters(question = Some(QuestionSearchFilter(Seq(questionId))))))
      val searchFilters: Seq[Query] = SearchFilters.getSearchFilters(searchQuery)
      val request: SearchRequest = search(proposalAlias).bool(BoolQuery(must = searchFilters))

      val finalRequest: SearchRequest = request
        .aggregations(
          TermsAggregation(
            name = "popularTags",
            script = Some(
              Script(
                "def tags = params._source['tags'];tags.removeIf(tag -> !tag['display']);String[] idLabel = new String[tags.length]; for (int i = 0; i < tags.length; i++) { idLabel[i] = tags[i]['tagId'] + ',' + tags[i]['label'] } return idLabel"
              )
            )
          ).size(size = size)
        )
        .limit(0)
        .trackTotalHits(true)

      def popularTagResponseFrombucket(bucket: TermBucket): PopularTagResponse = {
        val Array(tagId, label) = bucket.key.split(",", 2)
        PopularTagResponse(TagId(tagId), label, bucket.docCount)
      }

      client.executeAsFuture(finalRequest).map { response =>
        response.aggregations
          .result[Terms]("popularTags")
          .buckets
          .map(popularTagResponseFrombucket)
      }

    }

    override def getTopProposals(
      questionId: QuestionId,
      size: Int,
      aggregationField: ProposalElasticsearchFieldName
    ): Future[Seq[IndexedProposal]] = {
      val topHitsAggregationName = "topHits"
      val termsAggregationName = "termsAgg"
      val maxAggregationName = "maxTopScore"

      val searchQuery: SearchQuery =
        SearchQuery(filters = Some(SearchFilters(question = Some(QuestionSearchFilter(Seq(questionId))))))
      val searchFilters: Seq[Query] = SearchFilters.getSearchFilters(searchQuery)
      val request: SearchRequest = search(proposalAlias).bool(BoolQuery(must = searchFilters))

      // This aggregation create a field "maxTopScore" with the max value of indexedProposal.scores.topScore
      val maxAggregation: AbstractAggregation =
        MaxAggregation(name = maxAggregationName, field = Some(ProposalElasticsearchFieldName.scoreLowerBound.field))

      // This aggregation sort each bucket from the field "maxTopScore"
      val bucketSortAggregation: AbstractAggregation = BucketSortPipelineAgg(
        name = "topScoreBucketSort",
        sort = Seq(FieldSort(field = maxAggregationName, order = SortOrder.DESC))
      )

      // This aggregation take the proposal with the highest indexedProposal.scores.topScore on each bucket
      val topHitsAggregation: AbstractAggregation =
        TopHitsAggregation(
          name = topHitsAggregationName,
          sorts = Seq(FieldSort(field = ProposalElasticsearchFieldName.scoreLowerBound.field, order = SortOrder.DESC)),
          size = Some(1)
        )

      val finalRequest: SearchRequest = request
        .aggregations(
          TermsAggregation(name = termsAggregationName, field = Some(aggregationField.field), size = Some(size))
            .subAggregations(Seq(maxAggregation, bucketSortAggregation, topHitsAggregation)) // Those 3 subAggregation are execute on each bucket created by the parent aggregation
            .minDocCount(min = 1)
        )
        .size(0)
        .trackTotalHits(true)

      client.executeAsFuture(finalRequest).map { response =>
        response.aggregations
          .result[Terms](termsAggregationName)
          .buckets
          .flatMap(_.result[TopHits](topHitsAggregationName).hits.map(_.to[IndexedProposal]))
      }
    }

    override def countProposalsByIdea(ideaIds: Seq[IdeaId]): Future[Map[IdeaId, Long]] = {
      val searchFilters = SearchFilters.getSearchFilters(
        SearchQuery(filters = Some(SearchFilters(idea = Some(IdeaSearchFilter(ideaIds)))))
      )

      val request = search(proposalAlias)
        .bool(BoolQuery(must = searchFilters))
        .aggregations(termsAgg("by_idea", ProposalElasticsearchFieldName.ideaId.field))
        .size(0)
        .trackTotalHits(true)

      client.executeAsFuture(request).map { response =>
        response.aggregations
          .result[Terms]("by_idea")
          .buckets
          .map(termBucket => IdeaId(termBucket.key) -> termBucket.docCount)
          .toMap
      }

    }

    override def getRandomProposalsByIdeaWithAvatar(
      ideaIds: Seq[IdeaId],
      seed: Int
    ): Future[Map[IdeaId, AvatarsAndProposalsCount]] = {
      val avatarsSize = 4

      // this aggregation count the proposals without taking account of the search filters set for the bool query
      val globalAggregation = GlobalAggregation(name = "all_proposals")
        .subAggregations(
          FilterAggregation(
            name = "filter_global",
            query = TermQuery(field = ProposalElasticsearchFieldName.status.field, value = Accepted.value)
          ).subAggregations(
            TermsAggregation(
              name = "by_idea_global",
              field = Some(ProposalElasticsearchFieldName.ideaId.field),
              includeExactValues = ideaIds.map(_.value),
              size = Some(ideaIds.size)
            )
          )
        )

      val topHitsAggregation =
        TopHitsAggregation(
          name = "top_proposals",
          sorts = Seq(FieldSort(field = "_score", order = SortOrder.DESC)),
          size = Some(avatarsSize)
        )

      var request = search(proposalAlias)
        .bool(BoolQuery(must = Seq(ExistsQuery(field = ProposalElasticsearchFieldName.authorAvatarUrl.field))))
        .aggregations(
          TermsAggregation(
            name = "by_idea",
            field = Some(ProposalElasticsearchFieldName.ideaId.field),
            includeExactValues = ideaIds.map(_.value),
            size = Some(ideaIds.size)
          ).subAggregations(topHitsAggregation),
          globalAggregation
        )
        .size(0)
        .trackTotalHits(true)

      request = RandomAlgorithm(seed).sortDefinition(request)

      ideaIds match {
        case Seq() => Future.successful(Map.empty)
        case _ =>
          client.executeAsFuture(request).map { response =>
            computeAvatarAndProposalsCountResponse(response)
          }
      }
    }

    private def computeAvatarAndProposalsCountResponse(
      response: SearchResponse
    ): Map[IdeaId, AvatarsAndProposalsCount] = {
      val proposalsCountByIdea: Map[String, Long] = response.aggregations
        .global("all_proposals")
        .filter("filter_global")
        .result[Terms]("by_idea_global")
        .buckets
        .map(bucket => bucket.key -> bucket.docCount)
        .toMap
      val avatarsByIdea: Map[String, Seq[String]] = response.aggregations
        .result[Terms]("by_idea")
        .buckets
        .map(
          bucket =>
            bucket.key -> bucket
              .result[TopHits]("top_proposals")
              .hits
              .map(_.to[IndexedProposal])
              .map(_.author.avatarUrl.getOrElse(""))
        )
        .toMap
      proposalsCountByIdea.map {
        case (ideaId, count) =>
          IdeaId(ideaId) -> AvatarsAndProposalsCount(
            avatars = avatarsByIdea.getOrElse(ideaId, Seq.empty),
            proposalsCount = count.toInt
          )
      }
    }

    override def computeTop20ConsensusThreshold(
      questionIds: NonEmptyList[QuestionId]
    ): Future[Map[QuestionId, Double]] = {
      val searchFilters = SearchFilters.getSearchFilters(
        SearchQuery(filters = Some(
          SearchFilters(
            question = Some(QuestionSearchFilter(questionIds.toList)),
            sequencePool = Some(SequencePoolSearchFilter(SequencePool.Tested)),
            zone = Some(ZoneSearchFilter(Zone.Consensus))
          )
        )
        )
      )

      val consensusOutlier = PercentilesAggregation(
        name = "consensus_outlier",
        field = Some(ProposalElasticsearchFieldName.scoreLowerBound.field),
        percents = Seq(80d)
      )
      val aggByQuestion = TermsAggregation(
        name = "by_question",
        field = Some(ProposalElasticsearchFieldName.questionId.field),
        size = Some(questionIds.size),
        subaggs = Seq(consensusOutlier)
      )
      val request = search(proposalAlias)
        .bool(BoolQuery(must = searchFilters))
        .aggregations(aggByQuestion)
        .limit(0)
        .trackTotalHits(true)

      client.executeAsFuture(request).map { response =>
        response.aggregations
          .result[Terms](aggByQuestion.name)
          .buckets
          .flatMap { termBucket =>
            termBucket.percentiles(consensusOutlier.name).values.get("80.0").map(QuestionId(termBucket.key) -> _)
          }
          .toMap
      }

    }

  }

}
