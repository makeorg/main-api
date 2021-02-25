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

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.api.proposal.ProposalResponse
import org.make.api.user.UserResponse
import org.make.core.{CirceFormatters, RequestContext}
import org.make.core.proposal.ProposalId
import org.make.core.question.QuestionId
import org.make.core.sequence.{
  SelectionAlgorithmName,
  SequenceConfiguration,
  SequenceId,
  SequenceStatus,
  SequenceTranslation
}
import org.make.core.tag.TagId

import scala.annotation.meta.field

@ApiModel
final case class SequenceResponse(
  sequenceId: SequenceId,
  slug: String,
  title: String,
  @(ApiModelProperty @field)(dataType = "list[string]") tagIds: Seq[TagId] = Seq.empty,
  @(ApiModelProperty @field)(dataType = "list[string]") proposalIds: Seq[ProposalId] = Seq.empty,
  status: SequenceStatus,
  creationContext: RequestContext,
  createdAt: Option[ZonedDateTime],
  updatedAt: Option[ZonedDateTime],
  sequenceTranslation: Seq[SequenceTranslation] = Seq.empty,
  events: Seq[SequenceActionResponse]
)

object SequenceResponse extends CirceFormatters {
  implicit val encoder: Encoder[SequenceResponse] = deriveEncoder[SequenceResponse]
}

final case class SequenceActionResponse(
  date: ZonedDateTime,
  user: Option[UserResponse],
  actionType: String,
  arguments: Map[String, String]
)

object SequenceActionResponse extends CirceFormatters {
  implicit val encoder: Encoder[SequenceActionResponse] = deriveEncoder[SequenceActionResponse]
}

final case class SequenceResult(
  @(ApiModelProperty @field)(dataType = "string", example = "fd735649-e63d-4464-9d93-10da54510a12")
  id: SequenceId,
  title: String,
  slug: String,
  proposals: Seq[ProposalResponse]
)

object SequenceResult {
  implicit val encoder: Encoder[SequenceResult] = deriveEncoder[SequenceResult]
}
@ApiModel
final case class SequenceConfigurationResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "fd735649-e63d-4464-9d93-10da54510a12")
  sequenceId: SequenceId,
  @(ApiModelProperty @field)(dataType = "string", example = "d2b2694a-25cf-4eaa-9181-026575d58cf8")
  questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "int", example = "12")
  sequenceSize: Int = 12,
  @(ApiModelProperty @field)(dataType = "double", example = "0.5")
  newProposalsRatio: Double = 0.5,
  @(ApiModelProperty @field)(dataType = "int", example = "1000")
  maxTestedProposalCount: Int = 1000,
  @(ApiModelProperty @field)(dataType = "string", example = "Bandit")
  selectionAlgorithmName: SelectionAlgorithmName = SelectionAlgorithmName.Bandit,
  @(ApiModelProperty @field)(dataType = "boolean", example = "false")
  intraIdeaEnabled: Boolean = true,
  @(ApiModelProperty @field)(dataType = "int", example = "1")
  intraIdeaMinCount: Int = 1,
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  intraIdeaProposalsRatio: Double = 0.0,
  @(ApiModelProperty @field)(dataType = "boolean", example = "false")
  interIdeaCompetitionEnabled: Boolean = true,
  @(ApiModelProperty @field)(dataType = "int", example = "50")
  interIdeaCompetitionTargetCount: Int = 50,
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  interIdeaCompetitionControversialRatio: Double = 0.0,
  @(ApiModelProperty @field)(dataType = "int", example = "0")
  interIdeaCompetitionControversialCount: Int = 0,
  @(ApiModelProperty @field)(dataType = "int", example = "100")
  newProposalsVoteThreshold: Int = 10,
  @(ApiModelProperty @field)(dataType = "double", example = "0.8")
  testedProposalsEngagementThreshold: Option[Double] = None,
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  testedProposalsScoreThreshold: Option[Double] = None,
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  testedProposalsControversyThreshold: Option[Double] = None,
  @(ApiModelProperty @field)(dataType = "int", example = "1500")
  testedProposalsMaxVotesThreshold: Option[Int] = None,
  @(ApiModelProperty @field)(dataType = "double", example = "0.5")
  nonSequenceVotesWeight: Double = 0.5
)

object SequenceConfigurationResponse {
  implicit val decoder: Decoder[SequenceConfigurationResponse] = deriveDecoder[SequenceConfigurationResponse]
  implicit val encoder: Encoder[SequenceConfigurationResponse] = deriveEncoder[SequenceConfigurationResponse]

  def fromSequenceConfiguration(configuration: SequenceConfiguration): SequenceConfigurationResponse = {
    SequenceConfigurationResponse(
      sequenceId = configuration.sequenceId,
      questionId = configuration.questionId,
      sequenceSize = configuration.mainSequence.sequenceSize,
      newProposalsRatio = configuration.mainSequence.newProposalsRatio,
      maxTestedProposalCount = configuration.mainSequence.maxTestedProposalCount,
      selectionAlgorithmName = configuration.mainSequence.selectionAlgorithmName,
      intraIdeaEnabled = configuration.mainSequence.intraIdeaEnabled,
      intraIdeaMinCount = configuration.mainSequence.intraIdeaMinCount,
      intraIdeaProposalsRatio = configuration.mainSequence.intraIdeaProposalsRatio,
      interIdeaCompetitionEnabled = configuration.mainSequence.interIdeaCompetitionEnabled,
      interIdeaCompetitionTargetCount = configuration.mainSequence.interIdeaCompetitionTargetCount,
      interIdeaCompetitionControversialRatio = configuration.mainSequence.interIdeaCompetitionControversialRatio,
      interIdeaCompetitionControversialCount = configuration.mainSequence.interIdeaCompetitionControversialCount,
      newProposalsVoteThreshold = configuration.newProposalsVoteThreshold,
      testedProposalsEngagementThreshold = configuration.testedProposalsEngagementThreshold,
      testedProposalsScoreThreshold = configuration.testedProposalsScoreThreshold,
      testedProposalsControversyThreshold = configuration.testedProposalsControversyThreshold,
      testedProposalsMaxVotesThreshold = configuration.testedProposalsMaxVotesThreshold,
      nonSequenceVotesWeight = configuration.nonSequenceVotesWeight
    )
  }
}
