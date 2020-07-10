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
import org.make.core.Validation
import org.make.core.Validation.{validateUserInput, _}
import org.make.core.operation.{OperationId, OperationKind, OperationStatus, ResultsLink}
import org.make.core.reference.Language

import scala.annotation.meta.field
import scala.util.Try

@ApiModel
final case class ModerationCreateOperationRequest(
  slug: String,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  defaultLanguage: Language,
  @(ApiModelProperty @field)(dataType = "string", example = "PUBLIC_CONSULTATION")
  operationKind: OperationKind,
  allowedSources: Seq[String]
) {
  OperationValidation.validateCreate(defaultLanguage = defaultLanguage, slug = slug, allowedSources = allowedSources)
}

object ModerationCreateOperationRequest {
  implicit val decoder: Decoder[ModerationCreateOperationRequest] = deriveDecoder[ModerationCreateOperationRequest]
}
@ApiModel
final case class ModerationUpdateOperationRequest(
  status: String,
  slug: String,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  defaultLanguage: Language,
  @(ApiModelProperty @field)(dataType = "string", example = "PUBLIC_CONSULTATION")
  operationKind: OperationKind,
  allowedSources: Seq[String]
) {
  OperationValidation.validateUpdate(
    defaultLanguage = defaultLanguage,
    status = status,
    slug = slug,
    allowedSources = allowedSources
  )
}

object ModerationUpdateOperationRequest {
  implicit val decoder: Decoder[ModerationUpdateOperationRequest] = deriveDecoder[ModerationUpdateOperationRequest]
}

@ApiModel
final case class OperationIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "49207ae1-0732-42f5-a0d0-af4ff8c4c2de")
  operationId: OperationId
)

object OperationIdResponse {
  implicit val encoder: Encoder[OperationIdResponse] = deriveEncoder[OperationIdResponse]
}

private object OperationValidation {
  private val maxLanguageLength = 3

  def validateCreate(defaultLanguage: Language, slug: String, allowedSources: Seq[String]): Unit = {
    allowedSources.foreach { source =>
      validate(validateUserInput("allowedSources", source, None))
    }
    validate(
      maxLength("defaultLanguage", maxLanguageLength, defaultLanguage.value),
      maxLength("countryConfiguration", maxLanguageLength, defaultLanguage.value),
      requireValidSlug("slug", Some(slug), Some("Invalid slug")),
      validateUserInput("slug", slug, None)
    )
    validate(Validation.requireNonEmpty("allowedSources", allowedSources))
  }

  def validateUpdate(defaultLanguage: Language, status: String, slug: String, allowedSources: Seq[String]): Unit = {
    validateCreate(defaultLanguage, slug, allowedSources)
    val validStatusChoices: Seq[String] = OperationStatus.statusMap.toSeq.map {
      case (name, _) => name
    }
    validate(validChoices(fieldName = "status", userChoices = Seq(status), validChoices = validStatusChoices))
  }
}

final case class ResultsLinkRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "internal") kind: ResultsLinkKind,
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
