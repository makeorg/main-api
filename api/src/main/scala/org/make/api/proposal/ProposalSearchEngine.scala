package org.make.api.proposal

import akka.Done
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.searches.queries.BoolQueryDefinition
import com.sksamuel.elastic4s.{ElasticsearchClientUri, IndexAndType}
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.make.api.technical.elasticsearch.ElasticsearchConfigurationComponent
import org.make.core.CirceFormatters
import org.make.core.proposal._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ProposalSearchEngineComponent {
  def elasticsearchAPI: ProposalSearchEngine
}

trait ProposalSearchEngine {
  def findProposalById(proposalId: ProposalId): Future[Option[ProposalElasticsearch]]
  def searchProposals(query: SearchQuery): Future[Seq[ProposalElasticsearch]]
  def indexProposal(record: ProposalElasticsearch): Future[Done]
  def updateProposal(record: ProposalElasticsearch): Future[Done]
}

trait DefaultProposalSearchEngineComponent extends ProposalSearchEngineComponent with CirceFormatters {
  self: ElasticsearchConfigurationComponent =>

  override lazy val elasticsearchAPI = new ProposalSearchEngine with StrictLogging {

    private val client = HttpClient(
      ElasticsearchClientUri(elasticsearchConfiguration.host, elasticsearchConfiguration.port)
    )
    private val proposalIndex: IndexAndType = "proposals" / "proposal"

    override def findProposalById(proposalId: ProposalId): Future[Option[ProposalElasticsearch]] = {
      client.execute(get(id = proposalId.value).from(proposalIndex)).map(_.toOpt[ProposalElasticsearch])
    }

    override def searchProposals(searchQuery: SearchQuery): Future[Seq[ProposalElasticsearch]] = {
      client.execute {
        // parse json string to build search query
        val searchFilters = SearchFilter.getSearchFilters(searchQuery)
        // build search query
        search(proposalIndex)
          .bool(BoolQueryDefinition(must = searchFilters))
          .sortBy(SearchFilter.getSortOption(searchQuery))
          .from(SearchFilter.getSkipSearchOption(searchQuery))
          .size(SearchFilter.getLimitSearchOption(searchQuery))

      }.map { response =>
        response.to[ProposalElasticsearch]
      }
    }

    override def indexProposal(record: ProposalElasticsearch): Future[Done] = {
      logger.info(s"Saving in Elasticsearch: $record")
      client.execute {
        indexInto(proposalIndex).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.id.value)
      }.map { _ =>
        Done
      }
    }

    override def updateProposal(record: ProposalElasticsearch): Future[Done] = {
      logger.info(s"Updating in Elasticsearch: $record")
      client
        .execute((update(id = record.id.toString) in proposalIndex).doc(record).refresh(RefreshPolicy.IMMEDIATE))
        .map(_ => Done)
    }
  }

}
