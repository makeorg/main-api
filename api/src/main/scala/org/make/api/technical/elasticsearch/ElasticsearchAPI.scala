package org.make.api.technical.elasticsearch

import java.time.ZonedDateTime
import java.util.UUID

import akka.Done
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.searches.queries.BoolQueryDefinition
import com.sksamuel.elastic4s.{ElasticsearchClientUri, IndexAndType}
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.circe.{Decoder, Encoder, Json}
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.make.core.proposal.ProposalId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CustomFormatters {
  implicit val zonedDateTimeDecoder: Decoder[ZonedDateTime] =
    Decoder.decodeString.map(ZonedDateTime.parse)
  implicit val zonedDateTimeEncoder: Encoder[ZonedDateTime] =
    (a: ZonedDateTime) => Json.fromString(a.toString)

  implicit val uuidDecoder: Decoder[UUID] =
    Decoder.decodeString.map(UUID.fromString)
  implicit val uuidEncoder: Encoder[UUID] = (a: UUID) => Json.fromString(a.toString)
}

trait ElasticsearchAPIComponent {
  def elasticsearchAPI: ElasticsearchAPI
}

trait ElasticsearchAPI {
  def getProposalById(proposalId: ProposalId): Future[Option[ProposalElasticsearch]]
  def searchProposals(queryJsonString: String): Future[Seq[Option[ProposalElasticsearch]]]
  def save(record: ProposalElasticsearch): Future[Done]
  def updateProposal(record: ProposalElasticsearch): Future[Done]
}

trait DefaultElasticsearchAPIComponent extends ElasticsearchAPIComponent {
  self: ElasticsearchConfigurationComponent =>

  override lazy val elasticsearchAPI = new ElasticsearchAPI with SearchQueryBuilderComponent with CustomFormatters
  with StrictLogging {

    private val client = HttpClient(
      ElasticsearchClientUri(elasticsearchConfiguration.host, elasticsearchConfiguration.port)
    )
    private val proposalIndex: IndexAndType = "proposals" / "proposal"
    val searchQueryBuilder = new SearchQueryBuilder()

    override def getProposalById(proposalId: ProposalId): Future[Option[ProposalElasticsearch]] = {
      client.execute {
        get(id = proposalId.value).from(proposalIndex)
      }.flatMap { response =>
        logger.debug("Received response from Elasticsearch: " + response.toString)
        Future.successful(
          ProposalElasticsearch.shape
            .applyOrElse[AnyRef, Option[ProposalElasticsearch]](response, _ => None)
        )
      }
    }

    override def searchProposals(queryJsonString: String): Future[Seq[Option[ProposalElasticsearch]]] = {
      client.execute {
        // parse json string to build search query
        val searchQuery = searchQueryBuilder.buildSearchQueryFromJson(queryJsonString)
        val searchFilters = searchQueryBuilder.getSearchFilters(searchQuery)
        // build search query
        search(proposalIndex)
          .bool(BoolQueryDefinition(must = searchFilters))
          .sortBy(searchQueryBuilder.getSortOption(searchQuery))
          .from(searchQueryBuilder.getSkipSearchOption(searchQuery))
          .size(searchQueryBuilder.getLimitSearchOption(searchQuery))

      }.flatMap { response =>
        logger.debug("Received response from Elasticsearch: " + response.toString)
        Future.successful(
          response.hits.hits.map(
            searchHit =>
              ProposalElasticsearch.shape.applyOrElse[AnyRef, Option[ProposalElasticsearch]](searchHit, _ => None)
          )
        )
      }
    }

    override def save(record: ProposalElasticsearch): Future[Done] = {
      logger.info(s"Saving in Elasticsearch: $record")
      client.execute {
        indexInto(proposalIndex).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.id.toString)
      }.map { _ =>
        Done
      }
    }

    override def updateProposal(record: ProposalElasticsearch): Future[Done] = {
      logger.info(s"Updating in Elasticsearch: $record")
      client.execute {
        (update(id = record.id.toString) in proposalIndex).doc(record).refresh(RefreshPolicy.IMMEDIATE)
      }.map { _ =>
        Done
      }
    }
  }

}
