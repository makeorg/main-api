package org.make.api.proposal

import akka.Done
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.searches.SearchDefinition
import com.sksamuel.elastic4s.searches.queries.funcscorer.FunctionScoreQueryDefinition
import com.sksamuel.elastic4s.searches.queries.{BoolQueryDefinition, IdQueryDefinition}
import com.sksamuel.elastic4s.{ElasticsearchClientUri, IndexAndType}
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.make.api.technical.elasticsearch.ElasticsearchConfigurationComponent
import org.make.core.{CirceFormatters, DateHelper}
import org.make.core.proposal._
import org.make.core.proposal.indexed.{IndexedProposal, ProposalsSearchResult}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ProposalSearchEngineComponent {
  def elasticsearchProposalAPI: ProposalSearchEngine
}

trait ProposalSearchEngine {
  def findProposalById(proposalId: ProposalId): Future[Option[IndexedProposal]]
  def findProposalsByIds(proposalIds: Seq[ProposalId],
                         size: Option[Int] = None,
                         random: Boolean = true): Future[Seq[IndexedProposal]]
  def searchProposals(query: SearchQuery): Future[ProposalsSearchResult]
  def countProposals(query: SearchQuery): Future[Int]
  def indexProposal(record: IndexedProposal): Future[Done]
  def updateProposal(record: IndexedProposal): Future[Done]
}

trait DefaultProposalSearchEngineComponent extends ProposalSearchEngineComponent with CirceFormatters {
  self: ElasticsearchConfigurationComponent =>

  override lazy val elasticsearchProposalAPI: ProposalSearchEngine = new ProposalSearchEngine with StrictLogging {

    private val client = HttpClient(
      ElasticsearchClientUri(s"elasticsearch://${elasticsearchConfiguration.connectionString}")
    )
    private val proposalIndex: IndexAndType = elasticsearchConfiguration.indexName / "proposal"

    override def findProposalById(proposalId: ProposalId): Future[Option[IndexedProposal]] = {
      client.execute(get(id = proposalId.value).from(proposalIndex)).map(_.toOpt[IndexedProposal])
    }

    override def findProposalsByIds(proposalIds: Seq[ProposalId],
                                    size: Option[Int] = None,
                                    random: Boolean = true): Future[Seq[IndexedProposal]] = {

      val defaultMax: Int = 1000
      val seed: Int = DateHelper.now().toEpochSecond.toInt

      val query: IdQueryDefinition = idsQuery(ids = proposalIds.map(_.value)).types("proposal")
      val randomQuery: FunctionScoreQueryDefinition =
        functionScoreQuery(idsQuery(ids = proposalIds.map(_.value)).types("proposal")).scorers(Seq(randomScore(seed)))

      val request: SearchDefinition = search(proposalIndex)
        .query(if (random) randomQuery else query)
        .size(size.getOrElse(defaultMax))

      logger.debug(client.show(request))

      client.execute {
        request
      }.map {
        _.to[IndexedProposal]
      }
    }

    override def searchProposals(searchQuery: SearchQuery): Future[ProposalsSearchResult] = {
      // parse json string to build search query
      val searchFilters = SearchFilters.getSearchFilters(searchQuery)

      val request = search(proposalIndex)
        .bool(BoolQueryDefinition(must = searchFilters))
        .sortBy(SearchFilters.getSort(searchQuery))
        .from(SearchFilters.getSkipSearch(searchQuery))
        .size(SearchFilters.getLimitSearch(searchQuery))

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

      val request = search(proposalIndex)
        .bool(BoolQueryDefinition(must = searchFilters))

      logger.debug(client.show(request))

      client.execute {
        request
      }.map { response =>
        response.totalHits
      }

    }
    override def indexProposal(record: IndexedProposal): Future[Done] = {
      logger.info(s"$proposalIndex -> Saving in Elasticsearch: $record")
      client.execute {
        indexInto(proposalIndex).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.id.value)
      }.map { _ =>
        Done
      }
    }

    override def updateProposal(record: IndexedProposal): Future[Done] = {
      logger.info(s"$proposalIndex -> Updating in Elasticsearch: $record")
      client
        .execute((update(id = record.id.value) in proposalIndex).doc(record).refresh(RefreshPolicy.IMMEDIATE))
        .map(_ => Done)
    }
  }

}
