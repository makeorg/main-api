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

package org.make.api.sequence

import org.make.api.DatabaseTest
import org.make.core.question.QuestionId
import org.make.core.sequence.{
  ExplorationSequenceConfiguration,
  ExplorationSequenceConfigurationId,
  SelectionAlgorithmName,
  SequenceConfiguration,
  SequenceId,
  SpecificSequenceConfiguration,
  SpecificSequenceConfigurationId
}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import eu.timepit.refined.auto._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class DefaultPersistentSequenceConfigurationServiceComponentTest
    extends DatabaseTest
    with DefaultPersistentSequenceConfigurationServiceComponent {

  def createConfiguration(id: String): SequenceConfiguration = {
    val controversial = SpecificSequenceConfiguration(
      specificSequenceConfigurationId = SpecificSequenceConfigurationId(s"controversial-$id"),
      sequenceSize = 1,
      newProposalsRatio = 0.1,
      maxTestedProposalCount = 10,
      selectionAlgorithmName = SelectionAlgorithmName.Random,
      intraIdeaEnabled = false,
      interIdeaCompetitionEnabled = false
    )

    val popular = SpecificSequenceConfiguration(
      specificSequenceConfigurationId = SpecificSequenceConfigurationId(s"popular-$id"),
      sequenceSize = 2,
      newProposalsRatio = 0.2,
      maxTestedProposalCount = 20,
      selectionAlgorithmName = SelectionAlgorithmName.RoundRobin,
      intraIdeaEnabled = false,
      interIdeaCompetitionEnabled = false
    )

    val keyword = SpecificSequenceConfiguration(
      specificSequenceConfigurationId = SpecificSequenceConfigurationId(s"keyword-$id"),
      sequenceSize = 4,
      newProposalsRatio = 0.4,
      maxTestedProposalCount = 40,
      selectionAlgorithmName = SelectionAlgorithmName.RoundRobin,
      intraIdeaEnabled = false,
      interIdeaCompetitionEnabled = false
    )

    SequenceConfiguration(
      sequenceId = SequenceId(id),
      questionId = QuestionId(id),
      mainSequence = ExplorationSequenceConfiguration.default(ExplorationSequenceConfigurationId(id)),
      controversial = controversial,
      popular = popular,
      keyword = keyword,
      newProposalsVoteThreshold = 10,
      testedProposalsEngagementThreshold = Some(1.1d),
      testedProposalsScoreThreshold = Some(2.2d),
      testedProposalsControversyThreshold = Some(3.3d),
      testedProposalsMaxVotesThreshold = Some(4),
      nonSequenceVotesWeight = 0.2
    )
  }

  Feature("Sequence configuration CRUD") {
    Scenario("Insert and get") {
      val id = "Insert-and-get"
      val configuration = createConfiguration(id)

      val insertAndGet: Future[Option[SequenceConfiguration]] =
        persistentSequenceConfigurationService
          .persist(configuration)
          .flatMap(_ => persistentSequenceConfigurationService.findOne(configuration.questionId))

      whenReady(insertAndGet, Timeout(5.seconds)) {
        _ should contain(configuration)
      }
    }

    Scenario("delete configuration") {
      val id = "delete-configuration"
      val configuration = createConfiguration(id)

      val scenario = for {
        _       <- persistentSequenceConfigurationService.persist(configuration)
        initial <- persistentSequenceConfigurationService.findOne(configuration.questionId)
        _       <- persistentSequenceConfigurationService.delete(configuration.questionId)
        end     <- persistentSequenceConfigurationService.findOne(configuration.questionId)
      } yield (initial, end)

      whenReady(scenario, Timeout(5.seconds)) {
        case (initial, end) =>
          initial should contain(configuration)
          end should be(empty)
      }
    }

    Scenario("update configuration") {
      val id = "update-configuration"
      val configuration = createConfiguration(id)
      val updated = {
        val controversial = SpecificSequenceConfiguration(
          specificSequenceConfigurationId = SpecificSequenceConfigurationId(s"controversial-$id"),
          sequenceSize = 5,
          newProposalsRatio = 0.5,
          maxTestedProposalCount = 50,
          selectionAlgorithmName = SelectionAlgorithmName.Random,
          intraIdeaEnabled = false,
          interIdeaCompetitionEnabled = false
        )

        val popular = SpecificSequenceConfiguration(
          specificSequenceConfigurationId = SpecificSequenceConfigurationId(s"popular-$id"),
          sequenceSize = 6,
          newProposalsRatio = 0.6,
          maxTestedProposalCount = 60,
          selectionAlgorithmName = SelectionAlgorithmName.RoundRobin,
          intraIdeaEnabled = false,
          interIdeaCompetitionEnabled = false
        )

        val keyword = SpecificSequenceConfiguration(
          specificSequenceConfigurationId = SpecificSequenceConfigurationId(s"keyword-$id"),
          sequenceSize = 7,
          newProposalsRatio = 0.7,
          maxTestedProposalCount = 70,
          selectionAlgorithmName = SelectionAlgorithmName.RoundRobin,
          intraIdeaEnabled = false,
          interIdeaCompetitionEnabled = false
        )

        SequenceConfiguration(
          sequenceId = SequenceId(id),
          questionId = QuestionId(id),
          mainSequence = ExplorationSequenceConfiguration.default(ExplorationSequenceConfigurationId(id)),
          controversial = controversial,
          popular = popular,
          keyword = keyword,
          newProposalsVoteThreshold = 20,
          testedProposalsEngagementThreshold = Some(2.2d),
          testedProposalsScoreThreshold = Some(3.3d),
          testedProposalsControversyThreshold = Some(4.4d),
          testedProposalsMaxVotesThreshold = Some(5),
          nonSequenceVotesWeight = 0.6
        )
      }

      val scenario = for {
        _   <- persistentSequenceConfigurationService.persist(configuration)
        _   <- persistentSequenceConfigurationService.persist(updated)
        end <- persistentSequenceConfigurationService.findOne(configuration.questionId)
      } yield end

      whenReady(scenario, Timeout(5.seconds)) {
        _ should contain(updated)
      }
    }

    Scenario("list all") {
      val id = "list-all"

      val scenario = for {
        _   <- persistentSequenceConfigurationService.persist(createConfiguration(s"$id-1"))
        _   <- persistentSequenceConfigurationService.persist(createConfiguration(s"$id-2"))
        _   <- persistentSequenceConfigurationService.persist(createConfiguration(s"$id-3"))
        _   <- persistentSequenceConfigurationService.persist(createConfiguration(s"$id-4"))
        _   <- persistentSequenceConfigurationService.persist(createConfiguration(s"$id-5"))
        all <- persistentSequenceConfigurationService.findAll()
      } yield all

      val ids = (1 to 5).map(i => s"$id-$i")

      whenReady(scenario, Timeout(5.seconds)) { all =>
        all.map(_.questionId.value).intersect(ids) should be(ids)
      }
    }
  }

}
