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
import io.swagger.annotations.ApiModelProperty
import org.make.core.StringValue
import org.make.core.question.QuestionId

import scala.annotation.meta.field

final case class CrmQuestionTemplate(
  @(ApiModelProperty @field)(dataType = "string", example = "97077fa2-888d-4683-a12c-1b74e0a39991") id: CrmQuestionTemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "Welcome") kind: CrmTemplateKind,
  @(ApiModelProperty @field)(dataType = "string", example = "b5a66352-a081-4518-a909-3fa1ec95e224") questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") template: TemplateId
) extends CrmTemplate

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
