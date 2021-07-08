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
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations.ApiModelProperty
import org.make.core.question.QuestionId
import org.make.core.sequence._
import org.make.core.technical.RefinedTypes.Ratio

import scala.annotation.meta.field

final case class SequenceConfigurationRequest(
  main: ExplorationSequenceConfigurationRequest,
  controversial: SpecificSequenceConfigurationRequest,
  popular: SpecificSequenceConfigurationRequest,
  keyword: SpecificSequenceConfigurationRequest,
  @(ApiModelProperty @field)(dataType = "int", example = "100") newProposalsVoteThreshold: Int,
  @(ApiModelProperty @field)(dataType = "double", example = "0.8")
  testedProposalsEngagementThreshold: Option[Double],
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  testedProposalsScoreThreshold: Option[Double],
  @(ApiModelProperty @field)(dataType = "double", example = "0.0") testedProposalsControversyThreshold: Option[Double],
  @(ApiModelProperty @field)(dataType = "int", example = "1500") testedProposalsMaxVotesThreshold: Option[Int],
  @(ApiModelProperty @field)(dataType = "double", example = "0.5") nonSequenceVotesWeight: Double
) {

  def toSequenceConfiguration(sequenceId: SequenceId, questionId: QuestionId): SequenceConfiguration = {
    SequenceConfiguration(
      sequenceId = sequenceId,
      questionId = questionId,
      mainSequence = main.toExplorationConfiguration,
      controversial = controversial.toSpecificSequenceConfiguration,
      popular = popular.toSpecificSequenceConfiguration,
      keyword = keyword.toSpecificSequenceConfiguration,
      newProposalsVoteThreshold = newProposalsVoteThreshold,
      testedProposalsEngagementThreshold = testedProposalsEngagementThreshold,
      testedProposalsScoreThreshold = testedProposalsScoreThreshold,
      testedProposalsControversyThreshold = testedProposalsControversyThreshold,
      testedProposalsMaxVotesThreshold = testedProposalsMaxVotesThreshold,
      nonSequenceVotesWeight = nonSequenceVotesWeight
    )
  }
}

object SequenceConfigurationRequest {
  implicit val decoder: Decoder[SequenceConfigurationRequest] = deriveDecoder[SequenceConfigurationRequest]
}

final case class ExplorationSequenceConfigurationRequest(
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
) {
  def toExplorationConfiguration: ExplorationSequenceConfiguration = ExplorationSequenceConfiguration(
    explorationSequenceConfigurationId = explorationSequenceConfigurationId,
    sequenceSize = sequenceSize,
    maxTestedProposalCount = maxTestedProposalCount,
    newRatio = newRatio,
    controversyRatio = controversyRatio,
    topSorter = topSorter,
    controversySorter = controversySorter,
    keywordsThreshold = keywordsThreshold,
    candidatesPoolSize = candidatesPoolSize
  )
}

object ExplorationSequenceConfigurationRequest {
  implicit val decoder: Decoder[ExplorationSequenceConfigurationRequest] = deriveDecoder
}

final case class SpecificSequenceConfigurationRequest(
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
) {
  def toSpecificSequenceConfiguration: SpecificSequenceConfiguration = {
    SpecificSequenceConfiguration(
      specificSequenceConfigurationId = specificSequenceConfigurationId,
      sequenceSize = sequenceSize,
      newProposalsRatio = newProposalsRatio,
      maxTestedProposalCount = maxTestedProposalCount,
      selectionAlgorithmName = selectionAlgorithmName
    )
  }
}

object SpecificSequenceConfigurationRequest {
  implicit val decoder: Decoder[SpecificSequenceConfigurationRequest] =
    deriveDecoder[SpecificSequenceConfigurationRequest]
}
