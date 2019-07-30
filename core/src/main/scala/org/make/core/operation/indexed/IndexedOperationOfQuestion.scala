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

package org.make.core.operation.indexed

import java.time.ZonedDateTime

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import io.swagger.annotations.ApiModelProperty
import org.make.core.CirceFormatters
import org.make.core.operation.{OperationId, QuestionTheme}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}

import scala.annotation.meta.field

object OperationOfQuestionElasticsearchFieldNames {
  val questionId = "questionId"
  val question = "question"
  val questionKeyword = "question.keyword"
  val questionGeneral = "question.general"
  val startDate = "startDate"
  val endDate = "endDate"
  val description = " description"
  val country = "country"
  val language = "language"
  val operationKind = "operationKind"
}

case class IndexedOperationOfQuestion(@(ApiModelProperty @field)(
                                        dataType = "string",
                                        example = "42ccc3ce-f5b9-e7c0-b927-01a9cb159e55"
                                      ) questionId: QuestionId,
                                      question: String,
                                      @(ApiModelProperty @field)(example = "2019-01-23T16:32:00.000Z")
                                      startDate: Option[ZonedDateTime],
                                      @(ApiModelProperty @field)(example = "2019-01-23T16:32:00.000Z")
                                      endDate: Option[ZonedDateTime],
                                      theme: QuestionTheme,
                                      description: String,
                                      imageUrl: Option[String],
                                      @(ApiModelProperty @field)(dataType = "string", example = "FR")
                                      country: Country,
                                      @(ApiModelProperty @field)(dataType = "string", example = "fr")
                                      language: Language,
                                      operationId: OperationId,
                                      operationTitle: String,
                                      operationKind: String,
                                      aboutUrl: Option[String])

object IndexedOperationOfQuestion extends CirceFormatters {
  implicit val encoder: Encoder[IndexedOperationOfQuestion] = deriveEncoder[IndexedOperationOfQuestion]
  implicit val decoder: Decoder[IndexedOperationOfQuestion] = deriveDecoder[IndexedOperationOfQuestion]
}

final case class OperationOfQuestionSearchResult(total: Long, results: Seq[IndexedOperationOfQuestion])

object OperationOfQuestionSearchResult {
  implicit val encoder: Encoder[OperationOfQuestionSearchResult] = deriveEncoder[OperationOfQuestionSearchResult]
  implicit val decoder: Decoder[OperationOfQuestionSearchResult] = deriveDecoder[OperationOfQuestionSearchResult]

  def empty: OperationOfQuestionSearchResult = OperationOfQuestionSearchResult(0, Seq.empty)
}
