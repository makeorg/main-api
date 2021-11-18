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
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json}
import io.swagger.annotations.ApiModelProperty
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.technical.enumeratum.FallbackingCirceEnum.FallbackingStringCirceEnum
import org.make.core.{MakeSerializable, SprayJsonFormatters, StringValue}
import spray.json.JsonFormat
import com.github.plokhotnyuk.jsoniter_scala.core._

import scala.annotation.meta.field

final case class TagId(value: String) extends StringValue

object TagId {
  implicit lazy val tagIdEncoder: Encoder[TagId] =
    (a: TagId) => Json.fromString(a.value)
  implicit lazy val tagIdDecoder: Decoder[TagId] =
    Decoder.decodeString.map(TagId(_))

  implicit val tagIdFormatter: JsonFormat[TagId] = SprayJsonFormatters.forStringValue(TagId.apply)

  implicit val tagIdCodec: JsonValueCodec[TagId] =
    StringValue.makeCodec(TagId.apply)
}

sealed abstract class TagDisplay(val value: String) extends StringEnumEntry with Product with Serializable

object TagDisplay extends StringEnum[TagDisplay] with FallbackingStringCirceEnum[TagDisplay] {

  override def default(value: String): TagDisplay = Inherit

  case object Displayed extends TagDisplay("DISPLAYED")
  case object Hidden extends TagDisplay("HIDDEN")
  case object Inherit extends TagDisplay("INHERIT")

  override def values: IndexedSeq[TagDisplay] = findValues

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
  ]
) extends MakeSerializable

object Tag {
  implicit val encoder: Encoder[Tag] = deriveEncoder[Tag]
  implicit val decoder: Decoder[Tag] = deriveDecoder[Tag]
}
