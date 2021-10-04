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

package org.make.api.operation

import enumeratum.{Circe, Enum, EnumEntry}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.swagger.annotations.ApiModelProperty
import org.make.core.operation.ResultsLink

import scala.annotation.meta.field

final case class ResultsLinkResponse(
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "External,Internal")
  kind: ResultsLinkKind,
  value: String
)
object ResultsLinkResponse {
  def apply(resultsLink: ResultsLink): ResultsLinkResponse = resultsLink match {
    case ResultsLink.External(url)   => ResultsLinkResponse(ResultsLinkKind.External, url.toString)
    case ResultsLink.Internal(value) => ResultsLinkResponse(ResultsLinkKind.Internal, value)
  }
  implicit val decoder: Decoder[ResultsLinkResponse] = deriveDecoder
  implicit val encoder: Encoder[ResultsLinkResponse] = deriveEncoder
}

sealed abstract class ResultsLinkKind extends EnumEntry

object ResultsLinkKind extends Enum[ResultsLinkKind] {

  case object External extends ResultsLinkKind
  case object Internal extends ResultsLinkKind

  override val values: IndexedSeq[ResultsLinkKind] = findValues

  implicit val decoder: Decoder[ResultsLinkKind] = Circe.decodeCaseInsensitive(this)
  implicit val encoder: Encoder[ResultsLinkKind] = Circe.encoderLowercase(this)

}
