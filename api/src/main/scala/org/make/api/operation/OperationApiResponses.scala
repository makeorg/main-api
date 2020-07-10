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
import org.make.core.question.QuestionId
import org.make.core.reference.Language
import org.make.core.tag.TagId

import scala.annotation.meta.field

@ApiModel
final case class OperationResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "49207ae1-0732-42f5-a0d0-af4ff8c4c2de")
  operationId: OperationId,
  @(ApiModelProperty @field)(dataType = "string", example = "Active")
  status: OperationStatus,
  slug: String,
  translations: Seq[OperationTranslation] = Seq.empty,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  defaultLanguage: Language,
  @(ApiModelProperty @field)(example = "2019-01-23T16:32:00.000Z")
  createdAt: Option[ZonedDateTime],
  @(ApiModelProperty @field)(example = "2019-01-23T16:32:00.000Z")
  updatedAt: Option[ZonedDateTime],
  countriesConfiguration: Seq[OperationCountryConfiguration],
  @(ApiModelProperty @field)(dataType = "string", example = "PUBLIC_CONSULTATION")
  operationKind: OperationKind
)

object OperationResponse extends CirceFormatters {
  implicit val encoder: Encoder[OperationResponse] = deriveEncoder[OperationResponse]
  implicit val decoder: Decoder[OperationResponse] = deriveDecoder[OperationResponse]

  def apply(operation: Operation, tags: Map[QuestionId, Seq[TagId]]): OperationResponse = {
    OperationResponse(
      operationId = operation.operationId,
      status = operation.status,
      slug = operation.slug,
      translations = operation.questions.map { question =>
        OperationTranslation(title = question.details.operationTitle, language = question.question.language)
      },
      defaultLanguage = operation.defaultLanguage,
      createdAt = operation.createdAt,
      updatedAt = operation.updatedAt,
      countriesConfiguration = operation.questions.map { question =>
        OperationCountryConfiguration(
          countryCode = question.question.country,
          tagIds = tags.get(question.question.questionId).toSeq.flatten,
          landingSequenceId = question.details.landingSequenceId,
          startDate = question.details.startDate,
          questionId = Some(question.question.questionId),
          endDate = question.details.endDate
        )
      },
      operationKind = operation.operationKind
    )
  }
}

@ApiModel
final case class ModerationOperationResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "49207ae1-0732-42f5-a0d0-af4ff8c4c2de")
  id: OperationId,
  @(ApiModelProperty @field)(dataType = "string", example = "Active")
  status: OperationStatus,
  slug: String,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  defaultLanguage: Language,
  @(ApiModelProperty @field)(dataType = "string", example = "2019-01-23T11:20:00.000Z")
  createdAt: Option[ZonedDateTime],
  @(ApiModelProperty @field)(dataType = "string", example = "2019-01-23T11:20:00.000Z")
  updatedAt: Option[ZonedDateTime],
  allowedSources: Seq[String],
  @(ApiModelProperty @field)(dataType = "string", example = "PUBLIC_CONSULTATION")
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
      defaultLanguage = operation.defaultLanguage,
      createdAt = operation.createdAt,
      updatedAt = operation.updatedAt,
      allowedSources = operation.allowedSources,
      operationKind = operation.operationKind
    )
  }
}

final case class ResultsLinkResponse(kind: ResultsLinkKind, value: String)
object ResultsLinkResponse {
  def apply(resultsLink: ResultsLink): ResultsLinkResponse = resultsLink match {
    case ResultsLink.External(url)   => ResultsLinkResponse(ResultsLinkKind.External, url.toString)
    case ResultsLink.Internal(value) => ResultsLinkResponse(ResultsLinkKind.Internal, value)
  }
  implicit val decoder: Decoder[ResultsLinkResponse] = deriveDecoder
  implicit val encoder: Encoder[ResultsLinkResponse] = deriveEncoder
}
