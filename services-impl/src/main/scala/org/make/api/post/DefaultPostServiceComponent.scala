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

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import akka.stream.scaladsl.Sink
import cats.data.Validated.{Invalid, Valid}
import grizzled.slf4j.Logging
import eu.timepit.refined.auto._
import org.make.api.ActorSystemComponent
import org.make.api.technical.StreamUtils
import org.make.api.technical.webflow.WebflowItem.WebflowPost
import org.make.api.technical.webflow.{WebflowClientComponent, WebflowConfigurationComponent, WebflowItem}
import org.make.core.post.indexed.{PostSearchQuery, PostSearchResult}
import org.make.core.post.{Post, PostId}
import org.make.core.reference.Country

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait DefaultPostServiceComponent extends PostServiceComponent with Logging {
  this: PostSearchEngineComponent
    with WebflowClientComponent
    with ActorSystemComponent
    with WebflowConfigurationComponent =>

  override lazy val postService: PostService = new DefaultPostService

  class DefaultPostService extends PostService {

    override def search(searchQuery: PostSearchQuery): Future[PostSearchResult] = {
      elasticsearchPostAPI.searchPosts(searchQuery)
    }

    override def fetchPostsForHome(): Future[Seq[Post]] = {
      def toPost(webflowPost: WebflowPost, postDate: ZonedDateTime): ValidatedNel[String, Post] = {
        def parseImageUrl(imageUrl: Option[WebflowItem.WebflowImageRef]): ValidatedNel[String, URL] = {
          imageUrl match {
            case Some(WebflowItem.WebflowImageRef(url, _)) =>
              Try(new URL(url)) match {
                case Success(validUrl) => Validated.valid(validUrl)
                case Failure(_)        => Validated.invalidNel(s"invalid thumbnailImage: $url")
              }
            case None => Validated.invalidNel("empty thumbnailImage")
          }
        }
        def parseSummary(summary: Option[String]): ValidatedNel[String, String] = {
          summary match {
            case Some(desc) if desc.nonEmpty => Valid(desc)
            case _                           => Validated.invalidNel("empty postSummary")
          }
        }
        (parseImageUrl(webflowPost.thumbnailImage), parseSummary(webflowPost.summary))
          .mapN[Post] { (url: URL, summary: String) =>
            Post(
              postId = PostId(webflowPost.id),
              name = webflowPost.name,
              slug = webflowPost.slug,
              displayHome = webflowPost.displayHome.contains(true),
              postDate = postDate,
              thumbnailUrl = url,
              thumbnailAlt = webflowPost.thumbnailImage.flatMap(_.alt),
              sourceUrl = new URL(s"${webflowConfiguration.blogUrl}/post/${webflowPost.slug}"),
              summary = summary,
              country = Country("FR") // Posts from webflow are for France atm
            )
          }
      }

      StreamUtils
        .asyncPageToPageSource(offset => webflowClient.getPosts(100, offset))
        .mapConcat(identity)
        .collect {
          case post @ WebflowPost(id, false, false, _, _, Some(true), Some(postDate), _, _) =>
            (id, toPost(post, postDate))
        }
        .wireTap {
          case (postId, Invalid(errors)) =>
            logger.error(
              s"Ignoring failed post $postId due to invalid required fields: ${errors.toList.mkString(", ")}"
            )
          case _ => ()
        }
        .collect {
          case (_, Valid(post)) => post
        }
        .runWith(Sink.seq)
    }
  }
}
