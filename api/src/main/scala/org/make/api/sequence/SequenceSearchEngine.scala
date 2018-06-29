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

package org.make.api.sequence

import akka.Done
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.searches.queries.BoolQueryDefinition
import com.sksamuel.elastic4s.{ElasticsearchClientUri, IndexAndType}
import com.typesafe.scalalogging.StrictLogging
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.make.api.proposal.DefaultProposalSearchEngineComponent
import org.make.api.technical.elasticsearch.ElasticsearchConfigurationComponent
import org.make.core.CirceFormatters
import org.make.core.sequence._
import org.make.core.sequence.indexed.{IndexedSequence, SequencesSearchResult}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SequenceSearchEngineComponent {
  def elasticsearchSequenceAPI: SequenceSearchEngine
}

trait SequenceSearchEngine {
  def findSequenceById(sequenceId: SequenceId): Future[Option[IndexedSequence]]
  def findSequenceBySlug(slugSequence: String): Future[Option[IndexedSequence]]
  def searchSequences(query: SearchQuery): Future[SequencesSearchResult]
  def indexSequence(record: IndexedSequence, mayBeIndex: Option[IndexAndType] = None): Future[Done]
  def updateSequence(record: IndexedSequence, mayBeIndex: Option[IndexAndType] = None): Future[Done]
}

object SequenceSearchEngine {
  val sequenceIndexName = "sequence"
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

    private val sequenceAlias
      : IndexAndType = elasticsearchConfiguration.aliasName / SequenceSearchEngine.sequenceIndexName

    override def findSequenceById(sequenceId: SequenceId): Future[Option[IndexedSequence]] = {
      client.execute(get(id = sequenceId.value).from(sequenceAlias)).map(_.toOpt[IndexedSequence])
    }

    override def findSequenceBySlug(slugSequence: String): Future[Option[IndexedSequence]] = {
      val query = SearchStartSequenceRequest(slug = slugSequence).toSearchQuery
      val searchFilters = SearchFilters.getSearchFilters(query)
      val request = search(sequenceAlias)
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
      val request = search(sequenceAlias)
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

    override def indexSequence(record: IndexedSequence, mayBeIndex: Option[IndexAndType] = None): Future[Done] = {
      logger.debug(s"Saving in Elasticsearch: $record")
      val index = mayBeIndex.getOrElse(sequenceAlias)
      client.execute {
        indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.id.value)
      }.map { _ =>
        Done
      }
    }

    override def updateSequence(record: IndexedSequence, mayBeIndex: Option[IndexAndType] = None): Future[Done] = {
      val index = mayBeIndex.getOrElse(sequenceAlias)
      logger.debug(s"$index -> Updating in Elasticsearch: $record")
      client
        .execute((update(id = record.id.value) in index).doc(record).refresh(RefreshPolicy.IMMEDIATE))
        .map(_ => Done)
    }
  }

}
