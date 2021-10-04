/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.question

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.swagger.annotations.ApiModelProperty
import org.make.core.tag.TagId

import scala.annotation.meta.field

final case class PopularTagResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "7353ae89-0d05-4014-8aa0-1d7cb0b3aea3") tagId: TagId,
  label: String,
  proposalCount: Long
)

object PopularTagResponse {
  implicit val decoder: Decoder[PopularTagResponse] = deriveDecoder[PopularTagResponse]
  implicit val encoder: Encoder[PopularTagResponse] = deriveEncoder[PopularTagResponse]
}
