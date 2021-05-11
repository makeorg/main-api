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

import akka.actor.typed.Scheduler
import akka.util.Timeout
import grizzled.slf4j.Logging
import org.make.api.ActorSystemTypedComponent
import org.make.api.sequence.SequenceConfigurationActor._
import org.make.api.technical.BetterLoggingActors._
import org.make.api.technical.TimeSettings
import org.make.core.question.QuestionId
import org.make.core.sequence.{SequenceConfiguration, SequenceId}
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
trait SequenceConfigurationService {
  def getSequenceConfigurationByQuestionId(questionId: QuestionId): Future[SequenceConfiguration]
  def setSequenceConfiguration(sequenceConfiguration: SequenceConfiguration): Future[Boolean]
  def getPersistentSequenceConfiguration(sequenceId: SequenceId): Future[Option[SequenceConfiguration]]
  def getPersistentSequenceConfigurationByQuestionId(questionId: QuestionId): Future[Option[SequenceConfiguration]]
  def reloadConfigurations(): Unit
}

trait SequenceConfigurationComponent {
  val sequenceConfigurationService: SequenceConfigurationService
}

trait DefaultSequenceConfigurationComponent extends SequenceConfigurationComponent with Logging {
  self: SequenceConfigurationActorComponent
    with PersistentSequenceConfigurationComponent
    with ActorSystemTypedComponent =>

  override lazy val sequenceConfigurationService: SequenceConfigurationService = new DefaultSequenceConfigurationService

  class DefaultSequenceConfigurationService extends SequenceConfigurationService {
    implicit val timeout: Timeout = TimeSettings.defaultTimeout
    implicit val scheduler: Scheduler = actorSystemTyped.scheduler

    override def getSequenceConfigurationByQuestionId(questionId: QuestionId): Future[SequenceConfiguration] = {
      (sequenceConfigurationActor ?? (GetSequenceConfiguration(questionId, _)))
        .map(_.sequenceConfiguration)
    }

    override def setSequenceConfiguration(sequenceConfiguration: SequenceConfiguration): Future[Boolean] = {
      persistentSequenceConfigurationService.persist(sequenceConfiguration)
    }

    override def getPersistentSequenceConfiguration(sequenceId: SequenceId): Future[Option[SequenceConfiguration]] = {
      persistentSequenceConfigurationService.findOne(sequenceId)
    }

    override def getPersistentSequenceConfigurationByQuestionId(
      questionId: QuestionId
    ): Future[Option[SequenceConfiguration]] = {
      persistentSequenceConfigurationService.findOne(questionId)
    }

    override def reloadConfigurations(): Unit = {
      sequenceConfigurationActor ! ReloadSequenceConfiguration
    }
  }
}
