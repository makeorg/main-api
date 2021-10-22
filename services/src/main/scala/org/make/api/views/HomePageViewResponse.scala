/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.views

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.swagger.annotations.ApiModelProperty
import org.make.api.question.QuestionOfOperationResponse
import org.make.api.views.Highlights
import org.make.api.views.HomePageViewResponse.PostResponse
import org.make.core.CirceFormatters
import org.make.core.post.indexed.IndexedPost

import java.net.URL
import scala.annotation.meta.field

final case class HomePageViewResponse(
  highlights: Highlights,
  currentQuestions: Seq[QuestionOfOperationResponse],
  pastQuestions: Seq[QuestionOfOperationResponse],
  featuredQuestions: Seq[QuestionOfOperationResponse],
  posts: Seq[PostResponse]
)

object HomePageViewResponse extends CirceFormatters {
  final case class PostResponse(
    title: String,
    description: String,
    @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/picture.png") picture: URL,
    @(ApiModelProperty @field)(dataType = "string", example = "picture alternative") alt: Option[String],
    @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/link") link: URL
  )
  object PostResponse {
    def fromIndexedPost(post: IndexedPost): PostResponse =
      PostResponse(
        title = post.name,
        description = post.summary,
        picture = post.thumbnailUrl,
        alt = post.thumbnailAlt,
        link = post.sourceUrl
      )

  }

  implicit val postEncoder: Encoder[PostResponse] = deriveEncoder
  implicit val highlightsEncoder: Encoder[Highlights] = deriveEncoder
  implicit val encoder: Encoder[HomePageViewResponse] = deriveEncoder
}
