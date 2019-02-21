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

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.core.Validation
import org.make.core.Validation.{validateUserInput, _}
import org.make.core.operation.{OperationId, OperationStatus}
import org.make.core.reference.Language

import scala.annotation.meta.field

@ApiModel
final case class ModerationCreateOperationRequest(slug: String,
                                                  @(ApiModelProperty @field)(dataType = "string", example = "fr")
                                                  defaultLanguage: Language,
                                                  allowedSources: Seq[String]) {
  OperationValidation.validateCreate(defaultLanguage = defaultLanguage, slug = slug, allowedSources = allowedSources)
}

object ModerationCreateOperationRequest {
  implicit val decoder: Decoder[ModerationCreateOperationRequest] = deriveDecoder[ModerationCreateOperationRequest]
}
@ApiModel
final case class ModerationUpdateOperationRequest(status: String,
                                                  slug: String,
                                                  @(ApiModelProperty @field)(dataType = "string", example = "fr")
                                                  defaultLanguage: Language,
                                                  allowedSources: Seq[String]) {
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
