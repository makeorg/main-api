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
import org.make.core.proposal.{QualificationKey, VoteKey}

import scala.annotation.meta.field

final case class UpdateVoteRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "agree", allowableValues = "agree,disagree,neutral")
  key: VoteKey,
  @(ApiModelProperty @field)(dataType = "int")
  count: Option[Int] = None,
  @(ApiModelProperty @field)(dataType = "int")
  countVerified: Option[Int] = None,
  @(ApiModelProperty @field)(dataType = "int")
  countSequence: Option[Int] = None,
  @(ApiModelProperty @field)(dataType = "int")
  countSegment: Option[Int] = None,
  qualifications: Seq[UpdateQualificationRequest]
)

object UpdateVoteRequest {
  implicit val decoder: Decoder[UpdateVoteRequest] = deriveDecoder[UpdateVoteRequest]
  implicit val encoder: Encoder[UpdateVoteRequest] =
    deriveEncoder[UpdateVoteRequest]

}

final case class UpdateQualificationRequest(
  @(ApiModelProperty @field)(
    dataType = "string",
    example = "likeIt",
    allowableValues =
      "likeIt,doable,platitudeAgree,noWay,impossible,platitudeDisagree,doNotUnderstand,noOpinion,doNotCare"
  )
  key: QualificationKey,
  @(ApiModelProperty @field)(dataType = "int")
  count: Option[Int] = None,
  @(ApiModelProperty @field)(dataType = "int")
  countVerified: Option[Int] = None,
  @(ApiModelProperty @field)(dataType = "int")
  countSequence: Option[Int] = None,
  @(ApiModelProperty @field)(dataType = "int")
  countSegment: Option[Int] = None
)

object UpdateQualificationRequest {
  implicit val decoder: Decoder[UpdateQualificationRequest] = deriveDecoder[UpdateQualificationRequest]
  implicit val encoder: Encoder[UpdateQualificationRequest] =
    deriveEncoder[UpdateQualificationRequest]

}
