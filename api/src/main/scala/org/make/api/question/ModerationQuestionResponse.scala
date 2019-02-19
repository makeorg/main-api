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

package org.make.api.question
import java.time.LocalDate

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations.ApiModelProperty
import org.make.core.CirceFormatters
import org.make.core.operation.{Operation, OperationId, OperationOfQuestion}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId

import scala.annotation.meta.field

case class ModerationQuestionResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d2b2694a-25cf-4eaa-9181-026575d58cf8")
  id: QuestionId,
  slug: String,
  question: String,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language
)
object ModerationQuestionResponse {

  def apply(question: Question): ModerationQuestionResponse = ModerationQuestionResponse(
    id = question.questionId,
    slug = question.slug,
    question = question.question,
    country = question.country,
    language = question.language
  )

  implicit val encoder: Encoder[ModerationQuestionResponse] = deriveEncoder[ModerationQuestionResponse]
  implicit val decoder: Decoder[ModerationQuestionResponse] = deriveDecoder[ModerationQuestionResponse]
}

case class QuestionDetailsResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d2b2694a-25cf-4eaa-9181-026575d58cf8")
  questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "string", example = "49207ae1-0732-42f5-a0d0-af4ff8c4c2de")
  operationId: OperationId,
  slug: String,
  question: String,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language,
  allowedSources: Seq[String],
  @(ApiModelProperty @field)(dataType = "string", example = "1970-01-01")
  startDate: Option[LocalDate],
  @(ApiModelProperty @field)(dataType = "string", example = "1970-01-01")
  endDate: Option[LocalDate],
  @(ApiModelProperty @field)(dataType = "string", example = "fd735649-e63d-4464-9d93-10da54510a12")
  landingSequenceId: SequenceId,
  operationTitle: String,
  canPropose: Boolean
)

object QuestionDetailsResponse extends CirceFormatters {
  def apply(question: Question,
            operation: Operation,
            operationOfQuestion: OperationOfQuestion): QuestionDetailsResponse = QuestionDetailsResponse(
    questionId = question.questionId,
    operationId = operation.operationId,
    slug = question.slug,
    question = question.question,
    country = question.country,
    language = question.language,
    allowedSources = operation.allowedSources,
    startDate = operationOfQuestion.startDate,
    endDate = operationOfQuestion.endDate,
    landingSequenceId = operationOfQuestion.landingSequenceId,
    operationTitle = operationOfQuestion.operationTitle,
    canPropose = operationOfQuestion.canPropose
  )

  implicit val encoder: Encoder[QuestionDetailsResponse] = deriveEncoder[QuestionDetailsResponse]
  implicit val decoder: Decoder[QuestionDetailsResponse] = deriveDecoder[QuestionDetailsResponse]
}
