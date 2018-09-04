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

import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.{MakeSerializable, StringValue}
import spray.json.{JsString, JsValue, JsonFormat}

final case class TagId(value: String) extends StringValue

object TagId {
  implicit lazy val tagIdEncoder: Encoder[TagId] =
    (a: TagId) => Json.fromString(a.value)
  implicit lazy val tagIdDecoder: Decoder[TagId] =
    Decoder.decodeString.map(TagId(_))

  implicit val tagIdFormatter: JsonFormat[TagId] = new JsonFormat[TagId] {
    override def read(json: JsValue): TagId = json match {
      case JsString(s) => TagId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: TagId): JsValue = {
      JsString(obj.value)
    }
  }

}

sealed trait TagDisplay { val shortName: String }

object TagDisplay extends StrictLogging {
  val tagDisplayDefault: TagDisplay = Inherit
  val tagDisplays: Map[String, TagDisplay] =
    Map(Displayed.shortName -> Displayed, Hidden.shortName -> Hidden, Inherit.shortName -> Inherit)

  implicit lazy val tagDisplayEncoder: Encoder[TagDisplay] =
    (tagDisplay: TagDisplay) => Json.fromString(tagDisplay.shortName)
  implicit lazy val tagDisplayDecoder: Decoder[TagDisplay] =
    Decoder.decodeString.emap(
      tagDisplay =>
        Right(
          TagDisplay
            .matchTagDisplayOrDefault(tagDisplay)
      )
    )

  implicit val tagDisplayFormatter: JsonFormat[TagDisplay] = new JsonFormat[TagDisplay] {
    override def read(json: JsValue): TagDisplay = json match {
      case JsString(s) =>
        TagDisplay.tagDisplays.getOrElse(s, throw new IllegalArgumentException(s"Unable to convert $s"))
      case other => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: TagDisplay): JsValue = {
      JsString(obj.shortName)
    }
  }

  def matchTagDisplayOrDefault(tagDisplay: String): TagDisplay = {
    tagDisplays.getOrElse(tagDisplay, {
      logger.warn(s"$tagDisplay is not a tagDisplay")
      tagDisplayDefault
    })
  }

  case object Displayed extends TagDisplay { override val shortName: String = "DISPLAYED" }
  case object Hidden extends TagDisplay { override val shortName: String = "HIDDEN" }
  case object Inherit extends TagDisplay { override val shortName: String = "INHERIT" }
}

final case class Tag(tagId: TagId,
                     label: String,
                     display: TagDisplay,
                     tagTypeId: TagTypeId,
                     weight: Float,
                     operationId: Option[OperationId],
                     questionId: Option[QuestionId],
                     themeId: Option[ThemeId],
                     country: Country,
                     language: Language)
    extends MakeSerializable

object Tag {
  implicit val encoder: ObjectEncoder[Tag] = deriveEncoder[Tag]
  implicit val decoder: Decoder[Tag] = deriveDecoder[Tag]
}
