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

package org.make.core.reference

import io.circe.{Decoder, Encoder, Json}
import org.make.core.{MakeSerializable, SprayJsonFormatters, StringValue}
import spray.json.JsonFormat

final case class Label(labelId: LabelId, label: String) extends MakeSerializable

final case class LabelId(value: String) extends StringValue

object LabelId {
  implicit lazy val labelIdEncoder: Encoder[LabelId] =
    (a: LabelId) => Json.fromString(a.value)
  implicit lazy val labelIdDecoder: Decoder[LabelId] =
    Decoder.decodeString.map(LabelId(_))

  implicit val labelIdFormatter: JsonFormat[LabelId] = SprayJsonFormatters.forStringValue(LabelId.apply)

}
