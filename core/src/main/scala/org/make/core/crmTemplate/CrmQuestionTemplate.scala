/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

package org.make.core.crmTemplate

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.make.core.StringValue
import org.make.core.question.QuestionId

final case class CrmQuestionTemplate(
  id: CrmQuestionTemplateId,
  kind: CrmTemplateKind,
  questionId: QuestionId,
  template: TemplateId
)

object CrmQuestionTemplate {
  implicit val encoder: Encoder[CrmQuestionTemplate] = deriveEncoder[CrmQuestionTemplate]
  implicit val decoder: Decoder[CrmQuestionTemplate] = deriveDecoder[CrmQuestionTemplate]
}

final case class CrmQuestionTemplateId(value: String) extends StringValue

object CrmQuestionTemplateId {
  implicit val CrmQuestionTemplateIdEncoder: Encoder[CrmQuestionTemplateId] =
    Encoder[String].contramap(_.value)
  implicit val CrmQuestionTemplateIdDecoder: Decoder[CrmQuestionTemplateId] =
    Decoder[String].map(CrmQuestionTemplateId.apply)
}
