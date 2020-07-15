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

package org.make.core.tag

import enumeratum.values.{StringEnum, StringEnumEntry}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import org.make.core.StringValue
import org.make.core.technical.enumeratum.FallbackingCirceEnum.FallbackingStringCirceEnum
import spray.json.{JsString, JsValue, JsonFormat}

final case class TagTypeId(value: String) extends StringValue

object TagTypeId {
  implicit lazy val tagIdEncoder: Encoder[TagTypeId] = (a: TagTypeId) => Json.fromString(a.value)
  implicit lazy val tagIdDecoder: Decoder[TagTypeId] = Decoder.decodeString.map(TagTypeId(_))

  implicit val tagIdFormatter: JsonFormat[TagTypeId] = new JsonFormat[TagTypeId] {
    override def read(json: JsValue): TagTypeId = json match {
      case JsString(s) => TagTypeId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: TagTypeId): JsValue = {
      JsString(obj.value)
    }
  }

}

sealed abstract class TagTypeDisplay(val value: String) extends StringEnumEntry

object TagTypeDisplay extends StringEnum[TagTypeDisplay] with FallbackingStringCirceEnum[TagTypeDisplay] {

  override def default(value: String): TagTypeDisplay = Hidden

  case object Displayed extends TagTypeDisplay("DISPLAYED")
  case object Hidden extends TagTypeDisplay("HIDDEN")

  override def values: IndexedSeq[TagTypeDisplay] = findValues

}

final case class TagType(
  tagTypeId: TagTypeId,
  label: String,
  display: TagTypeDisplay,
  weight: Int = 0,
  requiredForEnrichment: Boolean
)

object TagType {
  implicit val encoder: Encoder[TagType] = deriveEncoder[TagType]
  implicit val decoder: Decoder[TagType] = deriveDecoder[TagType]

  val LEGACY: TagType = TagType(
    tagTypeId = TagTypeId("8405aba4-4192-41d2-9a0d-b5aa6cb98d37"),
    label = "Legacy",
    display = TagTypeDisplay.Displayed,
    requiredForEnrichment = false
  )
}
