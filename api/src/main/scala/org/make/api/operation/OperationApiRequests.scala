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

import java.net.URL

import enumeratum.{Circe, Enum, EnumEntry}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.api.operation.ResultsLinkRequest.ResultsLinkKind
import org.make.core.Validation.{validateUserInput, _}
import org.make.core.operation.{OperationKind, OperationStatus, ResultsLink}

import scala.annotation.meta.field
import scala.util.Try

@ApiModel
final case class ModerationCreateOperationRequest(
  slug: String,
  @(ApiModelProperty @field)(dataType = "string", example = "BUSINESS_CONSULTATION")
  operationKind: OperationKind
) {
  OperationValidation.validateSlug(slug = slug)
}

object ModerationCreateOperationRequest {
  implicit val decoder: Decoder[ModerationCreateOperationRequest] = deriveDecoder[ModerationCreateOperationRequest]
}
@ApiModel
final case class ModerationUpdateOperationRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "Active")
  status: Option[OperationStatus],
  slug: String,
  @(ApiModelProperty @field)(dataType = "string", example = "BUSINESS_CONSULTATION")
  operationKind: OperationKind
) {
  OperationValidation.validateSlug(slug = slug)
}

object ModerationUpdateOperationRequest {
  implicit val decoder: Decoder[ModerationUpdateOperationRequest] = deriveDecoder[ModerationUpdateOperationRequest]
}

private object OperationValidation {
  def validateSlug(slug: String): Unit = {
    validate(requireValidSlug("slug", Some(slug), Some("Invalid slug")), validateUserInput("slug", slug, None))
  }
}

final case class ResultsLinkRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "Internal", allowableValues = "External,Internal") kind: ResultsLinkKind,
  @(ApiModelProperty @field)(dataType = "string", example = "results") value: String
) {

  validate(kind match {
    case ResultsLinkKind.External =>
      validateField("value", "invalid_content", Try(new URL(value)).isSuccess, s"Invalid URL: '$value'")
    case ResultsLinkKind.Internal =>
      validChoices(
        "value",
        Some(s"Invalid internal link: '$value'"),
        Seq(value),
        ResultsLink.Internal.values.map(_.value)
      )
  })

  @ApiModelProperty(hidden = true)
  val resultsLink: Option[ResultsLink] = ResultsLink.parse(value)

}

object ResultsLinkRequest {

  sealed abstract class ResultsLinkKind extends EnumEntry

  object ResultsLinkKind extends Enum[ResultsLinkKind] {

    case object External extends ResultsLinkKind
    case object Internal extends ResultsLinkKind

    override val values: IndexedSeq[ResultsLinkKind] = findValues

    implicit val decoder: Decoder[ResultsLinkKind] = Circe.decodeCaseInsensitive(this)
    implicit val encoder: Encoder[ResultsLinkKind] = Circe.encoderLowercase(this)
  }

  implicit val decoder: Decoder[ResultsLinkRequest] = deriveDecoder
  implicit val encoder: Encoder[ResultsLinkRequest] = deriveEncoder

}
