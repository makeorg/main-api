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

import org.make.api.docker.SearchEngineIT
import org.make.api.technical.elasticsearch.{
  DefaultElasticsearchClientComponent,
  ElasticsearchConfiguration,
  ElasticsearchConfigurationComponent
}
import org.make.api.ItMakeTest
import org.make.core.{CirceFormatters, Order}
import org.make.core.post.PostId
import org.make.core.post.indexed._
import org.make.core.reference.Country
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class PostSearchEngineIT
    extends ItMakeTest
    with DefaultPostSearchEngineComponent
    with CirceFormatters
    with SearchEngineIT[PostId, IndexedPost]
    with ElasticsearchConfigurationComponent
    with DefaultElasticsearchClientComponent {

  override val StartContainersTimeout: FiniteDuration = 5.minutes

  override val elasticsearchExposedPort: Int = 30006

  override val eSIndexName: String = "post-it-test"
  override val eSDocType: String = "post"
  override def docs: Seq[IndexedPost] = indexedPostsToIndex

  override val elasticsearchConfiguration: ElasticsearchConfiguration =
    mock[ElasticsearchConfiguration]
  when(elasticsearchConfiguration.connectionString).thenReturn(s"localhost:$elasticsearchExposedPort")
  when(elasticsearchConfiguration.postAliasName).thenReturn(eSIndexName)
  when(elasticsearchConfiguration.indexName).thenReturn(eSIndexName)

  override def beforeAll(): Unit = {
    super.beforeAll()
    initializeElasticsearch(_.postId)
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
    indexedPost(PostId("post-5"), postDate = ZonedDateTime.parse("2020-01-01T00:00:00.000Z")),
    indexedPost(
      PostId("post-6"),
      postDate = ZonedDateTime.parse("2020-01-01T00:00:00.000Z"),
      displayHome = false,
      country = Country("DE")
    )
  )

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
        order = Some(Order.desc)
      )
      whenReady(elasticsearchPostAPI.searchPosts(query), Timeout(3.seconds)) { result =>
        result.total should be(3L)
        result.results.map(_.postId) shouldBe Seq(PostId("post-4"), PostId("post-2"), PostId("post-5"))
      }
    }

    Scenario("Search by country") {
      val query = PostSearchQuery(filters = Some(PostSearchFilters(country = Some(PostCountryFilter(Country("DE"))))))

      whenReady(elasticsearchPostAPI.searchPosts(query), Timeout(3.seconds)) { result =>
        result.total should be(1L)
        result.results.headOption.map(_.postId) should contain(PostId("post-6"))
      }
    }
  }

}
