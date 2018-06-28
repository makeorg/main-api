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

import akka.Done
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.searches.SearchDefinition
import com.sksamuel.elastic4s.searches.queries.funcscorer.FunctionScoreQueryDefinition
import com.sksamuel.elastic4s.searches.queries.{BoolQueryDefinition, IdQuery}
import com.sksamuel.elastic4s.{ElasticsearchClientUri, IndexAndType, RefreshPolicy}
import com.typesafe.scalalogging.StrictLogging
import org.make.api.technical.elasticsearch.{ElasticsearchConfigurationComponent, _}
import org.make.core.DateHelper
import org.make.core.proposal.VoteKey.{Agree, Disagree}
import org.make.core.proposal._
import org.make.core.proposal.indexed.{IndexedProposal, ProposalsSearchResult}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ProposalSearchEngineComponent {
  def elasticsearchProposalAPI: ProposalSearchEngine
}

//TODO: add multi-country
trait ProposalSearchEngine {
  def findProposalById(proposalId: ProposalId): Future[Option[IndexedProposal]]
  def findProposalsByIds(proposalIds: Seq[ProposalId],
                         size: Option[Int] = None,
                         random: Boolean = true): Future[Seq[IndexedProposal]]
  def searchProposals(searchQuery: SearchQuery): Future[ProposalsSearchResult]
  def countProposals(searchQuery: SearchQuery): Future[Long]
  def countVotedProposals(searchQuery: SearchQuery): Future[Int]
  def proposalTrendingMode(proposal: IndexedProposal): Option[String]
  def indexProposal(record: IndexedProposal, mayBeIndex: Option[IndexAndType] = None): Future[Done]
  def updateProposal(record: IndexedProposal, mayBeIndex: Option[IndexAndType] = None): Future[Done]
}

object ProposalSearchEngine {
  val proposalIndexName: String = "proposal"
}

trait DefaultProposalSearchEngineComponent extends ProposalSearchEngineComponent {
  self: ElasticsearchConfigurationComponent =>

  override lazy val elasticsearchProposalAPI: ProposalSearchEngine = new ProposalSearchEngine with StrictLogging {

    private val client = HttpClient(
      ElasticsearchClientUri(s"elasticsearch://${elasticsearchConfiguration.connectionString}")
    )

    private val proposalAlias
      : IndexAndType = elasticsearchConfiguration.aliasName / ProposalSearchEngine.proposalIndexName

    override def findProposalById(proposalId: ProposalId): Future[Option[IndexedProposal]] = {
      client.executeAsFuture(get(id = proposalId.value).from(proposalAlias)).map(_.toOpt[IndexedProposal])
    }

    override def findProposalsByIds(proposalIds: Seq[ProposalId],
                                    size: Option[Int] = None,
                                    random: Boolean = true): Future[Seq[IndexedProposal]] = {

      val defaultMax: Int = 1000
      val seed: Int = DateHelper.now().toEpochSecond.toInt

      val query: IdQuery = idsQuery(ids = proposalIds.map(_.value)).types("proposal")
      val randomQuery: FunctionScoreQueryDefinition =
        functionScoreQuery(idsQuery(ids = proposalIds.map(_.value)).types("proposal")).functions(Seq(randomScore(seed)))

      val request: SearchDefinition = searchWithType(proposalAlias)
        .query(if (random) randomQuery else query)
        .size(size.getOrElse(defaultMax))

      logger.debug(client.show(request))

      client.executeAsFuture(request).map {
        _.to[IndexedProposal]
      }
    }

    override def searchProposals(searchQuery: SearchQuery): Future[ProposalsSearchResult] = {
      // parse json string to build search query
      val searchFilters = SearchFilters.getSearchFilters(searchQuery)
      var request: SearchDefinition = searchWithType(proposalAlias)
        .bool(BoolQueryDefinition(must = searchFilters))
        .sortBy(SearchFilters.getSort(searchQuery))
        .from(SearchFilters.getSkipSearch(searchQuery))

      request = request.size(SearchFilters.getLimitSearch(searchQuery))

      searchQuery.sortAlgorithm.foreach { sortAlgorithm =>
        request = sortAlgorithm.sortDefinition(request)
      }

      logger.debug(client.show(request))

      client.executeAsFuture(request).map { response =>
        ProposalsSearchResult(total = response.totalHits, results = response.to[IndexedProposal])
      }

    }

    override def countProposals(searchQuery: SearchQuery): Future[Long] = {
      // parse json string to build search query
      val searchFilters = SearchFilters.getSearchFilters(searchQuery)

      val request = searchWithType(proposalAlias)
        .bool(BoolQueryDefinition(must = searchFilters))

      logger.debug(client.show(request))

      client.executeAsFuture(request).map { response =>
        response.totalHits
      }

    }

    override def countVotedProposals(searchQuery: SearchQuery): Future[Int] = {
      // parse json string to build search query
      val searchFilters = SearchFilters.getSearchFilters(searchQuery)

      val request = searchWithType(proposalAlias)
        .bool(BoolQueryDefinition(must = searchFilters))
        .aggs {
          sumAgg("total_votes", "votes.count")
        }

      logger.debug(client.show(request))

      client.executeAsFuture(request).map { response =>
        response.aggregations.data("total_votes").asInstanceOf[Double].toInt
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

    override def indexProposal(proposal: IndexedProposal, mayBeIndex: Option[IndexAndType] = None): Future[Done] = {
      val record: IndexedProposal = proposal.copy(trending = proposalTrendingMode(proposal))
      val index = mayBeIndex.getOrElse(proposalAlias)
      logger.debug(s"$index -> Saving in Elasticsearch: $record")
      client
        .executeAsFuture(indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.id.value))
        .map(_ => Done)
    }

    override def updateProposal(record: IndexedProposal, mayBeIndex: Option[IndexAndType] = None): Future[Done] = {
      val index = mayBeIndex.getOrElse(proposalAlias)
      logger.debug(s"$index -> Updating in Elasticsearch: $record")
      client
        .executeAsFuture((update(id = record.id.value) in index).doc(record).refresh(RefreshPolicy.IMMEDIATE))
        .map(_ => Done)
    }
  }

}
