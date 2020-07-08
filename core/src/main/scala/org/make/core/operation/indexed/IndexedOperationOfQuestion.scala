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

import cats.Show
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import io.circe.refined._
import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.MaxSize
import io.swagger.annotations.ApiModelProperty
import org.make.core.{BusinessConfig, CirceFormatters}
import org.make.core.operation.{OperationId, OperationOfQuestion, QuestionTheme, ResultsLink, SimpleOperation}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}

import scala.annotation.meta.field

object OperationOfQuestionElasticsearchFieldNames {
  val questionId = "questionId"
  val question = "question"
  val questionKeyword = "question.keyword"
  val questionGeneral = "question.general"
  val slug = "slug"
  val startDate = "startDate"
  val endDate = "endDate"
  val description = " description"
  val country = "country"
  val language = "language"
  val operationId = "operationId"
  val operationTitle = "operationTitle"
  val operationKind = "operationKind"
  val featured = "featured"
  val status = "status"
  val participantsCount = "participantsCount"
  val proposalsCount = "proposalsCount"

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

case class IndexedOperationOfQuestion(
  @(ApiModelProperty @field)(dataType = "string", example = "42ccc3ce-f5b9-e7c0-b927-01a9cb159e55") questionId: QuestionId,
  question: String,
  slug: String,
  questionShortTitle: Option[String],
  @(ApiModelProperty @field)(example = "2019-01-23T16:32:00.000Z")
  startDate: Option[ZonedDateTime],
  @(ApiModelProperty @field)(example = "2019-01-23T16:32:00.000Z")
  endDate: Option[ZonedDateTime],
  @(ApiModelProperty @field)(dataType = "string", example = "Finished") status: OperationOfQuestion.Status,
  theme: QuestionTheme,
  description: String,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/consultation-image.png")
  consultationImage: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "consultation image alternative")
  consultationImageAlt: Option[String Refined MaxSize[W.`130`.T]],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/description-image.png")
  descriptionImage: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "description image alternative")
  descriptionImageAlt: Option[String Refined MaxSize[W.`130`.T]],
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language,
  @(ApiModelProperty @field)(dataType = "string", example = "57c4a8d0-f3c1-4391-b75b-03082ac94d19")
  operationId: OperationId,
  operationTitle: String,
  operationKind: String,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/about")
  aboutUrl: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/results")
  resultsLink: Option[String],
  proposalsCount: Int,
  participantsCount: Int,
  actions: Option[String],
  featured: Boolean
)

object IndexedOperationOfQuestion extends CirceFormatters {
  implicit val encoder: Encoder[IndexedOperationOfQuestion] = deriveEncoder[IndexedOperationOfQuestion]
  implicit val decoder: Decoder[IndexedOperationOfQuestion] = deriveDecoder[IndexedOperationOfQuestion]

  def createFromOperationOfQuestion(
    operationOfQuestion: OperationOfQuestion,
    operation: SimpleOperation,
    question: Question
  ): IndexedOperationOfQuestion = {
    IndexedOperationOfQuestion(
      questionId = operationOfQuestion.questionId,
      question = question.question,
      slug = question.slug,
      questionShortTitle = question.shortTitle,
      startDate = operationOfQuestion.startDate,
      endDate = operationOfQuestion.endDate,
      status = operationOfQuestion.status,
      theme = operationOfQuestion.theme,
      description = operationOfQuestion.description,
      consultationImage = operationOfQuestion.consultationImage,
      consultationImageAlt = operationOfQuestion.consultationImageAlt,
      descriptionImage = operationOfQuestion.descriptionImage,
      descriptionImageAlt = operationOfQuestion.descriptionImageAlt,
      country = question.country,
      language = question.language,
      operationId = operationOfQuestion.operationId,
      operationTitle = operationOfQuestion.operationTitle,
      operationKind = operation.operationKind.shortName,
      aboutUrl = operationOfQuestion.aboutUrl,
      resultsLink = operationOfQuestion.resultsLink.map(Show[ResultsLink].show),
      proposalsCount = operationOfQuestion.proposalsCount,
      participantsCount = operationOfQuestion.participantsCount,
      actions = operationOfQuestion.actions,
      featured = operationOfQuestion.featured
    )
  }
}

final case class OperationOfQuestionSearchResult(total: Long, results: Seq[IndexedOperationOfQuestion])

object OperationOfQuestionSearchResult {
  implicit val encoder: Encoder[OperationOfQuestionSearchResult] = deriveEncoder[OperationOfQuestionSearchResult]
  implicit val decoder: Decoder[OperationOfQuestionSearchResult] = deriveDecoder[OperationOfQuestionSearchResult]

  def empty: OperationOfQuestionSearchResult = OperationOfQuestionSearchResult(0, Seq.empty)
}
