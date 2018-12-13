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
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.make.core.operation.OperationId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language, ThemeId}

case class QuestionResponse(questionId: QuestionId,
                            slug: String,
                            question: String,
                            country: Country,
                            language: Language,
                            operationId: Option[OperationId],
                            themeId: Option[ThemeId])
object QuestionResponse {

  def apply(question: Question): QuestionResponse = QuestionResponse(
    questionId = question.questionId,
    slug = question.slug,
    question = question.question,
    operationId = question.operationId,
    themeId = question.themeId,
    country = question.country,
    language = question.language
  )

  implicit val encoder: Encoder[QuestionResponse] = deriveEncoder[QuestionResponse]
  implicit val decoder: Decoder[QuestionResponse] = deriveDecoder[QuestionResponse]
}
