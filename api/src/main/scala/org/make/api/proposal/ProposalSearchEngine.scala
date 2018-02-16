package org.make.api.proposal

import akka.Done
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.http.search.SumAggregationResult
import com.sksamuel.elastic4s.searches.SearchDefinition
import com.sksamuel.elastic4s.searches.queries.funcscorer.FunctionScoreQueryDefinition
import com.sksamuel.elastic4s.searches.queries.{BoolQueryDefinition, IdQueryDefinition}
import com.sksamuel.elastic4s.{ElasticsearchClientUri, IndexAndType}
import com.typesafe.scalalogging.StrictLogging
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.make.api.technical.elasticsearch.ElasticsearchConfigurationComponent
import org.make.core.DateHelper
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
  def searchProposals(searchQuery: SearchQuery, maybeSeed: Option[Int] = None): Future[ProposalsSearchResult]
  def countProposals(searchQuery: SearchQuery): Future[Int]
  def countVotedProposals(searchQuery: SearchQuery): Future[Int]
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
      client.execute(get(id = proposalId.value).from(proposalAlias)).map(_.toOpt[IndexedProposal])
    }

    override def findProposalsByIds(proposalIds: Seq[ProposalId],
                                    size: Option[Int] = None,
                                    random: Boolean = true): Future[Seq[IndexedProposal]] = {

      val defaultMax: Int = 1000
      val seed: Int = DateHelper.now().toEpochSecond.toInt

      val query: IdQueryDefinition = idsQuery(ids = proposalIds.map(_.value)).types("proposal")
      val randomQuery: FunctionScoreQueryDefinition =
        functionScoreQuery(idsQuery(ids = proposalIds.map(_.value)).types("proposal")).scorers(Seq(randomScore(seed)))

      val request: SearchDefinition = search(proposalAlias)
        .query(if (random) randomQuery else query)
        .size(size.getOrElse(defaultMax))

      logger.debug(client.show(request))

      client.execute {
        request
      }.map {
        _.to[IndexedProposal]
      }
    }

    override def searchProposals(searchQuery: SearchQuery,
                                 maybeSeed: Option[Int] = None): Future[ProposalsSearchResult] = {
      // parse json string to build search query
      val searchFilters = SearchFilters.getSearchFilters(searchQuery)
      var request: SearchDefinition = search(proposalAlias)
        .bool(BoolQueryDefinition(must = searchFilters))
        .sortBy(SearchFilters.getSort(searchQuery))
        .from(SearchFilters.getSkipSearch(searchQuery))

      request = request.size(SearchFilters.getLimitSearch(searchQuery))

      request = (for {
        seed  <- maybeSeed
        query <- request.query
      } yield request.query(functionScoreQuery().query(query).scorers(randomScore(seed)))).getOrElse(request)

      logger.debug(client.show(request))

      client.execute {
        request
      }.map { response =>
        ProposalsSearchResult(total = response.totalHits, results = response.to[IndexedProposal])
      }

    }

    override def countProposals(searchQuery: SearchQuery): Future[Int] = {
      // parse json string to build search query
      val searchFilters = SearchFilters.getSearchFilters(searchQuery)

      val request = search(proposalAlias)
        .bool(BoolQueryDefinition(must = searchFilters))

      logger.debug(client.show(request))

      client.execute {
        request
      }.map { response =>
        response.totalHits
      }

    }

    override def countVotedProposals(searchQuery: SearchQuery): Future[Int] = {
      // parse json string to build search query
      val searchFilters = SearchFilters.getSearchFilters(searchQuery)

      val request = search(proposalAlias)
        .bool(BoolQueryDefinition(must = searchFilters))
        .aggs {
          sumAgg("total_votes", "votes.count")
        }

      logger.debug(client.show(request))

      client.execute {
        request
      }.map { response =>
        val totalVoteAggregations: SumAggregationResult = response.sumAgg("total_votes")
        totalVoteAggregations.value.toInt
      }
    }

    override def indexProposal(record: IndexedProposal, mayBeIndex: Option[IndexAndType] = None): Future[Done] = {
      val index = mayBeIndex.getOrElse(proposalAlias)
      logger.debug(s"$index -> Saving in Elasticsearch: $record")
      client.execute {
        indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.id.value)
      }.map { _ =>
        Done
      }
    }

    override def updateProposal(record: IndexedProposal, mayBeIndex: Option[IndexAndType] = None): Future[Done] = {
      val index = mayBeIndex.getOrElse(proposalAlias)
      logger.debug(s"$index -> Updating in Elasticsearch: $record")
      client
        .execute((update(id = record.id.value) in index).doc(record).refresh(RefreshPolicy.IMMEDIATE))
        .map(_ => Done)
    }
  }

}
