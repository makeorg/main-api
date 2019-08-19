/*
 *  Make.org Core API
 *  Copyright (C) 2018-2019 Make.org
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

package org.make.api.crmTemplates
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.swagger.annotations.ApiModelProperty
import org.make.core.Validation
import org.make.core.crmTemplate.{CrmTemplates, CrmTemplatesId, TemplateId}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}

import scala.annotation.meta.field

// Requests
final case class CreateTemplatesRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "d1397b66-1f3d-4350-bfd9-d57775b83355")
  questionId: Option[QuestionId],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Option[Country],
  @(ApiModelProperty @field)(dataType = "string", example = "fr") language: Option[Language],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") registration: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") welcome: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalAccepted: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalRefused: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") forgottenPassword: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalAcceptedOrganisation: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalRefusedOrganisation: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") forgottenPasswordOrganisation: TemplateId
) {
  def getLocale: Option[String] =
    for {
      c <- country
      l <- language
    } yield s"${l.value.toLowerCase}_${c.value.toUpperCase}"

  Validation.validate(
    Validation.requirePresent(
      fieldName = "questionId",
      fieldValue = questionId.orElse(getLocale),
      message = Some("At least one of questionId or country+language must exist.")
    )
  )
}

object CreateTemplatesRequest {
  implicit val decoder: Decoder[CreateTemplatesRequest] = deriveDecoder[CreateTemplatesRequest]
}

final case class UpdateTemplatesRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "123456") registration: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") welcome: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalAccepted: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalRefused: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") forgottenPassword: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalAcceptedOrganisation: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalRefusedOrganisation: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") forgottenPasswordOrganisation: TemplateId
)

object UpdateTemplatesRequest {
  implicit val decoder: Decoder[UpdateTemplatesRequest] = deriveDecoder[UpdateTemplatesRequest]
}

// Responses
final case class CrmTemplatesIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "068f1039-c36c-458f-9c3f-90fe193af1e4") id: CrmTemplatesId
)

object CrmTemplatesIdResponse {
  implicit val encoder: Encoder[CrmTemplatesIdResponse] = deriveEncoder[CrmTemplatesIdResponse]
  implicit val decoder: Decoder[CrmTemplatesIdResponse] = deriveDecoder[CrmTemplatesIdResponse]
}

final case class CrmTemplatesResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "9d0202f9-f67e-4680-92e9-4d7f9e3ac5d9") id: CrmTemplatesId,
  @(ApiModelProperty @field)(dataType = "string", example = "455e7543-6f1e-42a9-95a8-8071e2182c4d") questionId: Option[
    QuestionId
  ],
  @(ApiModelProperty @field)(dataType = "string", example = "fr_FR") locale: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") registration: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") welcome: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalAccepted: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalRefused: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") forgottenPassword: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalAcceptedOrganisation: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalRefusedOrganisation: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") forgottenPasswordOrganisation: TemplateId
)

object CrmTemplatesResponse {
  implicit val encoder: Encoder[CrmTemplatesResponse] = deriveEncoder[CrmTemplatesResponse]
  implicit val decoder: Decoder[CrmTemplatesResponse] = deriveDecoder[CrmTemplatesResponse]

  def apply(crmTemplates: CrmTemplates): CrmTemplatesResponse =
    CrmTemplatesResponse(
      id = crmTemplates.crmTemplatesId,
      questionId = crmTemplates.questionId,
      locale = crmTemplates.locale,
      registration = crmTemplates.registration,
      welcome = crmTemplates.welcome,
      proposalAccepted = crmTemplates.proposalAccepted,
      proposalRefused = crmTemplates.proposalRefused,
      forgottenPassword = crmTemplates.forgottenPassword,
      proposalAcceptedOrganisation = crmTemplates.proposalAcceptedOrganisation,
      proposalRefusedOrganisation = crmTemplates.proposalRefusedOrganisation,
      forgottenPasswordOrganisation = crmTemplates.forgottenPasswordOrganisation
    )
}
