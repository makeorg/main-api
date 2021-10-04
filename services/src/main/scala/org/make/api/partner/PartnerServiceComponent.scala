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

package org.make.api.partner

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations.ApiModelProperty
import org.make.core.partner.{Partner, PartnerId, PartnerKind}
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import org.make.core.Order

import scala.concurrent.Future
import org.make.core.technical.Pagination._

import scala.annotation.meta.field

trait PartnerServiceComponent {
  def partnerService: PartnerService
}

trait PartnerService {
  def getPartner(partnerId: PartnerId): Future[Option[Partner]]
  def find(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    questionId: Option[QuestionId],
    organisationId: Option[UserId],
    partnerKind: Option[PartnerKind]
  ): Future[Seq[Partner]]
  def count(
    questionId: Option[QuestionId],
    organisationId: Option[UserId],
    partnerKind: Option[PartnerKind]
  ): Future[Int]
  def createPartner(request: CreatePartnerRequest): Future[Partner]
  def updatePartner(partnerId: PartnerId, request: UpdatePartnerRequest): Future[Option[Partner]]
  def deletePartner(partnerId: PartnerId): Future[Unit]
}

final case class CreatePartnerRequest(
  name: String,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/logo.png")
  logo: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/link")
  link: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  organisationId: Option[UserId],
  @(ApiModelProperty @field)(dataType = "string", example = "FOUNDER")
  partnerKind: PartnerKind,
  @(ApiModelProperty @field)(dataType = "string", example = "6a90575f-f625-4025-a485-8769e8a26967")
  questionId: QuestionId,
  weight: Float
)

object CreatePartnerRequest {
  implicit val decoder: Decoder[CreatePartnerRequest] = deriveDecoder[CreatePartnerRequest]
}

final case class UpdatePartnerRequest(
  name: String,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/logo.png")
  logo: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/link")
  link: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  organisationId: Option[UserId],
  @(ApiModelProperty @field)(dataType = "string", example = "FOUNDER")
  partnerKind: PartnerKind,
  weight: Float
)

object UpdatePartnerRequest {
  implicit val decoder: Decoder[UpdatePartnerRequest] = deriveDecoder[UpdatePartnerRequest]
}
