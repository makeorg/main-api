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
import io.circe.{Decoder, ObjectEncoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.core.operation.OperationId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}

import scala.annotation.meta.field

@ApiModel
case class TagResponse(@(ApiModelProperty @field)(dataType = "string", example = "tag-slug") id: TagId,
                       label: String,
                       @(ApiModelProperty @field)(dataType = "string", example = "INHERIT") display: TagDisplay,
                       @(ApiModelProperty @field)(dataType = "string") tagTypeId: TagTypeId,
                       weight: Float,
                       @(ApiModelProperty @field)(dataType = "string") operationId: Option[OperationId],
                       @(ApiModelProperty @field)(dataType = "string") themeId: Option[ThemeId],
                       country: Country,
                       language: Language)

object TagResponse {
  implicit val encoder: ObjectEncoder[TagResponse] = deriveEncoder[TagResponse]
  implicit val decoder: Decoder[TagResponse] = deriveDecoder[TagResponse]

  def apply(tag: Tag): TagResponse =
    TagResponse(
      id = tag.tagId,
      label = tag.label,
      display = tag.display,
      tagTypeId = tag.tagTypeId,
      weight = tag.weight,
      operationId = tag.operationId,
      themeId = tag.themeId,
      country = tag.country,
      language = tag.language
    )
}
