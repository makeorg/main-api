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

package org.make.api.tag

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}

import scala.annotation.meta.field

@ApiModel
case class TagResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "cb46cea0-e6a0-430a-a4e4-cc05860eea5d") id: TagId,
  label: String,
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "DISPLAYED,HIDDEN,INHERIT") display: TagDisplay,
  @(ApiModelProperty @field)(dataType = "string", example = "fba4d844-af12-454f-b39b-f360561a46fa") tagTypeId: TagTypeId,
  weight: Float,
  @(ApiModelProperty @field)(dataType = "string", example = "2392f734-b965-4293-ad45-1073bf1f62c5")
  operationId: Option[OperationId],
  @(ApiModelProperty @field)(dataType = "string", example = "1f3757ca-9813-4557-a3b4-295f832b0fd0")
  questionId: Option[QuestionId],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr") language: Language
)

object TagResponse {
  implicit val encoder: Encoder[TagResponse] = deriveEncoder[TagResponse]
  implicit val decoder: Decoder[TagResponse] = deriveDecoder[TagResponse]

  def apply(tag: Tag): TagResponse =
    TagResponse(
      id = tag.tagId,
      label = tag.label,
      display = tag.display,
      tagTypeId = tag.tagTypeId,
      weight = tag.weight,
      operationId = tag.operationId,
      questionId = tag.questionId,
      country = tag.country,
      language = tag.language
    )
}
