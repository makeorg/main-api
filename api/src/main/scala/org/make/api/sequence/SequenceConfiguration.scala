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
import org.make.api.sequence.SequenceConfigurationActor._
import org.make.api.technical.TimeSettings
import org.make.core.question.QuestionId
import org.make.core.sequence.SequenceId

import scala.annotation.meta.field
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ApiModel
case class SequenceConfiguration(
  @(ApiModelProperty @field)(dataType = "string", example = "fd735649-e63d-4464-9d93-10da54510a12")
  sequenceId: SequenceId,
  @(ApiModelProperty @field)(dataType = "string", example = "d2b2694a-25cf-4eaa-9181-026575d58cf8")
  questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "int", example = "1000")
  maxAvailableProposals: Int = 1000,
  @(ApiModelProperty @field)(dataType = "double", example = "0.5")
  newProposalsRatio: Double = 0.5,
  @(ApiModelProperty @field)(dataType = "int", example = "100")
  newProposalsVoteThreshold: Int = 100,
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  testedProposalsEngagementThreshold: Double = 0.0,
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  testedProposalsScoreThreshold: Double = 0.0,
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  testedProposalsControversyThreshold: Double = 0.0,
  @(ApiModelProperty @field)(dataType = "int", example = "1500")
  testedProposalsMaxVotesThreshold: Int = 1500,
  @(ApiModelProperty @field)(dataType = "boolean", example = "false")
  banditEnabled: Boolean = false,
  @(ApiModelProperty @field)(dataType = "int", example = "1")
  banditMinCount: Int = 1,
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  banditProposalsRatio: Double = 0.0,
  @(ApiModelProperty @field)(dataType = "boolean", example = "false")
  ideaCompetitionEnabled: Boolean = false,
  @(ApiModelProperty @field)(dataType = "int", example = "50")
  ideaCompetitionTargetCount: Int = 50,
  @(ApiModelProperty @field)(dataType = "double", example = "0.0")
  ideaCompetitionControversialRatio: Double = 0.0,
  @(ApiModelProperty @field)(dataType = "int", example = "0")
  ideaCompetitionControversialCount: Int = 0,
  @(ApiModelProperty @field)(dataType = "int", example = "1000")
  maxTestedProposalCount: Int = 1000,
  @(ApiModelProperty @field)(dataType = "int", example = "12")
  sequenceSize: Int = 12,
  @(ApiModelProperty @field)(dataType = "int", example = "1500")
  maxVotes: Int = 1500
)

object SequenceConfiguration {
  implicit val decoder: Decoder[SequenceConfiguration] = deriveDecoder[SequenceConfiguration]
  implicit val encoder: Encoder[SequenceConfiguration] = deriveEncoder[SequenceConfiguration]

  val default: SequenceConfiguration = SequenceConfiguration(
    sequenceId = SequenceId("default-sequence"),
    questionId = QuestionId("default-question"),
    maxAvailableProposals = 1000,
    newProposalsRatio = 0.5,
    newProposalsVoteThreshold = 100,
    testedProposalsEngagementThreshold = 0.8,
    testedProposalsScoreThreshold = 0.0,
    testedProposalsControversyThreshold = 0.0,
    testedProposalsMaxVotesThreshold = 1500,
    banditEnabled = true,
    banditMinCount = 1,
    banditProposalsRatio = 0.0,
    ideaCompetitionEnabled = false,
    ideaCompetitionTargetCount = 50,
    ideaCompetitionControversialRatio = 0.0,
    ideaCompetitionControversialCount = 0,
    maxTestedProposalCount = 1000,
    sequenceSize = 12,
    maxVotes = 1500
  )

}

trait SequenceConfigurationService {
  def getSequenceConfiguration(sequenceId: SequenceId): Future[SequenceConfiguration]
  def getSequenceConfigurationByQuestionId(questionId: QuestionId): Future[SequenceConfiguration]
  def setSequenceConfiguration(sequenceConfiguration: SequenceConfiguration): Future[Boolean]
  def getPersistentSequenceConfiguration(sequenceId: SequenceId): Future[Option[SequenceConfiguration]]
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
    override def reloadConfigurations(): Unit = {
      sequenceConfigurationActor ! ReloadSequenceConfiguration
    }
  }
}
