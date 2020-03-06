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

import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.search.{SearchResponse, TermBucket}
import com.sksamuel.elastic4s.script.Script
import com.sksamuel.elastic4s.searches.aggs.pipeline.BucketSortPipelineAgg
import com.sksamuel.elastic4s.searches.aggs.{
  FilterAggregation,
  GlobalAggregation,
  MaxAggregation,
  TermsAggregation,
  TopHitsAggregation
}
import com.sksamuel.elastic4s.searches.queries.funcscorer.FunctionScoreQuery
import com.sksamuel.elastic4s.searches.queries.term.TermQuery
import com.sksamuel.elastic4s.searches.queries.{BoolQuery, ExistsQuery, IdQuery, Query}
import com.sksamuel.elastic4s.searches.sort.{FieldSort, SortOrder}
import com.sksamuel.elastic4s.searches.{IncludeExclude, SearchRequest => ElasticSearchRequest}
import com.sksamuel.elastic4s.{IndexAndType, RefreshPolicy}
import com.typesafe.scalalogging.StrictLogging
import org.make.api.question.{AvatarsAndProposalsCount, PopularTagResponse}
import org.make.api.technical.elasticsearch.{ElasticsearchConfigurationComponent, _}
import org.make.core.DateHelper
import org.make.core.DateHelper._
import org.make.core.idea.IdeaId
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.VoteKey.{Agree, Disagree}
import org.make.core.proposal._
import org.make.core.proposal.indexed.{IndexedProposal, ProposalElasticsearchFieldNames, ProposalsSearchResult}
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
  def findProposalsByIds(proposalIds: Seq[ProposalId], size: Int, random: Boolean = true): Future[Seq[IndexedProposal]]
  def searchProposals(searchQuery: SearchQuery): Future[ProposalsSearchResult]
  def countProposals(searchQuery: SearchQuery): Future[Long]
  def countProposalsByQuestion(maybeQuestionIds: Option[Seq[QuestionId]],
                               status: Option[Seq[ProposalStatus]],
                               maybeUserId: Option[UserId]): Future[Map[QuestionId, Long]]
  def countVotedProposals(searchQuery: SearchQuery): Future[Int]
  def proposalTrendingMode(proposal: IndexedProposal): Option[String]
  def indexProposals(records: Seq[IndexedProposal],
                     mayBeIndex: Option[IndexAndType] = None): Future[Seq[IndexedProposal]]
  def updateProposals(records: Seq[IndexedProposal],
                      mayBeIndex: Option[IndexAndType] = None): Future[Seq[IndexedProposal]]
  def getPopularTagsByProposal(questionId: QuestionId, size: Int): Future[Seq[PopularTagResponse]]
  def getTopProposals(questionId: QuestionId, size: Int, aggregationField: String): Future[Seq[IndexedProposal]]
  def countProposalsByIdea(ideaIds: Seq[IdeaId]): Future[Map[IdeaId, Long]]
  def getRandomProposalsByIdeaWithAvatar(ideaIds: Seq[IdeaId], seed: Int): Future[Map[IdeaId, AvatarsAndProposalsCount]]
}

object ProposalSearchEngine {
  val proposalIndexName: String = "proposal"
}

trait DefaultProposalSearchEngineComponent extends ProposalSearchEngineComponent {
  self: ElasticsearchConfigurationComponent with ElasticsearchClientComponent =>

  override lazy val elasticsearchProposalAPI: DefaultProposalSearchEngine = new DefaultProposalSearchEngine

  class DefaultProposalSearchEngine extends ProposalSearchEngine with StrictLogging {

    private lazy val client = elasticsearchClient.client

    private val proposalAlias: IndexAndType =
      elasticsearchConfiguration.proposalAliasName / ProposalSearchEngine.proposalIndexName

    override def findProposalById(proposalId: ProposalId): Future[Option[IndexedProposal]] = {
      client.executeAsFuture(get(id = proposalId.value).from(proposalAlias)).map(_.toOpt[IndexedProposal])
    }

    override def findProposalsByIds(proposalIds: Seq[ProposalId],
                                    size: Int,
                                    random: Boolean = true): Future[Seq[IndexedProposal]] = {

      val seed: Int = DateHelper.now().toEpochSecond.toInt

      val query: IdQuery = idsQuery(ids = proposalIds.map(_.value)).types("proposal")
      val randomQuery: FunctionScoreQuery =
        functionScoreQuery(idsQuery(ids = proposalIds.map(_.value)).types("proposal")).functions(Seq(randomScore(seed)))

      val request: ElasticSearchRequest = searchWithType(proposalAlias)
        .query(if (random) randomQuery else query)
        .size(size)

      client.executeAsFuture(request).map {
        _.to[IndexedProposal]
      }
    }

