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

package org.make.core.session

import io.circe.{Decoder, Encoder, Json}
import org.make.core.StringValue
import spray.json.{JsString, JsValue, JsonFormat}

final case class VisitorId(value: String) extends StringValue

object VisitorId {

  implicit lazy val visitorIdEncoder: Encoder[VisitorId] =
    (a: VisitorId) => Json.fromString(a.value)
  implicit lazy val visitorIdDecoder: Decoder[VisitorId] =
    Decoder.decodeString.map(VisitorId(_))

  implicit val visitorIdFormatter: JsonFormat[VisitorId] = new JsonFormat[VisitorId] {
    override def read(json: JsValue): VisitorId = json match {
      case JsString(s) => VisitorId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: VisitorId): JsValue = {
      JsString(obj.value)
    }
  }

}
