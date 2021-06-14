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
import cats.data.NonEmptyList
import enumeratum.values.StringEnum
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import io.circe.refined._
import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.MaxSize
import io.swagger.annotations.ApiModelProperty
import org.make.core.elasticsearch.ElasticsearchFieldName
import org.make.core.{BusinessConfig, CirceFormatters}
import org.make.core.operation.{OperationId, OperationOfQuestion, QuestionTheme, ResultsLink, SimpleOperation}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}

import scala.annotation.meta.field

sealed abstract class OperationOfQuestionElasticsearchFieldName(val value: String, val sortable: Boolean = false)
    extends ElasticsearchFieldName

object OperationOfQuestionElasticsearchFieldName extends StringEnum[OperationOfQuestionElasticsearchFieldName] {

  sealed abstract class Simple(val field: String, override val sortable: Boolean = false)
      extends OperationOfQuestionElasticsearchFieldName(field, sortable) {
    override def parameter: String = field
  }

  sealed abstract class Alias(
    val parameter: String,
    aliased: OperationOfQuestionElasticsearchFieldName,
    override val sortable: Boolean = false
  ) extends OperationOfQuestionElasticsearchFieldName(parameter, sortable) {
    override def field: String = aliased.field
  }

  case object questionId extends Simple("questionId")
  case object question extends Simple("question", sortable = true)
  case object questionKeyword extends Simple("question.keyword")
  case object questionGeneral extends Simple("question.general")
  case object slug extends Simple("slug", sortable = true)
  case object startDate extends Simple("startDate", sortable = true)
  case object endDate extends Simple("endDate", sortable = true)
  case object description extends Simple(" description", sortable = true)
  case object countries extends Simple("countries", sortable = true)
  case object country extends Alias("country", countries, sortable = true)
  case object language extends Simple("language", sortable = true)
  case object operationId extends Simple("operationId")
  case object operationTitle extends Simple("operationTitle")
  case object operationKind extends Simple("operationKind", sortable = true)
  case object featured extends Simple("featured")
  case object status extends Simple("status")
  case object participantsCount extends Simple("participantsCount")
  case object proposalsCount extends Simple("proposalsCount")
  case object resultsLink extends Simple("resultsLink")

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

  override def values: IndexedSeq[OperationOfQuestionElasticsearchFieldName] = findValues
}

final case class IndexedOperationOfQuestion(
  @(ApiModelProperty @field)(dataType = "string", example = "42ccc3ce-f5b9-e7c0-b927-01a9cb159e55") questionId: QuestionId,
  question: String,
  slug: String,
  questionShortTitle: Option[String],
  @(ApiModelProperty @field)(dataType = "dateTime")
  startDate: ZonedDateTime,
  @(ApiModelProperty @field)(dataType = "dateTime")
  endDate: ZonedDateTime,
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
  countries: NonEmptyList[Country],
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
  featured: Boolean,
  top20ConsensusThreshold: Option[Double]
) {
  val immutableFields: IndexedOperationOfQuestion.ImmutableFields =
    IndexedOperationOfQuestion.ImmutableFields(top20ConsensusThreshold = top20ConsensusThreshold)

  def applyImmutableFields(fields: IndexedOperationOfQuestion.ImmutableFields): IndexedOperationOfQuestion =
    this.copy(top20ConsensusThreshold = fields.top20ConsensusThreshold)
}

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
      countries = question.countries,
      language = question.language,
      operationId = operationOfQuestion.operationId,
      operationTitle = operationOfQuestion.operationTitle,
      operationKind = operation.operationKind.value,
      aboutUrl = operationOfQuestion.aboutUrl,
      resultsLink = operationOfQuestion.resultsLink.map(Show[ResultsLink].show),
      proposalsCount = operationOfQuestion.proposalsCount,
      participantsCount = operationOfQuestion.participantsCount,
      actions = operationOfQuestion.actions,
      featured = operationOfQuestion.featured,
      top20ConsensusThreshold = None
    )
  }

  final case class ImmutableFields(top20ConsensusThreshold: Option[Double])

  object ImmutableFields {
    val empty: ImmutableFields = ImmutableFields(top20ConsensusThreshold = None)
  }

}

final case class OperationOfQuestionSearchResult(total: Long, results: Seq[IndexedOperationOfQuestion])

object OperationOfQuestionSearchResult {
  implicit val encoder: Encoder[OperationOfQuestionSearchResult] = deriveEncoder[OperationOfQuestionSearchResult]
  implicit val decoder: Decoder[OperationOfQuestionSearchResult] = deriveDecoder[OperationOfQuestionSearchResult]

  def empty: OperationOfQuestionSearchResult = OperationOfQuestionSearchResult(0, Seq.empty)
}
