/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.scaladsl.{Flow, Source => AkkaSource}
import io.circe.syntax._
import org.make.api.docker.DockerElasticsearchService
import org.make.api.technical.elasticsearch.{
  DefaultElasticsearchClientComponent,
  ElasticsearchConfiguration,
  ElasticsearchConfigurationComponent
}
import org.make.api.{ActorSystemComponent, ItMakeTest}
import org.make.core.CirceFormatters
import org.make.core.post.PostId
import org.make.core.post.indexed._
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}
import scala.io.{Codec, Source}
import scala.util.{Failure, Success, Try}

class PostSearchEngineIT
    extends ItMakeTest
    with DefaultPostSearchEngineComponent
    with CirceFormatters
    with DockerElasticsearchService
    with ElasticsearchConfigurationComponent
    with DefaultElasticsearchClientComponent
    with ActorSystemComponent {

  override val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)

  override val StartContainersTimeout: FiniteDuration = 5.minutes

  override val elasticsearchExposedPort: Int = 30006

  private val eSIndexName: String = "post-it-test"
  private val eSDocType: String = "post"

  override val elasticsearchConfiguration: ElasticsearchConfiguration =
    mock[ElasticsearchConfiguration]
  when(elasticsearchConfiguration.connectionString).thenReturn(s"localhost:$elasticsearchExposedPort")
  when(elasticsearchConfiguration.postAliasName).thenReturn(eSIndexName)
  when(elasticsearchConfiguration.indexName).thenReturn(eSIndexName)

  override def beforeAll(): Unit = {
    super.beforeAll()
    initializeElasticsearch()
  }

  val indexedPostsToIndex: Seq[IndexedPost] = Seq(
    indexedPost(PostId("post-1"), displayHome = false),
    indexedPost(
      PostId("post-2"),
      name = "Just fancy that !",
      postDate = ZonedDateTime.parse("2020-06-15T00:00:00.000Z")
    ),
    indexedPost(PostId("post-3"), slug = "waddaya-mean", displayHome = false),
    indexedPost(PostId("post-4"), postDate = ZonedDateTime.parse("2020-12-31T00:00:00.000Z")),
    indexedPost(PostId("post-5"), postDate = ZonedDateTime.parse("2020-01-01T00:00:00.000Z"))
  )

  private def initializeElasticsearch(): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem()
    val elasticsearchEndpoint = s"http://localhost:$elasticsearchExposedPort"
    val postMapping =
      Source.fromResource("elasticsearch-mappings/post.json")(Codec.UTF8).getLines().mkString("")
    val responseFuture: Future[HttpResponse] =
      Http().singleRequest(
        HttpRequest(
          uri = s"$elasticsearchEndpoint/$eSIndexName",
          method = HttpMethods.PUT,
          entity = HttpEntity(ContentTypes.`application/json`, postMapping)
        )
      )
    Await.result(responseFuture, 20.seconds)
    responseFuture.onComplete {
      case Failure(e) =>
        logger.error(s"Cannot create elasticsearch schema: ${e.getStackTrace.mkString("\n")}")
        fail(e)
      case Success(_) => logger.debug("Elasticsearch mapped successfully.")
    }

    val pool: Flow[(HttpRequest, PostId), (Try[HttpResponse], PostId), Http.HostConnectionPool] =
      Http().cachedHostConnectionPool[PostId](
        "localhost",
        elasticsearchExposedPort,
        ConnectionPoolSettings(actorSystem).withMaxConnections(3)
      )

    val insertFutures = AkkaSource[IndexedPost](indexedPostsToIndex).map { postToIndex =>
      val indexAndDocTypeEndpoint = s"$eSIndexName/$eSDocType"
      (
        HttpRequest(
          uri = s"$elasticsearchEndpoint/$indexAndDocTypeEndpoint/${postToIndex.postId.value}",
          method = HttpMethods.PUT,
          entity = HttpEntity(ContentTypes.`application/json`, postToIndex.asJson.toString)
        ),
        postToIndex.postId
      )
    }.via(pool).runForeach {
      case (Failure(e), id) => logger.error(s"Error when indexing post ${id.value}:", e)
      case _                =>
    }
    Await.result(insertFutures, 150.seconds)
    logger.debug("posts indexed successfully.")

    val responseRefreshIdeaFuture: Future[HttpResponse] = Http().singleRequest(
      HttpRequest(uri = s"$elasticsearchEndpoint/$eSIndexName/_refresh", method = HttpMethods.POST)
    )
    Await.result(responseRefreshIdeaFuture, 5.seconds)
  }

  Feature("get post by id") {
    Scenario("find by postId") {
      whenReady(elasticsearchPostAPI.findPostById(PostId("post-2")), Timeout(3.seconds)) {
        case Some(post) => post.postId should equal(PostId("post-2"))
        case None       => fail("post not found by id")
      }
    }
  }

  Feature("search posts") {
    Scenario("search multiple post ids") {
      val query = PostSearchQuery(filters =
        Some(PostSearchFilters(postIds = Some(PostIdsSearchFilter(Seq(PostId("post-4"), PostId("post-2"))))))
      )
      whenReady(elasticsearchPostAPI.searchPosts(query), Timeout(3.seconds)) { result =>
        result.total should be(2L)
        result.results.exists(_.postId == PostId("post-4")) shouldBe true
        result.results.exists(_.postId == PostId("post-2")) shouldBe true
      }
    }

    Scenario("search by displayHome ordered by postDate DESC") {
      val query = PostSearchQuery(
        filters = Some(PostSearchFilters(displayHome = Some(DisplayHomeSearchFilter(true)))),
        sort = Some(PostElasticsearchFieldNames.postDate),
        order = Some("DESC")
      )
      whenReady(elasticsearchPostAPI.searchPosts(query), Timeout(3.seconds)) { result =>
        result.total should be(3L)
        result.results.map(_.postId) shouldBe Seq(PostId("post-4"), PostId("post-2"), PostId("post-5"))
      }
    }
  }

}
