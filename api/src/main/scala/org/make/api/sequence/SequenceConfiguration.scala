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

import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.api.proposal.SelectionAlgorithmName
import org.make.api.sequence.SequenceConfigurationActor._
import org.make.api.technical.TimeSettings
import org.make.core.question.QuestionId
import org.make.core.sequence.SequenceId

import scala.annotation.meta.field
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * SequenceConfiguration fields are used to configure the selection algorithms of one sequence `sequenceId`.
  * Here is a documentation on each and every fields of the SequenceConfiguration. For a more detailed and mathematical
  * explanation, check:
  *  - questionId: The related question.
  *  - newProposalsRatio: Ratio of new proposals to be found in the generated sequence. If this ratio cannot be acheived
  *     (e.g.: user already voted on most new proposals) then a rebalancing occurs to add more tested proposals in the sequence.
  *  - newProposalsVoteThreshold: Number of votes threshold for a proposal to reach to get out of the "new" proposal pool.
  *  - testedProposalsEngagementThreshold: _facultative_ Number of votes threshold for a tested proposal to be above in
  *     order to stay in the engagement competition (i.e.: emergence).
  *  - testedProposalsScoreThreshold: _facultative_ Emergence score threshold for a tested proposal to be above in
  *     order to stay in the engagement competition (i.e.: emergence).
  *  - testedProposalsControversyThreshold: _facultative_ Controversy score threshold for a tested proposal to be above in
  *     order to stay in the engagement competition (i.e.: emergence).
  *  - testedProposalsMaxVotesThreshold: _facultative_ Number of votes threshold for a tested proposal to be below in
  *     order to stay in the engagement competition (i.e.: emergence).
  *  - intraIdeaEnabled: Boolean to use the competition or not inside every ideas. If false: SoftMin on votes inside every ideas.
  *  - intraIdeaMinCount: The minimum number of proposals needed to apply the bandit algorithm.
  *  - intraIdeaProposalsRatio: Ratio of proposal chose by the bandit algorithm
  *  - interIdeaCompetitionEnabled: Boolean to use the competition or not between every ideas.
  *  - interIdeaCompetitionTargetCount: Number of proposal to chose from the inter ideas score competition.
  *  - interIdeaCompetitionControversialRatio: Ratio of controversy proposal to chose from the inter ideas competition.
  *  - interIdeaCompetitionControversialCount: Number of proposal to chose from the inter ideas controversy score competition.
  *  - maxTestedProposalCount: Max size of the tested pool to fetch from ES.
  *  - sequenceSize: Max size of the sequence.
  *  - selectionAlgorithmName: Name of the selection algorithm to use. At the moment, this can be "Bandit" or "RoundRobin"
  *
  * Additional informations:
  *  - The proposal "pool" is defined at indexation time in `ProposalScorer.sequencePool`.
  *  - A new proposal is a proposal in the "new" pool.
  *  - A tested proposal is a proposal in the "tested" pool.
  *  - A excluded proposal is a proposal in the "excluded" pool.
  *  - If both `testedProposalsScoreThreshold` and `testedProposalsControversyThreshold` are defined, a proposal will be
  *     in the "tested" proposal pool if only one of the two thresholds is surpassed. I.e. the logical condition is an OR.
  *
  **/

@ApiModel
final case class SequenceConfiguration(
  @(ApiModelProperty @field)(dataType = "string", example = "fd735649-e63d-4464-9d93-10da54510a12")
  sequenceId: SequenceId,
  @(ApiModelProperty @field)(dataType = "string", example = "d2b2694a-25cf-4eaa-9181-026575d58cf8")
  questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "double", example = "0.5")
  newProposalsRatio: Double = 0.3,
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
  @(ApiModelProperty @field)(dataType = "boolean", example = "false")
  intraIdeaEnabled: Boolean = true,
  @(ApiModelProperty @field)(dataType = "int", example = "1")
  intraIdeaMinCount: Int = 1,
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  intraIdeaProposalsRatio: Double = 0.0,
  @(ApiModelProperty @field)(dataType = "boolean", example = "false")
  interIdeaCompetitionEnabled: Boolean = true,
  @(ApiModelProperty @field)(dataType = "int", example = "50")
  interIdeaCompetitionTargetCount: Int = 20,
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  interIdeaCompetitionControversialRatio: Double = 0.0,
  @(ApiModelProperty @field)(dataType = "int", example = "0")
  interIdeaCompetitionControversialCount: Int = 2,
  @(ApiModelProperty @field)(dataType = "int", example = "1000")
  maxTestedProposalCount: Int = 1000,
  @(ApiModelProperty @field)(dataType = "int", example = "12")
  sequenceSize: Int = 12,
  @(ApiModelProperty @field)(dataType = "string", example = "Bandit")
  selectionAlgorithmName: SelectionAlgorithmName = SelectionAlgorithmName.Bandit,
  @(ApiModelProperty @field)(dataType = "double", example = "0.5")
  nonSequenceVotesWeight: Double = 0.5,
  @(ApiModelProperty @field)(dataType = "int", example = "100")
  scoreAdjustementVotesThreshold: Int = 100,
  @(ApiModelProperty @field)(dataType = "double", example = "1000")
  scoreAdjustementFactor: Double = 1000
)

