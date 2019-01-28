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

package org.make.core.idea.indexed

import java.time.ZonedDateTime

import io.circe.generic.semiauto._
import io.circe.{Decoder, ObjectEncoder}
import io.swagger.annotations.ApiModelProperty
import org.make.core.CirceFormatters
import org.make.core.idea.{Idea, IdeaId, IdeaStatus}
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}

import scala.annotation.meta.field

object IdeaElasticsearchFieldNames {
  val ideaId: String = "ideaId"
  val name: String = "name"
  val nameFr: String = "name.fr"
  val nameEn: String = "name.en"
  val nameIt: String = "name.it"
  val nameDe: String = "name.de"
  val nameBg: String = "name.bg"
  val nameCs: String = "name.cs"
  val nameDa: String = "name.da"
  val nameNl: String = "name.nl"
  val nameFi: String = "name.fi"
  val nameEl: String = "name.el"
  val nameHu: String = "name.hu"
  val nameLv: String = "name.lv"
  val nameLt: String = "name.lt"
  val namePt: String = "name.pt"
  val nameRo: String = "name.ro"
  val nameEs: String = "name.es"
  val nameSv: String = "name.sv"
  val namePl: String = "name.pl"
  val nameHr: String = "name.hr"
  val nameEt: String = "name.et"
  val nameMt: String = "name.mt"
  val nameSk: String = "name.sk"
  val nameSl: String = "name.sl"
  val nameGeneral: String = "name.general"
  val questionId: String = "questionId"
  val operationId: String = "operationId"
  val themeId: String = "themeId"
  val question: String = "question"
  val country: String = "country"
  val language: String = "language"
  val status: String = "status"
  val createdAt: String = "createdAt"
  val updatedAt: String = "updatedAt"
}

case class IndexedIdea(
  @(ApiModelProperty @field)(dataType = "string", example = "a10086bb-4312-4486-8f57-91b5e92b3eb9") ideaId: IdeaId,
  name: String,
  @(ApiModelProperty @field)(dataType = "string", example = "57b1d160-2593-46bd-b7ad-f5e99ba3aa0d") questionId: Option[
    QuestionId
  ],
  @(ApiModelProperty @field)(dataType = "string", example = "f4767b7b-06c1-479d-8bc1-6e2a2de97f22") operationId: Option[
    OperationId
  ],
  @(ApiModelProperty @field)(dataType = "string", example = "e65fb52e-6438-4074-a79f-adb38fdee544") themeId: Option[
    ThemeId
  ],
  question: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Option[Country],
  @(ApiModelProperty @field)(dataType = "string", example = "fr") language: Option[Language],
  @(ApiModelProperty @field)(dataType = "string", example = "Activated") status: IdeaStatus,
  @(ApiModelProperty @field)(dataType = "string", example = "2019-01-23T12:12:12.012Z") createdAt: ZonedDateTime,
  @(ApiModelProperty @field)(dataType = "string", example = "2019-01-23T12:12:12.012Z") updatedAt: Option[ZonedDateTime]
)

object IndexedIdea extends CirceFormatters {
  implicit val encoder: ObjectEncoder[IndexedIdea] = deriveEncoder[IndexedIdea]
  implicit val decoder: Decoder[IndexedIdea] = deriveDecoder[IndexedIdea]

  def createFromIdea(idea: Idea): IndexedIdea = {
    IndexedIdea(
      ideaId = idea.ideaId,
      name = idea.name,
      questionId = idea.questionId,
      operationId = idea.operationId,
      themeId = idea.themeId,
      question = idea.question,
      country = idea.country,
      language = idea.language,
      status = idea.status,
      createdAt = idea.createdAt match {
        case Some(date) => date
        case _          => throw new IllegalStateException("created at required")
      },
      updatedAt = idea.updatedAt
    )
  }
}

final case class IdeaSearchResult(total: Long, results: Seq[IndexedIdea])

object IdeaSearchResult {
  implicit val encoder: ObjectEncoder[IdeaSearchResult] = deriveEncoder[IdeaSearchResult]
  implicit val decoder: Decoder[IdeaSearchResult] = deriveDecoder[IdeaSearchResult]

  def empty: IdeaSearchResult = IdeaSearchResult(0, Seq.empty)
}
