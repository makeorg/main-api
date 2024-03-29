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

import eu.timepit.refined.types.numeric.PosInt
import io.circe.refined._
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.api.demographics.DemographicsCardResponse
import org.make.api.proposal.ProposalResponse
import org.make.core.question.QuestionId
import org.make.core.sequence.{
  ExplorationSequenceConfiguration,
  ExplorationSequenceConfigurationId,
  ExplorationSortAlgorithm,
  SelectionAlgorithmName,
  SequenceConfiguration,
  SequenceId,
  SpecificSequenceConfiguration,
  SpecificSequenceConfigurationId
}
import org.make.core.technical.RefinedTypes.Ratio

import scala.annotation.meta.field

final case class ExplorationSequenceConfigurationResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "9963fff6-85c7-4cb5-8698-31c5e8204d6e")
  explorationSequenceConfigurationId: ExplorationSequenceConfigurationId,
  @(ApiModelProperty @field)(dataType = "int", example = "12")
  sequenceSize: PosInt,
  @(ApiModelProperty @field)(dataType = "int", example = "1000")
  maxTestedProposalCount: PosInt,
  @(ApiModelProperty @field)(dataType = "double", example = "0.5")
  newRatio: Ratio,
  @(ApiModelProperty @field)(dataType = "double", example = "0.1")
  controversyRatio: Ratio,
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "bandit,random,round-robin", example = "bandit")
  topSorter: ExplorationSortAlgorithm,
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "bandit,random,round-robin", example = "bandit")
  controversySorter: ExplorationSortAlgorithm,
  @(ApiModelProperty @field)(dataType = "double", example = "0.2")
  keywordsThreshold: Ratio,
  @(ApiModelProperty @field)(dataType = "int", example = "2")
  candidatesPoolSize: Int
)

object ExplorationSequenceConfigurationResponse {
  implicit val encoder: Encoder[ExplorationSequenceConfigurationResponse] = deriveEncoder

  def fromExplorationConfiguration(conf: ExplorationSequenceConfiguration): ExplorationSequenceConfigurationResponse =
    ExplorationSequenceConfigurationResponse(
      explorationSequenceConfigurationId = conf.explorationSequenceConfigurationId,
      sequenceSize = conf.sequenceSize,
      maxTestedProposalCount = conf.maxTestedProposalCount,
      newRatio = conf.newRatio,
      controversyRatio = conf.controversyRatio,
      topSorter = conf.topSorter,
      controversySorter = conf.controversySorter,
      keywordsThreshold = conf.keywordsThreshold,
      candidatesPoolSize = conf.candidatesPoolSize
    )
}

@ApiModel
final case class SequenceConfigurationResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d2b2694a-25cf-4eaa-9181-026575d58cf8")
  id: QuestionId,
  @(ApiModelProperty @field)(dataType = "string", example = "fd735649-e63d-4464-9d93-10da54510a12")
  sequenceId: SequenceId,
  main: ExplorationSequenceConfigurationResponse,
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
  implicit val encoder: Encoder[SequenceConfigurationResponse] = deriveEncoder[SequenceConfigurationResponse]

  def fromSequenceConfiguration(configuration: SequenceConfiguration): SequenceConfigurationResponse = {
    SequenceConfigurationResponse(
      id = configuration.questionId,
      sequenceId = configuration.sequenceId,
      main = ExplorationSequenceConfigurationResponse.fromExplorationConfiguration(configuration.mainSequence),
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
  sequenceSize: PosInt,
  @(ApiModelProperty @field)(dataType = "double", example = "0.5")
  newProposalsRatio: Double,
  @(ApiModelProperty @field)(dataType = "int", example = "1000")
  maxTestedProposalCount: PosInt,
  @(ApiModelProperty @field)(dataType = "string", example = "Bandit")
  selectionAlgorithmName: SelectionAlgorithmName
)

object SpecificSequenceConfigurationResponse {
  implicit val encoder: Encoder[SpecificSequenceConfigurationResponse] = deriveEncoder

  def fromSpecificSequenceConfiguration(
    configuration: SpecificSequenceConfiguration
  ): SpecificSequenceConfigurationResponse = {
    SpecificSequenceConfigurationResponse(
      specificSequenceConfigurationId = configuration.specificSequenceConfigurationId,
      sequenceSize = configuration.sequenceSize,
      newProposalsRatio = configuration.newProposalsRatio,
      maxTestedProposalCount = configuration.maxTestedProposalCount,
      selectionAlgorithmName = configuration.selectionAlgorithmName
    )
  }
}

final case class KeywordSequenceResult(
  key: String,
  label: String,
  proposals: Seq[ProposalResponse],
  demographics: Option[DemographicsCardResponse]
)

object KeywordSequenceResult {
  implicit val encoder: Encoder[KeywordSequenceResult] = deriveEncoder[KeywordSequenceResult]
}

final case class FirstProposalResponse(proposal: ProposalResponse, sequenceSize: PosInt)

object FirstProposalResponse {
  implicit val encoder: Encoder[FirstProposalResponse] = deriveEncoder[FirstProposalResponse]
}
