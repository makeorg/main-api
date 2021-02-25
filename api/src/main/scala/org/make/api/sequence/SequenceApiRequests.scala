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

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations.ApiModelProperty
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.sequence._

import scala.annotation.meta.field

final case class ContextFilterRequest(
  operation: Option[OperationId] = None,
  source: Option[String] = None,
  location: Option[String] = None,
  question: Option[String] = None
) {
  def toContext: ContextSearchFilter = {
    ContextSearchFilter(operation, source, location, question)
  }
}

object ContextFilterRequest {
  implicit val decoder: Decoder[ContextFilterRequest] = deriveDecoder[ContextFilterRequest]
}

final case class SequenceConfigurationRequest(
  @(ApiModelProperty @field)(dataType = "double", example = "0.5")
  newProposalsRatio: Double,
  @(ApiModelProperty @field)(dataType = "int", example = "100") newProposalsVoteThreshold: Int,
  @(ApiModelProperty @field)(dataType = "double", example = "0.8")
  testedProposalsEngagementThreshold: Option[Double],
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  testedProposalsScoreThreshold: Option[Double],
  @(ApiModelProperty @field)(dataType = "double", example = "0.0") testedProposalsControversyThreshold: Option[Double],
  @(ApiModelProperty @field)(dataType = "int", example = "1500") testedProposalsMaxVotesThreshold: Option[Int],
  @(ApiModelProperty @field)(dataType = "boolean", example = "false") intraIdeaEnabled: Boolean,
  @(ApiModelProperty @field)(dataType = "int", example = "1") intraIdeaMinCount: Int,
  @(ApiModelProperty @field)(dataType = "double", example = "0.0") intraIdeaProposalsRatio: Double,
  @(ApiModelProperty @field)(dataType = "boolean", example = "false") interIdeaCompetitionEnabled: Boolean,
  @(ApiModelProperty @field)(dataType = "int", example = "50") interIdeaCompetitionTargetCount: Int,
  @(ApiModelProperty @field)(dataType = "double", example = "0.0") interIdeaCompetitionControversialRatio: Double,
  @(ApiModelProperty @field)(dataType = "int", example = "0") interIdeaCompetitionControversialCount: Int,
  @(ApiModelProperty @field)(dataType = "int", example = "1000") maxTestedProposalCount: Int,
  @(ApiModelProperty @field)(dataType = "int", example = "12") sequenceSize: Int,
  @(ApiModelProperty @field)(dataType = "string", example = "Bandit")
  selectionAlgorithmName: SelectionAlgorithmName,
  @(ApiModelProperty @field)(dataType = "double", example = "0.5") nonSequenceVotesWeight: Double
) {

  def toSequenceConfiguration(sequenceId: SequenceId, questionId: QuestionId): SequenceConfiguration = {
    val specificSequenceConfiguration: SpecificSequenceConfiguration = SpecificSequenceConfiguration(
      sequenceSize = sequenceSize,
      newProposalsRatio = newProposalsRatio,
      maxTestedProposalCount = maxTestedProposalCount,
      selectionAlgorithmName = selectionAlgorithmName,
      intraIdeaEnabled = intraIdeaEnabled,
      intraIdeaMinCount = intraIdeaMinCount,
      intraIdeaProposalsRatio = intraIdeaProposalsRatio,
      interIdeaCompetitionEnabled = interIdeaCompetitionEnabled,
      interIdeaCompetitionTargetCount = interIdeaCompetitionTargetCount,
      interIdeaCompetitionControversialRatio = interIdeaCompetitionControversialRatio,
      interIdeaCompetitionControversialCount = interIdeaCompetitionControversialCount
    )
    SequenceConfiguration(
      sequenceId = sequenceId,
      questionId = questionId,
      mainSequence = specificSequenceConfiguration,
      controversial = specificSequenceConfiguration,
      popular = specificSequenceConfiguration,
      keyword = specificSequenceConfiguration,
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
