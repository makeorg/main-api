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

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}
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
  SequenceTranslation,
  SpecificSequenceConfiguration,
  SpecificSequenceConfigurationId
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

final case class SequenceResult(proposals: Seq[ProposalResponse])

object SequenceResult {
  implicit val encoder: Encoder[SequenceResult] = deriveEncoder[SequenceResult]
}
@ApiModel
final case class SequenceConfigurationResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d2b2694a-25cf-4eaa-9181-026575d58cf8")
  id: QuestionId,
  @(ApiModelProperty @field)(dataType = "string", example = "fd735649-e63d-4464-9d93-10da54510a12")
  sequenceId: SequenceId,
  main: SpecificSequenceConfigurationResponse,
  controversial: SpecificSequenceConfigurationResponse,
  popular: SpecificSequenceConfigurationResponse,
  keyword: SpecificSequenceConfigurationResponse,
  @(ApiModelProperty @field)(dataType = "int", example = "100")
  newProposalsVoteThreshold: Int,
  @(ApiModelProperty @field)(dataType = "double", example = "0.8")
  testedProposalsEngagementThreshold: Option[Double],
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  testedProposalsScoreThreshold: Option[Double],
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  testedProposalsControversyThreshold: Option[Double],
  @(ApiModelProperty @field)(dataType = "int", example = "1500")
  testedProposalsMaxVotesThreshold: Option[Int],
  @(ApiModelProperty @field)(dataType = "double", example = "0.5")
  nonSequenceVotesWeight: Double
)

object SequenceConfigurationResponse {
  implicit val decoder: Decoder[SequenceConfigurationResponse] = deriveDecoder[SequenceConfigurationResponse]
  implicit val encoder: Encoder[SequenceConfigurationResponse] = deriveEncoder[SequenceConfigurationResponse]

  def fromSequenceConfiguration(configuration: SequenceConfiguration): SequenceConfigurationResponse = {
    SequenceConfigurationResponse(
      id = configuration.questionId,
      sequenceId = configuration.sequenceId,
      main = SpecificSequenceConfigurationResponse.fromSpecificSequenceConfiguration(configuration.mainSequence),
      controversial =
        SpecificSequenceConfigurationResponse.fromSpecificSequenceConfiguration(configuration.controversial),
      popular = SpecificSequenceConfigurationResponse.fromSpecificSequenceConfiguration(configuration.popular),
      keyword = SpecificSequenceConfigurationResponse.fromSpecificSequenceConfiguration(configuration.keyword),
      newProposalsVoteThreshold = configuration.newProposalsVoteThreshold,
      testedProposalsEngagementThreshold = configuration.testedProposalsEngagementThreshold,
      testedProposalsScoreThreshold = configuration.testedProposalsScoreThreshold,
      testedProposalsControversyThreshold = configuration.testedProposalsControversyThreshold,
      testedProposalsMaxVotesThreshold = configuration.testedProposalsMaxVotesThreshold,
      nonSequenceVotesWeight = configuration.nonSequenceVotesWeight
    )
  }
}

final case class SpecificSequenceConfigurationResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "fd735649-e63d-4464-9d93-10da54510a12")
  specificSequenceConfigurationId: SpecificSequenceConfigurationId,
  @(ApiModelProperty @field)(dataType = "int", example = "12")
  sequenceSize: Int,
  @(ApiModelProperty @field)(dataType = "double", example = "0.5")
  newProposalsRatio: Double,
  @(ApiModelProperty @field)(dataType = "int", example = "1000")
  maxTestedProposalCount: Int,
  @(ApiModelProperty @field)(dataType = "string", example = "Bandit")
  selectionAlgorithmName: SelectionAlgorithmName,
  @(ApiModelProperty @field)(dataType = "boolean", example = "false")
  intraIdeaEnabled: Boolean,
  @(ApiModelProperty @field)(dataType = "int", example = "1")
  intraIdeaMinCount: Int,
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  intraIdeaProposalsRatio: Double,
  @(ApiModelProperty @field)(dataType = "boolean", example = "true")
  interIdeaCompetitionEnabled: Boolean,
  @(ApiModelProperty @field)(dataType = "int", example = "50")
  interIdeaCompetitionTargetCount: Int,
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  interIdeaCompetitionControversialRatio: Double,
  @(ApiModelProperty @field)(dataType = "int", example = "0")
  interIdeaCompetitionControversialCount: Int
)

object SpecificSequenceConfigurationResponse {
  implicit val decoder: Codec[SpecificSequenceConfigurationResponse] = deriveCodec

  def fromSpecificSequenceConfiguration(
    configuration: SpecificSequenceConfiguration
  ): SpecificSequenceConfigurationResponse = {
    SpecificSequenceConfigurationResponse(
      specificSequenceConfigurationId = configuration.specificSequenceConfigurationId,
      sequenceSize = configuration.sequenceSize,
      newProposalsRatio = configuration.newProposalsRatio,
      maxTestedProposalCount = configuration.maxTestedProposalCount,
      selectionAlgorithmName = configuration.selectionAlgorithmName,
      intraIdeaEnabled = configuration.intraIdeaEnabled,
      intraIdeaMinCount = configuration.intraIdeaMinCount,
      intraIdeaProposalsRatio = configuration.intraIdeaProposalsRatio,
      interIdeaCompetitionEnabled = configuration.interIdeaCompetitionEnabled,
      interIdeaCompetitionTargetCount = configuration.interIdeaCompetitionTargetCount,
      interIdeaCompetitionControversialRatio = configuration.interIdeaCompetitionControversialRatio,
      interIdeaCompetitionControversialCount = configuration.interIdeaCompetitionControversialCount
    )
  }
}
