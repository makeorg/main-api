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

package org.make.api.post

import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.searches.SearchRequest
import com.sksamuel.elastic4s.searches.queries.BoolQuery
import com.sksamuel.elastic4s.{IndexAndType, RefreshPolicy}
import grizzled.slf4j.Logging
import io.circe.{Json, Printer}
import org.make.api.technical.elasticsearch.{ElasticsearchClientComponent, ElasticsearchConfigurationComponent, _}
import org.make.core.CirceFormatters
import org.make.core.elasticsearch.IndexationStatus
import org.make.core.post.PostId
import org.make.core.post.indexed._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PostSearchEngineComponent {
  def elasticsearchPostAPI: PostSearchEngine
}

trait PostSearchEngine {
  def findPostById(postId: PostId): Future[Option[IndexedPost]]
  def searchPosts(query: PostSearchQuery): Future[PostSearchResult]
  def indexPosts(records: Seq[IndexedPost], maybeIndex: Option[IndexAndType]): Future[IndexationStatus]
}

object PostSearchEngine {
  val postIndexName: String = "post"
}

trait DefaultPostSearchEngineComponent extends PostSearchEngineComponent with CirceFormatters with Logging {
  self: ElasticsearchConfigurationComponent with ElasticsearchClientComponent =>

  override lazy val elasticsearchPostAPI: PostSearchEngine =
    new DefaultPostSearchEngine

  class DefaultPostSearchEngine extends PostSearchEngine {

    private lazy val client = elasticsearchClient.client

    private val postAlias: IndexAndType =
      elasticsearchConfiguration.postAliasName / PostSearchEngine.postIndexName

    // TODO remove once elastic4s-circe upgrades to circe 0.14
    private implicit val printer: Json => String = Printer.noSpaces.print

    override def findPostById(postId: PostId): Future[Option[IndexedPost]] = {
      client
        .executeAsFuture(get(id = postId.value).from(postAlias))
        .map(_.toOpt[IndexedPost])
    }

    override def searchPosts(query: PostSearchQuery): Future[PostSearchResult] = {
      val searchFilters = PostSearchFilters.getPostSearchFilters(query)
      val request: SearchRequest = searchWithType(postAlias)
        .bool(BoolQuery(must = searchFilters))
        .sortBy(PostSearchFilters.getSort(query).toList)
        .size(PostSearchFilters.getLimitSearch(query))
        .from(PostSearchFilters.getSkipSearch(query))

      client
        .executeAsFuture(request)
        .map { response =>
          PostSearchResult(total = response.totalHits, results = response.to[IndexedPost])
        }
    }

    override def indexPosts(records: Seq[IndexedPost], maybeIndex: Option[IndexAndType]): Future[IndexationStatus] = {
      val index = maybeIndex.getOrElse(postAlias)
      client
        .executeAsFuture(bulk(records.map { record =>
          indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.postId.value)
        }))
        .map(_ => IndexationStatus.Completed)
        .recover {
          case e: Exception =>
            logger.error(s"Indexing ${records.size} posts failed", e)
            IndexationStatus.Failed(e)
        }
    }
  }
}
