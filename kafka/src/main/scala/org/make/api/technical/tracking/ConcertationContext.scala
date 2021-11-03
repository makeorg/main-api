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

package org.make.api.technical.tracking

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations.ApiModelProperty
import org.make.core.reference.{Country, Language}
import org.make.core.session.SessionId

import scala.annotation.meta.field

final case class ConcertationContext(
  @(ApiModelProperty @field)(dataType = "string", example = "4ee15013-b5a6-415c-8270-da8438766644")
  sessionId: SessionId,
  concertationSlug: Option[String],
  projectSlug: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "4ee15013-b5a6-415c-8270-da8438766644")
  sectionId: Option[SectionId],
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Country,
  location: String,
  @(ApiModelProperty @field)(dataType = "map[string]")
  getParameters: Map[String, String]
)

object ConcertationContext {
  implicit val decoder: Decoder[ConcertationContext] = deriveDecoder[ConcertationContext]
}
