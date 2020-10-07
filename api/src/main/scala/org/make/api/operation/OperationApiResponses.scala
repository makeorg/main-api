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

package org.make.api.operation

import java.time.ZonedDateTime

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.api.operation.ResultsLinkRequest.ResultsLinkKind
import org.make.core.CirceFormatters
import org.make.core.operation._

import scala.annotation.meta.field
@ApiModel
final case class OperationIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "49207ae1-0732-42f5-a0d0-af4ff8c4c2de")
  operationId: OperationId
)

object OperationIdResponse {
  implicit val encoder: Encoder[OperationIdResponse] = deriveEncoder[OperationIdResponse]
}

@ApiModel
final case class ModerationOperationResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "49207ae1-0732-42f5-a0d0-af4ff8c4c2de")
  id: OperationId,
  @(ApiModelProperty @field)(dataType = "string", example = "Active")
  status: OperationStatus,
  slug: String,
  @(ApiModelProperty @field)(dataType = "dateTime")
  createdAt: Option[ZonedDateTime],
  @(ApiModelProperty @field)(dataType = "dateTime")
  updatedAt: Option[ZonedDateTime],
  @(ApiModelProperty @field)(dataType = "string", example = "BUSINESS_CONSULTATION")
  operationKind: OperationKind
)

object ModerationOperationResponse extends CirceFormatters {
  implicit val encoder: Encoder[ModerationOperationResponse] = deriveEncoder[ModerationOperationResponse]
  implicit val decoder: Decoder[ModerationOperationResponse] = deriveDecoder[ModerationOperationResponse]

  def apply(operation: SimpleOperation): ModerationOperationResponse = {
    ModerationOperationResponse(
      id = operation.operationId,
      status = operation.status,
      slug = operation.slug,
      createdAt = operation.createdAt,
      updatedAt = operation.updatedAt,
      operationKind = operation.operationKind
    )
  }
}

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
