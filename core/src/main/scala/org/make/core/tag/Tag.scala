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
import io.swagger.annotations.ApiModelProperty
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.{MakeSerializable, StringValue}
import spray.json.{JsString, JsValue, JsonFormat}

import scala.annotation.meta.field

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

final case class Tag(
  @(ApiModelProperty @field)(dataType = "string", example = "85a11cad-bb00-4418-9fe4-592154918312") tagId: TagId,
  label: String,
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "DISPLAYED,HIDDEN,INHERIT") display: TagDisplay,
  @(ApiModelProperty @field)(dataType = "string", example = "419fa418-b16d-4cd8-a371-38f78e75d25f") tagTypeId: TagTypeId,
  @(ApiModelProperty @field)(dataType = "float", example = "50") weight: Float,
  @(ApiModelProperty @field)(dataType = "string", example = "7509a527-45ef-464f-9c24-ca7e076c77fb") operationId: Option[
    OperationId
  ],
  @(ApiModelProperty @field)(dataType = "string", example = "bb59193e-4d17-44a1-8b0a-6f85e3de7e90") questionId: Option[
    QuestionId
  ],
  @(ApiModelProperty @field)(dataType = "string", example = "d0ba60f6-07c8-4493-9be2-1fffa23d27fb") themeId: Option[
    ThemeId
  ],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr") language: Language
) extends MakeSerializable

object Tag {
  implicit val encoder: ObjectEncoder[Tag] = deriveEncoder[Tag]
  implicit val decoder: Decoder[Tag] = deriveDecoder[Tag]
}
