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

package org.make.core.widget

import io.circe.{Codec, Decoder, Encoder}
import org.make.core.StringValue
import org.make.core.user.UserId

import java.time.ZonedDateTime

final case class SourceId(value: String) extends StringValue

object SourceId {
  implicit val codec: Codec[SourceId] =
    Codec.from(Decoder[String].map(SourceId.apply), Encoder[String].contramap(_.value))

}

final case class Source(
  id: SourceId,
  name: String,
  source: String,
  createdAt: ZonedDateTime,
  updatedAt: ZonedDateTime,
  userId: UserId
)