    override def searchProposals(searchQuery: SearchQuery): Future[ProposalsSearchResult] = {
      // parse json string to build search query
      val searchFilters = SearchFilters.getSearchFilters(searchQuery)
      val excludesFilters = SearchFilters.getExcludeFilters(searchQuery)
      var request: ElasticSearchRequest = searchWithType(proposalAlias)
        .bool(BoolQuery(must = searchFilters, not = excludesFilters))
        .sortBy(SearchFilters.getSort(searchQuery))
        .from(SearchFilters.getSkipSearch(searchQuery))

      request = request.size(SearchFilters.getLimitSearch(searchQuery))

      searchQuery.sortAlgorithm.foreach { sortAlgorithm =>
        request = sortAlgorithm.sortDefinition(request)
      }

      client.executeAsFuture(request).map { response =>
        ProposalsSearchResult(total = response.totalHits, results = response.to[IndexedProposal])
      }

    }

    override def countProposals(searchQuery: SearchQuery): Future[Long] = {
      // parse json string to build search query
      val searchFilters = SearchFilters.getSearchFilters(searchQuery)

      val request = searchWithType(proposalAlias)
        .bool(BoolQuery(must = searchFilters))
        .limit(0)

      client.executeAsFuture(request).map { response =>
        response.totalHits
      }

    }

    override def countProposalsByQuestion(maybeQuestionIds: Option[Seq[QuestionId]],
                                          status: Option[Seq[ProposalStatus]],
                                          maybeUserId: Option[UserId]): Future[Map[QuestionId, Long]] = {
      val searchQuery: SearchQuery = SearchQuery(
        filters = Some(
          SearchFilters(
            question = maybeQuestionIds.map(QuestionSearchFilter.apply),
            status = status.map(StatusSearchFilter.apply),
            user = maybeUserId.map(UserSearchFilter.apply)
          )
        )
      )
      val searchFilters: Seq[Query] = SearchFilters.getSearchFilters(searchQuery)
      val request: ElasticSearchRequest = searchWithType(proposalAlias).bool(BoolQuery(must = searchFilters))
      val questionAggrSize: Int = maybeQuestionIds.map(_.length + 1).getOrElse(10000)

      val finalRequest: ElasticSearchRequest = request
        .aggregations(
          termsAgg(name = "questions", field = ProposalElasticsearchFieldNames.questionId)
            .size(size = questionAggrSize)
            .minDocCount(min = 1)
        )
        .limit(0)

      client.executeAsFuture(finalRequest).map { response =>
        response.aggregations
          .terms("questions")
          .buckets
          .map(termBucket => QuestionId(termBucket.key) -> termBucket.docCount)
          .toMap
      }

    }

    override def countVotedProposals(searchQuery: SearchQuery): Future[Int] = {
      // parse json string to build search query
      val searchFilters = SearchFilters.getSearchFilters(searchQuery)

      val request = searchWithType(proposalAlias)
        .bool(BoolQuery(must = searchFilters))
        .aggregations(sumAgg("total_votes", "votes.count"))

      client.executeAsFuture(request).map { response =>
        response.aggregations.sum("total_votes").valueOpt.map(_.toInt).getOrElse(0)
      }
    }

    override def proposalTrendingMode(proposal: IndexedProposal): Option[String] = {
      val totalVotes: Int = proposal.votes.map(_.count).sum
      val agreeVote: Int = proposal.votes.find(_.key == Agree).map(_.count).getOrElse(0)
      val disagreeVote: Int = proposal.votes.find(_.key == Disagree).map(_.count).getOrElse(0)
      val agreementRate: Float = agreeVote.toFloat / totalVotes.toFloat
      val disagreementRate: Float = disagreeVote.toFloat / totalVotes.toFloat

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

    override def indexProposals(proposals: Seq[IndexedProposal],
                                mayBeIndex: Option[IndexAndType] = None): Future[Seq[IndexedProposal]] = {
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

    override def updateProposals(proposals: Seq[IndexedProposal],
                                 mayBeIndex: Option[IndexAndType] = None): Future[Seq[IndexedProposal]] = {
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
          (update(id = record.id.value) in index).doc(record).refresh(RefreshPolicy.IMMEDIATE)
        }))
        .map(_ => records.toSeq)
    }

    override def getPopularTagsByProposal(questionId: QuestionId, size: Int): Future[Seq[PopularTagResponse]] = {
      // parse json string to build search query
      val searchQuery: SearchQuery = SearchQuery(
        filters = Some(SearchFilters(question = Some(QuestionSearchFilter(Seq(questionId)))))
      )
      val searchFilters: Seq[Query] = SearchFilters.getSearchFilters(searchQuery)
      val request: ElasticSearchRequest = searchWithType(proposalAlias).bool(BoolQuery(must = searchFilters))

      val finalRequest: ElasticSearchRequest = request
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
      def popularTagResponseFrombucket(bucket: TermBucket): PopularTagResponse = {
        val Array(tagId, label) = bucket.key.split(",", 2)
        PopularTagResponse(TagId(tagId), label, bucket.docCount)
      }

      client.executeAsFuture(finalRequest).map { response =>
        response.aggregations
          .terms("popularTags")
          .buckets
          .map(popularTagResponseFrombucket)
      }

    }

