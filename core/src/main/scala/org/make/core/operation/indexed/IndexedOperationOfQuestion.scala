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
import spray.json.DefaultJsonProtocol._
import io.swagger.annotations.ApiModelProperty
import org.make.core.{BusinessConfig, CirceFormatters, SprayJsonFormatters}
import org.make.core.operation.{OperationId, OperationOfQuestion, QuestionTheme, SimpleOperation}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

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

  def questionLanguageSubfield(language: Language, stemmed: Boolean = false): Option[String] = {
    BusinessConfig.supportedCountries
      .find(_.supportedLanguages.contains(language))
      .map { _ =>
        if (stemmed)
          s"question.$language-stemmed"
        else
          s"question.$language"
      }
  }
}

case class IndexedOperationOfQuestion(@(ApiModelProperty @field)(
                                        dataType = "string",
                                        example = "42ccc3ce-f5b9-e7c0-b927-01a9cb159e55"
                                      ) questionId: QuestionId,
                                      question: String,
                                      slug: String,
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

object IndexedOperationOfQuestion extends CirceFormatters with SprayJsonFormatters {
  implicit val encoder: Encoder[IndexedOperationOfQuestion] = deriveEncoder[IndexedOperationOfQuestion]
  implicit val decoder: Decoder[IndexedOperationOfQuestion] = deriveDecoder[IndexedOperationOfQuestion]

  implicit val format: RootJsonFormat[IndexedOperationOfQuestion] =
    DefaultJsonProtocol.jsonFormat14(IndexedOperationOfQuestion.apply)

  def createFromOperationOfQuestion(operationOfQuestion: OperationOfQuestion,
                                    operation: SimpleOperation,
                                    question: Question): IndexedOperationOfQuestion = {
    IndexedOperationOfQuestion(
      questionId = operationOfQuestion.questionId,
      question = question.question,
      slug = question.slug,
      startDate = operationOfQuestion.startDate,
      endDate = operationOfQuestion.endDate,
      theme = operationOfQuestion.theme,
      description = operationOfQuestion.description,
      imageUrl = operationOfQuestion.imageUrl,
      country = question.country,
      language = question.language,
      operationId = operationOfQuestion.operationId,
      operationTitle = operationOfQuestion.operationTitle,
      operationKind = operation.operationKind.shortName,
      aboutUrl = operationOfQuestion.aboutUrl
    )
  }
}

final case class OperationOfQuestionSearchResult(total: Long, results: Seq[IndexedOperationOfQuestion])

object OperationOfQuestionSearchResult {
  implicit val encoder: Encoder[OperationOfQuestionSearchResult] = deriveEncoder[OperationOfQuestionSearchResult]
  implicit val decoder: Decoder[OperationOfQuestionSearchResult] = deriveDecoder[OperationOfQuestionSearchResult]

  implicit val format: RootJsonFormat[OperationOfQuestionSearchResult] =
    DefaultJsonProtocol.jsonFormat2(OperationOfQuestionSearchResult.apply)

  def empty: OperationOfQuestionSearchResult = OperationOfQuestionSearchResult(0, Seq.empty)
}
