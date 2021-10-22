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

import cats.data.NonEmptyList
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.swagger.annotations.ApiModelProperty
import org.make.api.question.SimpleQuestionWordingResponse
import org.make.core.operation.indexed.IndexedOperationOfQuestion
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}

import java.time.ZonedDateTime
import scala.annotation.meta.field

final case class ModerationOperationOfQuestionInfosResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "a90d4223-a198-480b-b412-53970c5f1151")
  questionId: QuestionId,
  slug: String,
  @(ApiModelProperty @field)(dataType = "org.make.api.question.SimpleQuestionWordingResponse")
  wording: SimpleQuestionWordingResponse,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  countries: NonEmptyList[Country],
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language,
  @(ApiModelProperty @field)(dataType = "dateTime") startDate: ZonedDateTime,
  @(ApiModelProperty @field)(dataType = "dateTime") endDate: ZonedDateTime,
  totalProposalCount: Int,
  proposalToModerateCount: Int,
  hasTags: Boolean
)

object ModerationOperationOfQuestionInfosResponse {
  def apply(
    question: IndexedOperationOfQuestion,
    proposalToModerateCount: Int,
    totalProposalCount: Int,
    hasTags: Boolean
  ): ModerationOperationOfQuestionInfosResponse = ModerationOperationOfQuestionInfosResponse(
    questionId = question.questionId,
    slug = question.slug,
    wording = SimpleQuestionWordingResponse(question.operationTitle, question.question),
    countries = question.countries,
    language = question.language,
    startDate = question.startDate,
    endDate = question.endDate,
    totalProposalCount = totalProposalCount,
    proposalToModerateCount = proposalToModerateCount,
    hasTags = hasTags
  )

  implicit val codec: Codec[ModerationOperationOfQuestionInfosResponse] = deriveCodec
}