    override def getTopProposals(questionId: QuestionId,
                                 size: Int,
                                 aggregationField: String): Future[Seq[IndexedProposal]] = {
      val topHitsAggregationName = "topHits"
      val termsAggregationName = "termsAgg"
      val maxAggregationName = "maxTopScore"

      val searchQuery: SearchQuery = SearchQuery(
        filters = Some(SearchFilters(question = Some(QuestionSearchFilter(Seq(questionId)))))
      )
      val searchFilters: Seq[Query] = SearchFilters.getSearchFilters(searchQuery)
      val request: ElasticSearchRequest = searchWithType(proposalAlias).bool(BoolQuery(must = searchFilters))

      // This aggregation create a field "maxTopScore" with the max value of indexedProposal.scores.topScore
      val maxAggregation =
        MaxAggregation(
          name = maxAggregationName,
          field = Some(ProposalElasticsearchFieldNames.topScoreAjustedWithVotes)
        )

      // This aggregation sort each bucket from the field "maxTopScore"
      val bucketSortAggregation = BucketSortPipelineAgg(
        name = "topScoreBucketSort",
        sort = Seq(FieldSort(field = maxAggregationName, order = SortOrder.DESC))
      )

      // This aggregation take the proposal with the highest indexedProposal.scores.topScore on each bucket
      val topHitsAggregation =
        TopHitsAggregation(
          name = topHitsAggregationName,
          sorts =
            Seq(FieldSort(field = ProposalElasticsearchFieldNames.topScoreAjustedWithVotes, order = SortOrder.DESC)),
          size = Some(1)
        )

      val finalRequest: ElasticSearchRequest = request
        .aggregations(
          TermsAggregation(name = termsAggregationName, field = Some(aggregationField), size = Some(size))
            .subAggregations(Seq(maxAggregation, bucketSortAggregation, topHitsAggregation)) // Those 3 subAggregation are execute on each bucket created by the parent aggregation
            .minDocCount(min = 1)
        )
        .size(0)

      client.executeAsFuture(finalRequest).map { response =>
        response.aggregations
          .terms(termsAggregationName)
          .buckets
          .flatMap(_.tophits(topHitsAggregationName).hits.map(_.to[IndexedProposal]))
      }
    }

    override def countProposalsByIdea(ideaIds: Seq[IdeaId]): Future[Map[IdeaId, Long]] = {
      val searchFilters = SearchFilters.getSearchFilters(
        SearchQuery(filters = Some(SearchFilters(idea = Some(IdeaSearchFilter(ideaIds)))))
      )

      val request = searchWithType(proposalAlias)
        .bool(BoolQuery(must = searchFilters))
        .aggregations(termsAgg("by_idea", ProposalElasticsearchFieldNames.ideaId))
        .size(0)

      client.executeAsFuture(request).map { response =>
        response.aggregations
          .terms("by_idea")
          .buckets
          .map(termBucket => IdeaId(termBucket.key) -> termBucket.docCount)
          .toMap
      }

    }

    override def getRandomProposalsByIdeaWithAvatar(ideaIds: Seq[IdeaId],
                                                    seed: Int): Future[Map[IdeaId, AvatarsAndProposalsCount]] = {
      val avatarsSize = 4

      // this aggregation count the proposals without taking account of the search filters set for the bool query
      val globalAggregation = GlobalAggregation(name = "all_proposals")
        .subAggregations(
          FilterAggregation(
            name = "filter_global",
            query = TermQuery(field = ProposalElasticsearchFieldNames.status, value = Accepted.shortName)
          ).subAggregations(
            TermsAggregation(
              name = "by_idea_global",
              field = Some(ProposalElasticsearchFieldNames.ideaId),
              includeExclude = Some(IncludeExclude(include = ideaIds.map(_.value), exclude = Seq.empty)),
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

      var request = searchWithType(proposalAlias)
        .bool(BoolQuery(must = Seq(ExistsQuery(field = ProposalElasticsearchFieldNames.authorAvatarUrl))))
        .aggregations(
          TermsAggregation(
            name = "by_idea",
            field = Some(ProposalElasticsearchFieldNames.ideaId),
            includeExclude = Some(IncludeExclude(include = ideaIds.map(_.value), exclude = Seq.empty)),
            size = Some(ideaIds.size)
          ).subAggregations(topHitsAggregation),
          globalAggregation
        )
        .size(0)

      request = RandomAlgorithm(seed).sortDefinition(request)

      client.executeAsFuture(request).map { response =>
        computeAvatarAndProposalsCountResponse(response)
      }
    }

    private def computeAvatarAndProposalsCountResponse(
      response: SearchResponse
    ): Map[IdeaId, AvatarsAndProposalsCount] = {
      val proposalsCountByIdea: Map[String, Long] = response.aggregations
        .global("all_proposals")
        .filter("filter_global")
        .terms("by_idea_global")
        .buckets
        .map(bucket => bucket.key -> bucket.docCount)
        .toMap
      val avatarsByIdea: Map[String, Seq[String]] = response.aggregations
        .terms("by_idea")
        .buckets
        .map(
          bucket =>
            bucket.key -> bucket
              .tophits("top_proposals")
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
  }

}
