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

package org.make.api.idea

import akka.Done
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.searches.SearchRequest
import com.sksamuel.elastic4s.searches.queries.BoolQuery
import com.sksamuel.elastic4s.{IndexAndType, RefreshPolicy}
import grizzled.slf4j.Logging
import io.circe.{Json, Printer}
import org.make.api.technical.elasticsearch.{ElasticsearchConfigurationComponent, _}
import org.make.core.CirceFormatters
import org.make.core.idea.indexed.{IdeaSearchResult, IndexedIdea}
import org.make.core.idea.{IdeaId, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait IdeaSearchEngineComponent {
  def elasticsearchIdeaAPI: IdeaSearchEngine
}

trait IdeaSearchEngine {
  def findIdeaById(ideaId: IdeaId): Future[Option[IndexedIdea]]
  def searchIdeas(query: IdeaSearchQuery): Future[IdeaSearchResult]
  def indexIdea(record: IndexedIdea, mayBeIndex: Option[IndexAndType] = None): Future[Done]
  def indexIdeas(records: Seq[IndexedIdea], mayBeIndex: Option[IndexAndType] = None): Future[Done]
  def updateIdea(record: IndexedIdea, mayBeIndex: Option[IndexAndType] = None): Future[Done]
}

object IdeaSearchEngine {
  val ideaIndexName: String = "idea"
}

trait DefaultIdeaSearchEngineComponent extends IdeaSearchEngineComponent with CirceFormatters {
  self: ElasticsearchConfigurationComponent with ElasticsearchClientComponent =>

  override lazy val elasticsearchIdeaAPI: IdeaSearchEngine = new DefaultIdeaSearchEngine

  class DefaultIdeaSearchEngine extends IdeaSearchEngine with Logging {

    private lazy val client = elasticsearchClient.client

    private val ideaAlias: IndexAndType = elasticsearchConfiguration.ideaAliasName / IdeaSearchEngine.ideaIndexName

    // TODO remove once elastic4s-circe upgrades to circe 0.14
    private implicit val printer: Json => String = Printer.noSpaces.print

    override def findIdeaById(ideaId: IdeaId): Future[Option[IndexedIdea]] = {
      client
        .executeAsFuture(get(id = ideaId.value).from(ideaAlias))
        .map(_.toOpt[IndexedIdea])
    }

    override def searchIdeas(ideaSearchQuery: IdeaSearchQuery): Future[IdeaSearchResult] = {
      // parse json string to build search query
      val searchFilters = IdeaSearchFilters.getIdeaSearchFilters(ideaSearchQuery)
      val request: SearchRequest = searchWithType(ideaAlias)
        .bool(BoolQuery(must = searchFilters))
        .sortBy(IdeaSearchFilters.getSort(ideaSearchQuery).toList)
        .size(IdeaSearchFilters.getLimitSearch(ideaSearchQuery))
        .from(IdeaSearchFilters.getSkipSearch(ideaSearchQuery))

      client
        .executeAsFuture(request)
        .map(response => IdeaSearchResult(total = response.totalHits, results = response.to[IndexedIdea]))
    }

    override def indexIdea(record: IndexedIdea, maybeIndex: Option[IndexAndType] = None): Future[Done] = {
      val index = maybeIndex.getOrElse(ideaAlias)
      client
        .executeAsFuture(indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.ideaId.value))
        .map { _ =>
          Done
        }
    }

    override def indexIdeas(records: Seq[IndexedIdea], maybeIndex: Option[IndexAndType] = None): Future[Done] = {
      val index = maybeIndex.getOrElse(ideaAlias)
      client
        .executeAsFuture(bulk(records.map { record =>
          indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.ideaId.value)
        }))
        .map { _ =>
          Done
        }
    }

    override def updateIdea(record: IndexedIdea, maybeIndex: Option[IndexAndType] = None): Future[Done] = {
      val index = maybeIndex.getOrElse(ideaAlias)
      client
        .executeAsFuture((update(id = record.ideaId.value) in index).doc(record).refresh(RefreshPolicy.IMMEDIATE))
        .map(_ => Done)
    }
  }
}
