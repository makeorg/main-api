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

import java.net.URL
import java.time.ZonedDateTime

import akka.http.scaladsl.model.StatusCodes
import org.make.api.technical.webflow.WebflowClient._
import org.make.api.technical.webflow.WebflowItem.{WebflowImageRef, WebflowPost}
import org.make.api.technical.webflow._
import org.make.api.{EmptyActorSystemComponent, MakeUnitTest}
import org.make.core.post.PostId
import org.make.core.post.indexed.PostSearchQuery
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PostServiceTest
    extends MakeUnitTest
    with DefaultPostServiceComponent
    with PostSearchEngineComponent
    with WebflowClientComponent
    with EmptyActorSystemComponent
    with WebflowConfigurationComponent {

  override val elasticsearchPostAPI: PostSearchEngine = mock[PostSearchEngine]
  override val webflowConfiguration: WebflowConfiguration = mock[WebflowConfiguration]

  var webflowResult: Future[Seq[WebflowPost]] = Future.successful(Seq.empty[WebflowPost])
  override val webflowClient: WebflowClient = (_: UpToOneHundred, offset: Int) =>
    if (offset > 0) Future.successful(Seq.empty[WebflowPost]) else webflowResult

  when(webflowConfiguration.blogUrl).thenReturn(new URL("https://example.com/webflow-url"))

  val defaultImageRef: WebflowImageRef = WebflowImageRef(url = "https://example.com/image", alt = Some("image alt"))
  def webflowPost(
    id: String,
    archived: Boolean = false,
    draft: Boolean = false,
    name: String = "Post name",
    slug: String = "post-slug",
    displayHome: Option[Boolean] = Some(true),
    postDate: Option[ZonedDateTime] = Some(ZonedDateTime.parse("2020-06-10T10:10:10.000Z")),
    thumbnailImage: Option[WebflowImageRef] = Some(defaultImageRef),
    summary: Option[String] = Some("This is a summary of an awesome post.")
  ): WebflowPost =
    WebflowPost(
      id = id,
      archived = archived,
      draft = draft,
      name = name,
      slug = slug,
      displayHome = displayHome,
      postDate = postDate,
      thumbnailImage = thumbnailImage,
      summary = summary
    )

  Feature("search") {
    Scenario("search posts") {
      val query = PostSearchQuery()
      postService.search(query)
      verify(elasticsearchPostAPI).searchPosts(query)
    }
  }

  Feature("fetch posts for home") {
    Scenario("webflow returns only valid results") {
      webflowResult = Future.successful(Seq(webflowPost("post-id-1"), webflowPost("post-id-2")))

      whenReady(postService.fetchPostsForHome(), Timeout(3.seconds)) { posts =>
        posts.size shouldBe 2
      }
    }

    Scenario("webflow returns unpublished posts") {
      webflowResult = Future.successful(
        Seq(webflowPost("post-id-unpublished-1", archived = true), webflowPost("post-id-unpublished-2", draft = true))
      )

      whenReady(postService.fetchPostsForHome(), Timeout(3.seconds)) { posts =>
        posts.size shouldBe 0
      }
    }

    Scenario("webflow returns displayHome false results") {
      webflowResult = Future.successful(Seq(webflowPost("post-id-hide-1", displayHome = Some(false))))

      whenReady(postService.fetchPostsForHome(), Timeout(3.seconds)) { posts =>
        posts shouldBe empty
      }
    }

    Scenario("webflow returns no results") {
      webflowResult = Future.successful(Seq.empty[WebflowPost])

      whenReady(postService.fetchPostsForHome(), Timeout(3.seconds)) { posts =>
        posts shouldBe empty
      }
    }

    Scenario("webflow returns results with invalid thumbnail url") {
      webflowResult = Future.successful(
        Seq(
          webflowPost("post-id-invalid-thumbnail", thumbnailImage = Some(defaultImageRef.copy(url = "invalid url"))),
          webflowPost("post-id-empty-thumbnail", thumbnailImage = None),
          webflowPost("post-id-valid")
        )
      )

      whenReady(postService.fetchPostsForHome(), Timeout(3.seconds)) { posts =>
        posts.size shouldBe 1
        posts.head.postId shouldBe PostId("post-id-valid")
      }
    }

    Scenario("webflow fails for whatever reason") {
      webflowResult = Future.failed(
        WebflowClientException
          .RequestException("getItemsFromCollection [posts]", StatusCodes.BadRequest, "Some error message")
      )

      whenReady(postService.fetchPostsForHome().failed, Timeout(3.seconds)) { exception =>
        exception shouldBe a[WebflowClientException.RequestException]
        exception.getMessage shouldBe "getItemsFromCollection [posts] failed with status 400 Bad Request: Some error message"
      }
    }
  }
}
