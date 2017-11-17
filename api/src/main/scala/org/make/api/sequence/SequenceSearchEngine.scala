package org.make.api.sequence

import akka.Done
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.searches.queries.BoolQueryDefinition
import com.sksamuel.elastic4s.update.UpdateDefinition
import com.sksamuel.elastic4s.{ElasticsearchClientUri, IndexAndType}
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.make.api.proposal.DefaultProposalSearchEngineComponent
import org.make.api.technical.businessconfig.BackofficeConfiguration
import org.make.api.technical.elasticsearch.ElasticsearchConfigurationComponent
import org.make.core.CirceFormatters
import org.make.core.sequence._
import org.make.core.sequence.indexed.{IndexedSequence, IndexedStartSequence, SequencesSearchResult}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SequenceSearchEngineComponent {
  def elasticsearchSequenceAPI: SequenceSearchEngine
}

trait SequenceSearchEngine {
  def findSequenceById(sequenceId: SequenceId): Future[Option[IndexedSequence]]
  def findSequenceBySlug(slugSequence: String): Future[Option[IndexedSequence]]
  def searchSequences(query: SearchQuery): Future[SequencesSearchResult]
  def indexSequence(record: IndexedSequence): Future[Done]
  def updateSequence(record: IndexedSequence): Future[Done]
}

trait DefaultSequenceSearchEngineComponent
    extends SequenceSearchEngineComponent
    with CirceFormatters
    with DefaultProposalSearchEngineComponent {
  self: ElasticsearchConfigurationComponent =>

  override lazy val elasticsearchSequenceAPI: SequenceSearchEngine = new SequenceSearchEngine with StrictLogging {

    private val client = HttpClient(
      ElasticsearchClientUri(s"elasticsearch://${elasticsearchConfiguration.connectionString}")
    )
    private val sequenceIndex: IndexAndType = elasticsearchConfiguration.indexName / "sequence"

    override def findSequenceById(sequenceId: SequenceId): Future[Option[IndexedSequence]] = {
      client.execute(get(id = sequenceId.value).from(sequenceIndex)).map(_.toOpt[IndexedSequence])
    }

    override def findSequenceBySlug(slugSequence: String): Future[Option[IndexedSequence]] = {
      val query = SearchStartSequenceRequest(slug = slugSequence).toSearchQuery
      val searchFilters = SearchFilters.getSearchFilters(query)
      val request = search(sequenceIndex)
        .bool(BoolQueryDefinition(must = searchFilters))
        .from(0)
        .size(1)

      logger.debug(client.show(request))

      client.execute {
        request
      }.map(_.to[IndexedSequence]).map {
        case indexedSeq if indexedSeq.isEmpty => None
        case other                            => Some(other.head)
      }
    }

    override def searchSequences(searchQuery: SearchQuery): Future[SequencesSearchResult] = {
      // parse json string to build search query
      val searchFilters = SearchFilters.getSearchFilters(searchQuery)

      val request = search(sequenceIndex)
        .bool(BoolQueryDefinition(must = searchFilters))
        .sortBy(SearchFilters.getSort(searchQuery))
        .from(SearchFilters.getSkipSearch(searchQuery))
        .size(SearchFilters.getLimitSearch(searchQuery))

      logger.debug(client.show(request))

      client.execute {
        request
      }.map { response =>
        SequencesSearchResult(total = response.totalHits, results = response.to[IndexedSequence])
      }
    }

    override def indexSequence(record: IndexedSequence): Future[Done] = {
      logger.info(s"Saving in Elasticsearch: $record")
      client.execute {
        indexInto(sequenceIndex).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.id.value)
      }.map { _ =>
        Done
      }
    }

    override def updateSequence(record: IndexedSequence): Future[Done] = {
      logger.info(s"Updating in Elasticsearch: $record")
      val updateDefinition: UpdateDefinition =
        (update(id = record.id.value) in sequenceIndex).doc(record).refresh(RefreshPolicy.IMMEDIATE)
      logger.debug(client.show(updateDefinition))
      client
        .execute(updateDefinition)
        .map(_ => Done)
    }
  }

}
