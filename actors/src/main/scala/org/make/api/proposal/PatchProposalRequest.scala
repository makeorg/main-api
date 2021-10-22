/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.proposal

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.swagger.annotations.ApiModelProperty
import org.make.core.{ApplicationName, CirceFormatters}
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal.{ProposalKeyword, ProposalStatus}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, LabelId, Language}
import org.make.core.session.{SessionId, VisitorId}
import org.make.core.tag.TagId
import org.make.core.user.UserId

import java.time.ZonedDateTime
import scala.annotation.meta.field

final case class PatchProposalRequest(
  slug: Option[String] = None,
  content: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  author: Option[UserId] = None,
  @(ApiModelProperty @field)(dataType = "list[string]")
  labels: Option[Seq[LabelId]] = None,
  @(ApiModelProperty @field)(
    dataType = "string",
    example = "Accepted",
    allowableValues = "Pending,Accepted,Refused,Postponed,Archived"
  )
  status: Option[ProposalStatus] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "other")
  refusalReason: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "list[string]")
  tags: Option[Seq[TagId]] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "2d791a66-3cd5-4a2e-a117-9daa68bd3a33")
  questionId: Option[QuestionId] = None,
  creationContext: Option[PatchRequestContext] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "2a774774-33ca-41a3-a0fa-65931397fbfc")
  ideaId: Option[IdeaId] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "3a9cd696-7e0b-4758-952c-04ae6798039a")
  operation: Option[OperationId] = None,
  @(ApiModelProperty @field)(dataType = "boolean")
  initialProposal: Option[Boolean] = None,
  keywords: Option[Seq[ProposalKeyword]] = None
)

object PatchProposalRequest {
  implicit val encoder: Encoder[PatchProposalRequest] = deriveEncoder[PatchProposalRequest]
  implicit val decoder: Decoder[PatchProposalRequest] = deriveDecoder[PatchProposalRequest]
}

final case class PatchRequestContext(
  @(ApiModelProperty @field)(dataType = "string", example = "5b13da67-17bb-413f-9f4f-e73699383153")
  requestId: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "af938667-a15a-482b-bd0f-681f09c83e51")
  sessionId: Option[SessionId] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "e52d2ac3-a929-43ec-acfa-fb1f486a8c75")
  visitorId: Option[VisitorId] = None,
  @(ApiModelProperty @field)(dataType = "dateTime")
  visitorCreatedAt: Option[ZonedDateTime] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "ea3caa65-0bc3-430a-9af9-e8c473730601")
  externalId: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Option[Country] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  detectedCountry: Option[Country] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Option[Language] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "2d791a66-3cd5-4a2e-a117-9daa68bd3a33")
  operation: Option[OperationId] = None,
  source: Option[String] = None,
  location: Option[String] = None,
  question: Option[String] = None,
  hostname: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "0.0.0.x")
  ipAddress: Option[String] = None,
  ipAddressHash: Option[String] = None,
  getParameters: Option[Map[String, String]] = None,
  userAgent: Option[String] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "f626cabc-d0f1-49ef-aec1-eb8d50c8dda1")
  questionId: Option[QuestionId] = None,
  @(ApiModelProperty @field)(
    dataType = "string",
    example = "main-front",
    allowableValues = "main-front,legacy-front,backoffice,widget,widget-manager,dial,bi-batchs,dial-batchs,infra"
  )
  applicationName: Option[ApplicationName] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "main-front")
  referrer: Option[String] = None,
  customData: Option[Map[String, String]] = None
)

object PatchRequestContext extends CirceFormatters {
  implicit val encoder: Encoder[PatchRequestContext] = deriveEncoder[PatchRequestContext]
  implicit val decoder: Decoder[PatchRequestContext] = deriveDecoder[PatchRequestContext]
}
