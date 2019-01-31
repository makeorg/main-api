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

package org.make.api.sequence

import java.time.ZonedDateTime

import io.circe.ObjectEncoder
import io.circe.generic.semiauto.deriveEncoder
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.api.proposal.ProposalResponse
import org.make.api.user.UserResponse
import org.make.core.{CirceFormatters, RequestContext}
import org.make.core.proposal.ProposalId
import org.make.core.reference.ThemeId
import org.make.core.sequence.{SequenceId, SequenceStatus, SequenceTranslation}
import org.make.core.tag.TagId

import scala.annotation.meta.field

@ApiModel
final case class SequenceResponse(sequenceId: SequenceId,
                                  slug: String,
                                  title: String,
                                  @(ApiModelProperty @field)(dataType = "list[string]") tagIds: Seq[TagId] = Seq.empty,
                                  @(ApiModelProperty @field)(dataType = "list[string]") proposalIds: Seq[ProposalId] =
                                    Seq.empty,
                                  @(ApiModelProperty @field)(dataType = "list[string]") themeIds: Seq[ThemeId],
                                  status: SequenceStatus,
                                  creationContext: RequestContext,
                                  createdAt: Option[ZonedDateTime],
                                  updatedAt: Option[ZonedDateTime],
                                  sequenceTranslation: Seq[SequenceTranslation] = Seq.empty,
                                  events: Seq[SequenceActionResponse])

object SequenceResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[SequenceResponse] = deriveEncoder[SequenceResponse]
}

final case class SequenceActionResponse(date: ZonedDateTime,
                                        user: Option[UserResponse],
                                        actionType: String,
                                        arguments: Map[String, String])

object SequenceActionResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[SequenceActionResponse] = deriveEncoder[SequenceActionResponse]
}

final case class SequenceResult(
  @(ApiModelProperty @field)(dataType = "string", example = "fd735649-e63d-4464-9d93-10da54510a12")
  id: SequenceId,
  title: String,
  slug: String,
  proposals: Seq[ProposalResponse]
)

object SequenceResult {
  implicit val encoder: ObjectEncoder[SequenceResult] = deriveEncoder[SequenceResult]
}