object SequenceConfiguration {
  implicit val decoder: Decoder[SequenceConfiguration] = deriveDecoder[SequenceConfiguration]
  implicit val encoder: Encoder[SequenceConfiguration] = deriveEncoder[SequenceConfiguration]

  val default: SequenceConfiguration = SequenceConfiguration(
    sequenceId = SequenceId("default-sequence"),
    questionId = QuestionId("default-question"),
    newProposalsRatio = 0.5,
    newProposalsVoteThreshold = 100,
    testedProposalsEngagementThreshold = Some(0.8),
    testedProposalsScoreThreshold = None,
    testedProposalsControversyThreshold = None,
    testedProposalsMaxVotesThreshold = Some(1500),
    intraIdeaEnabled = true,
    intraIdeaMinCount = 1,
    intraIdeaProposalsRatio = 0.0,
    interIdeaCompetitionEnabled = false,
    interIdeaCompetitionTargetCount = 50,
    interIdeaCompetitionControversialRatio = 0.0,
    interIdeaCompetitionControversialCount = 0,
    maxTestedProposalCount = 1000,
    sequenceSize = 12,
    selectionAlgorithmName = SelectionAlgorithmName.Bandit,
    nonSequenceVotesWeight = 0.5,
    scoreAdjustementVotesThreshold = 100,
    scoreAdjustementFactor = 1000
  )

}

trait SequenceConfigurationService {
  def getSequenceConfiguration(sequenceId: SequenceId): Future[SequenceConfiguration]
  def getSequenceConfigurationByQuestionId(questionId: QuestionId): Future[SequenceConfiguration]
  def setSequenceConfiguration(sequenceConfiguration: SequenceConfiguration): Future[Boolean]
  def getPersistentSequenceConfiguration(sequenceId: SequenceId): Future[Option[SequenceConfiguration]]
  def getPersistentSequenceConfigurationByQuestionId(questionId: QuestionId): Future[Option[SequenceConfiguration]]
  def reloadConfigurations(): Unit
}

trait SequenceConfigurationComponent {
  val sequenceConfigurationService: SequenceConfigurationService
}

trait DefaultSequenceConfigurationComponent extends SequenceConfigurationComponent with StrictLogging {
  self: SequenceConfigurationActorComponent =>

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override lazy val sequenceConfigurationService: SequenceConfigurationService = new SequenceConfigurationService {
    override def getSequenceConfiguration(sequenceId: SequenceId): Future[SequenceConfiguration] = {
      (sequenceConfigurationActor ? GetSequenceConfiguration(sequenceId))
        .mapTo[CachedSequenceConfiguration]
        .map(_.sequenceConfiguration)
    }

    override def getSequenceConfigurationByQuestionId(questionId: QuestionId): Future[SequenceConfiguration] = {
      (sequenceConfigurationActor ? GetSequenceConfigurationByQuestionId(questionId))
        .mapTo[CachedSequenceConfiguration]
        .map(_.sequenceConfiguration)
    }

    override def setSequenceConfiguration(sequenceConfiguration: SequenceConfiguration): Future[Boolean] = {
      (sequenceConfigurationActor ? SetSequenceConfiguration(sequenceConfiguration)).mapTo[Boolean]
    }

    override def getPersistentSequenceConfiguration(sequenceId: SequenceId): Future[Option[SequenceConfiguration]] = {
      (sequenceConfigurationActor ? GetPersistentSequenceConfiguration(sequenceId))
        .mapTo[StoredSequenceConfiguration]
        .map(_.sequenceConfiguration)
    }

    override def getPersistentSequenceConfigurationByQuestionId(
      questionId: QuestionId
    ): Future[Option[SequenceConfiguration]] = {
      (sequenceConfigurationActor ? GetPersistentSequenceConfigurationByQuestionId(questionId))
        .mapTo[StoredSequenceConfiguration]
        .map(_.sequenceConfiguration)
    }

    override def reloadConfigurations(): Unit = {
      sequenceConfigurationActor ! ReloadSequenceConfiguration
    }
  }
}
