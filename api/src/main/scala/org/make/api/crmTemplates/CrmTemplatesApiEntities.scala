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
import org.make.core.crmTemplate.{CrmTemplates, CrmTemplatesId, TemplateId}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}

import scala.annotation.meta.field

trait CrmTemplatesRequest {
  val registration: Option[TemplateId]
  val welcome: Option[TemplateId]
  val proposalAccepted: Option[TemplateId]
  val proposalRefused: Option[TemplateId]
  val forgottenPassword: Option[TemplateId]
  val resendRegistration: Option[TemplateId]
  val proposalAcceptedOrganisation: Option[TemplateId]
  val proposalRefusedOrganisation: Option[TemplateId]
  val forgottenPasswordOrganisation: Option[TemplateId]
  val organisationEmailChangeConfirmation: Option[TemplateId]
  val registrationB2B: Option[TemplateId]
}

final case class CreateTemplatesRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "d1397b66-1f3d-4350-bfd9-d57775b83355")
  questionId: Option[QuestionId],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr") language: Language,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") registration: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") welcome: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalAccepted: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalRefused: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") forgottenPassword: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") resendRegistration: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalAcceptedOrganisation: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalRefusedOrganisation: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") forgottenPasswordOrganisation: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") organisationEmailChangeConfirmation: Option[
    TemplateId
  ],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") registrationB2B: Option[TemplateId]
) extends CrmTemplatesRequest {
  def getLocale: String = s"${language.value.toLowerCase}_${country.value.toUpperCase}"
}

object CreateTemplatesRequest {
  implicit val decoder: Decoder[CreateTemplatesRequest] = deriveDecoder[CreateTemplatesRequest]
}

final case class UpdateTemplatesRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "123456") registration: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") welcome: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalAccepted: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalRefused: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") forgottenPassword: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") resendRegistration: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalAcceptedOrganisation: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalRefusedOrganisation: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") forgottenPasswordOrganisation: Option[TemplateId],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") organisationEmailChangeConfirmation: Option[
    TemplateId
  ],
  @(ApiModelProperty @field)(dataType = "string", example = "123456") registrationB2B: Option[TemplateId]
) extends CrmTemplatesRequest

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
  @(ApiModelProperty @field)(dataType = "string", example = "123456") resendRegistration: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalAcceptedOrganisation: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalRefusedOrganisation: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") forgottenPasswordOrganisation: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") organisationEmailChangeConfirmation: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") registrationB2B: TemplateId
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
      resendRegistration = crmTemplates.resendRegistration,
      proposalAcceptedOrganisation = crmTemplates.proposalAcceptedOrganisation,
      proposalRefusedOrganisation = crmTemplates.proposalRefusedOrganisation,
      forgottenPasswordOrganisation = crmTemplates.forgottenPasswordOrganisation,
      organisationEmailChangeConfirmation = crmTemplates.organisationEmailChangeConfirmation,
      registrationB2B = crmTemplates.registrationB2B
    )
}
