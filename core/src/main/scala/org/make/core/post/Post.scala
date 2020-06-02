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

package org.make.core.post
import java.net.URL
import java.time.ZonedDateTime

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveDecoder
import org.make.core.{CirceFormatters, StringValue}

case class Post(
  postId: PostId,
  name: String,
  slug: String,
  displayHome: Boolean,
  postDate: ZonedDateTime,
  thumbnailUrl: URL,
  thumbnailAlt: Option[String],
  sourceUrl: URL,
  summary: String
)

object Post extends CirceFormatters {
  implicit val decoder: Decoder[Post] = deriveDecoder[Post]
}

case class PostId(value: String) extends StringValue

object PostId {
  implicit val PostIdEncoder: Encoder[PostId] = Encoder.encodeString.contramap(_.value)
  implicit val PostIdDecoder: Decoder[PostId] = Decoder.decodeString.map(PostId(_))
}
