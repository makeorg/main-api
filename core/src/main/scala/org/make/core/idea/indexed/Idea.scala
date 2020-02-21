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
import io.circe.{Decoder, Encoder}
import org.make.core.CirceFormatters
import org.make.core.idea.{Idea, IdeaId, IdeaStatus}
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}

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
  val question: String = "question"
  val country: String = "country"
  val language: String = "language"
  val status: String = "status"
  val createdAt: String = "createdAt"
  val updatedAt: String = "updatedAt"
  val proposalsCount: String = "proposalsCount"
}

case class IndexedIdea(ideaId: IdeaId,
                       name: String,
                       questionId: Option[QuestionId],
                       operationId: Option[OperationId],
                       question: Option[String],
                       country: Option[Country],
                       language: Option[Language],
                       status: IdeaStatus,
                       createdAt: ZonedDateTime,
                       updatedAt: Option[ZonedDateTime],
                       proposalsCount: Int)

object IndexedIdea extends CirceFormatters {
  implicit val encoder: Encoder[IndexedIdea] = deriveEncoder[IndexedIdea]
  implicit val decoder: Decoder[IndexedIdea] = deriveDecoder[IndexedIdea]

  def createFromIdea(idea: Idea, proposalsCount: Int): IndexedIdea = {
    IndexedIdea(
      ideaId = idea.ideaId,
      name = idea.name,
      questionId = idea.questionId,
      operationId = idea.operationId,
      question = idea.question,
      country = idea.country,
      language = idea.language,
      status = idea.status,
      createdAt = idea.createdAt match {
        case Some(date) => date
        case _          => throw new IllegalStateException("created at required")
      },
      updatedAt = idea.updatedAt,
      proposalsCount = proposalsCount
    )
  }
}

final case class IdeaSearchResult(total: Long, results: Seq[IndexedIdea])

object IdeaSearchResult {
  implicit val encoder: Encoder[IdeaSearchResult] = deriveEncoder[IdeaSearchResult]
  implicit val decoder: Decoder[IdeaSearchResult] = deriveDecoder[IdeaSearchResult]

  def empty: IdeaSearchResult = IdeaSearchResult(0, Seq.empty)
}
