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

package org.make.core.post.indexed
import java.net.URL
import java.time.ZonedDateTime

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations.ApiModelProperty
import org.make.core.CirceFormatters
import org.make.core.post.{Post, PostId}

import scala.annotation.meta.field

object PostElasticsearchFieldNames {
  val postId = "postId"
  val name = "name"
  val slug = "slug"
  val displayHome = "displayHome"
  val postDate = "postDate"
  val thumbnailUrl = "thumbnailUrl"
  val thumbnailAlt = "thumbnailAlt"
  val sourceUrl = "sourceUrl"
  val summary = "summary"
}

final case class IndexedPost(
  @(ApiModelProperty @field)(dataType = "string", example = "6e02345a-8eff-4f2a-a732-83ff831ef10e") postId: PostId,
  name: String,
  slug: String,
  displayHome: Boolean,
  @(ApiModelProperty @field)(dataType = "dateTime") postDate: ZonedDateTime,
  thumbnailUrl: URL,
  thumbnailAlt: Option[String],
  sourceUrl: URL,
  summary: String
)

object IndexedPost extends CirceFormatters {
  implicit val encoder: Encoder[IndexedPost] = deriveEncoder[IndexedPost]
  implicit val decoder: Decoder[IndexedPost] = deriveDecoder[IndexedPost]

  def createFromPost(post: Post): IndexedPost = {
    IndexedPost(
      postId = post.postId,
      name = post.name,
      slug = post.slug,
      displayHome = post.displayHome,
      postDate = post.postDate,
      thumbnailUrl = post.thumbnailUrl,
      thumbnailAlt = post.thumbnailAlt,
      sourceUrl = post.sourceUrl,
      summary = post.summary
    )
  }
}

final case class PostSearchResult(total: Long, results: Seq[IndexedPost])
