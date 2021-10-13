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

package org.make.core

import io.circe.{Decoder, Encoder, Json}
import spray.json.JsonFormat

final case class EventId(value: String) extends StringValue

object EventId {
  implicit val eventIdFormatter: JsonFormat[EventId] = SprayJsonFormatters.forStringValue(EventId.apply)

  implicit lazy val eventIdEncoder: Encoder[EventId] =
    (a: EventId) => Json.fromString(a.value)
  implicit lazy val eventIdDecoder: Decoder[EventId] =
    Decoder.decodeString.map(EventId(_))
}
