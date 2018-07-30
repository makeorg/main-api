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

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import org.make.core.tag.Tag
import org.make.core.{MakeSerializable, StringValue}
import spray.json.{JsString, JsValue, JsonFormat}

final case class GradientColor(from: String, to: String) extends MakeSerializable

object GradientColor {
  implicit val encoder: ObjectEncoder[GradientColor] = deriveEncoder[GradientColor]
  implicit val decoder: Decoder[GradientColor] = deriveDecoder[GradientColor]
}

final case class ThemeTranslation(slug: String, title: String, language: Language) extends MakeSerializable

object ThemeTranslation {
  implicit val encoder: ObjectEncoder[ThemeTranslation] = deriveEncoder[ThemeTranslation]
  implicit val decoder: Decoder[ThemeTranslation] = deriveDecoder[ThemeTranslation]
}

final case class Theme(themeId: ThemeId,
                       translations: Seq[ThemeTranslation],
                       actionsCount: Int,
                       proposalsCount: Long,
                       votesCount: Int,
                       country: Country,
                       color: String,
                       gradient: Option[GradientColor] = None,
                       tags: Seq[Tag] = Seq.empty)
    extends MakeSerializable

object Theme {
  implicit val encoder: ObjectEncoder[Theme] = deriveEncoder[Theme]
  implicit val decoder: Decoder[Theme] = deriveDecoder[Theme]
}

final case class ThemeId(value: String) extends StringValue

object ThemeId {
  implicit lazy val themeIdEncoder: Encoder[ThemeId] =
    (a: ThemeId) => Json.fromString(a.value)
  implicit lazy val themeIdDecoder: Decoder[ThemeId] =
    Decoder.decodeString.map(ThemeId(_))

  implicit val themeIdFormatter: JsonFormat[ThemeId] = new JsonFormat[ThemeId] {
    override def read(json: JsValue): ThemeId = json match {
      case JsString(s) => ThemeId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: ThemeId): JsValue = {
      JsString(obj.value)
    }
  }

}
