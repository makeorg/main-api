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

package org.make.api.tagtype

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.core.tag.{TagType, TagTypeDisplay, TagTypeId}

import scala.annotation.meta.field

@ApiModel
final case class TagTypeResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "12345678-90ab-cdef-1234-567890abcdef") id: TagTypeId,
  label: String,
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "DISPLAYED,HIDDEN") display: TagTypeDisplay,
  weight: Int
)

object TagTypeResponse {
  implicit val encoder: ObjectEncoder[TagTypeResponse] = deriveEncoder[TagTypeResponse]
  implicit val decoder: Decoder[TagTypeResponse] = deriveDecoder[TagTypeResponse]

  def apply(tagType: TagType): TagTypeResponse =
    TagTypeResponse(id = tagType.tagTypeId, label = tagType.label, display = tagType.display, weight = tagType.weight)
}
