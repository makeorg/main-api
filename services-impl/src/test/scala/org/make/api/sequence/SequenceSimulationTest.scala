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

import eu.timepit.refined.auto._
import org.make.api.proposal.ProposalScorer
import org.make.api.proposal.ProposalScorer.VotesCounter
import org.make.api.sequence.SelectionAlgorithm.ExplorationSelectionAlgorithm
import org.make.api.sequence.SequenceSimulationTest.{counter, extractDeciles}
import org.make.api.technical.MakeRandom
import org.make.api.MakeUnitTest
import org.make.core.proposal.QualificationKey.{
  DoNotCare,
  DoNotUnderstand,
  Doable,
  Impossible,
  LikeIt,
  NoOpinion,
  NoWay,
  PlatitudeAgree,
  PlatitudeDisagree
}
import org.make.core.proposal.VoteKey.{Agree, Disagree, Neutral}
import org.make.core.proposal.indexed.{IndexedProposal, IndexedScores}
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.sequence.{ExplorationSequenceConfiguration, ExplorationSequenceConfigurationId}
import org.make.api.ProposalsUtils

import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec

class SequenceSimulationTest extends MakeUnitTest {

  private val nonSequenceRatio: Double = 0.5
  private val maxVotes: Int = 2000
  private val votesPerProposalTarget = 150
  private val votesCounter: VotesCounter = VotesCounter.SequenceVotesCounter
  private val logSteps: Boolean = false

  val seed = 0
  MakeRandom.setSeed(seed)
  ProposalScorer.setSeed(seed)

  private val endCondition: Seq[IndexedProposal] => Boolean =
    proposals => proposals.map(_.votesSequenceCount).sum / proposals.size >= votesPerProposalTarget

  private val proposals = ProposalsUtils.getProposalsFromCsv("sequence_simulation.csv")

  // For each proposal compute a score that is the "real" score,
  // use it to choose how to vote on proposals
  private val targetScores: Map[ProposalId, ProposalScorer] =
    proposals
      .map(p => p.id -> ProposalScorer(p.votes, votesCounter, nonSequenceRatio))
      .toMap

  Feature("choosing proposals to vote on") {
    Scenario("Using the bandit") {
      counter.set(0)
      val configuration = ExplorationSequenceConfiguration.default(ExplorationSequenceConfigurationId("bandit"))
      val result = runSimulationRound(configuration, proposals, endCondition)

      logger.info(s"Simulation ended in ${counter.get()} steps")
      result.groupBy(_.scores.zone).foreach {
        case (zone, proposals) =>
          logger.info(s"Zone $zone has ${proposals.size} proposals")
      }

      val deciles = extractDeciles(result.sortBy(_.scores.topScore.score))
      val votesAverage = deciles.map { q =>
        if (q.isEmpty) {
          0
        } else {
          q.map(_.votesSequenceCount).sum / q.size
        }
      }

      val emergenceSize = 3
      val bestAverages = votesAverage.takeRight(emergenceSize)
      votesAverage.dropRight(emergenceSize).foreach { average =>
        bestAverages.foreach(_ should be > average)
      }
    }
  }

  @tailrec
  private def runSimulationRound(
    configuration: ExplorationSequenceConfiguration,
    proposals: Seq[IndexedProposal],
    hasEnded: Seq[IndexedProposal] => Boolean
  ): Seq[IndexedProposal] = {
    if (hasEnded(proposals)) {
      proposals
    } else {
      val iteration = counter.incrementAndGet()
      if (logSteps && iteration % 100 == 0) {
        val minVotes =
          proposals
            .sortBy(_.votesSequenceCount)
            .map { p =>
              p.votes.map(v => s"${v.key.value.head}: ${v.countSequence}").mkString(", ")
            }
            .take(10)
            .mkString("[", ", ", "]")
        val maxVotes =
          proposals.sortBy(-_.votesSequenceCount).map(_.votesSequenceCount).take(10).mkString("[", ", ", "]")
        logger.info(s"$iteration: worst proposals have $minVotes votes, best have $maxVotes votes.")
      }
      val (newProposals, testesProposals) =
        proposals.filter(_.votesSequenceCount < maxVotes).partition(_.votesSequenceCount <= 10)
      val chosenSequence = ExplorationSelectionAlgorithm.selectProposalsForSequence(
        configuration,
        nonSequenceRatio,
        Seq.empty,
        newProposals,
        testesProposals,
        None
      )
      chosenSequence.map(_.userId).toSet.size should be(chosenSequence.size)
      val sequence: Seq[IndexedProposal] = if (chosenSequence.size == configuration.sequenceSize.value) {
        chosenSequence
      } else {
        val neededCount = configuration.sequenceSize.value - chosenSequence.size
        val chosenIds = chosenSequence.map(_.id).toSet
        chosenSequence ++
          MakeRandom
            .shuffleSeq(proposals.filterNot(p => chosenIds.contains(p.id) || p.votesSequenceCount >= maxVotes))
            .take(neededCount)
      }
      val votedSequence = sequence.map(voteOnProposal)
      val selectedIds = votedSequence.map(_.id).toSet
      val updatedProposals = proposals.filterNot(p => selectedIds.contains(p.id)) ++ votedSequence
      runSimulationRound(configuration, updatedProposals, hasEnded)
    }
  }

  private def voteOnProposal(proposal: IndexedProposal): IndexedProposal = {
    val scorer = targetScores(proposal.id)
    val ratio = scorer.agree.sample + scorer.disagree.sample + scorer.neutral.sample
    val normalizedKeys = Seq(
      (Agree, scorer.agree.sample / ratio),
      (Disagree, scorer.disagree.sample / ratio),
      (Neutral, scorer.neutral.sample / ratio)
    )
    // The getOrElse is here in case there would be some rounding and the sum of probabilities
    // wouldn't be 1 but 0.99999999999999999999999999999999999999
    val key = choose(normalizedKeys, MakeRandom.nextDouble()).getOrElse(Neutral)

    val qualifications = chooseQualifications(scorer, key)
    val updatedVotes = proposal.votes.map { vote =>
      if (vote.key != key) {
        vote
      } else {
        vote.copy(
          count = vote.count + 1,
          countVerified = vote.countVerified + 1,
          countSequence = vote.countSequence + 1,
          qualifications = vote.qualifications.map { qualification =>
            if (qualifications.contains(qualification.key)) {
              qualification.copy(
                count = qualification.count + 1,
                countVerified = qualification.countVerified + 1,
                countSequence = qualification.countSequence + 1
              )
            } else {
              qualification
            }
          }
        )
      }
    }
    proposal.copy(
      votes = updatedVotes,
      votesCount = proposal.votesCount + 1,
      votesVerifiedCount = proposal.votesVerifiedCount + 1,
      votesSequenceCount = proposal.votesSequenceCount + 1,
      scores = IndexedScores(ProposalScorer(updatedVotes, votesCounter, nonSequenceRatio))
    )
  }

  private def chooseQualifications(scorer: ProposalScorer, key: VoteKey): Option[QualificationKey] = {
    val scoredQualifications = key match {
      case Agree =>
        Seq(
          LikeIt -> Math.max(scorer.likeIt.sample, 0),
          Doable -> Math.max(scorer.doable.sample, 0),
          PlatitudeAgree -> Math.max(scorer.platitudeAgree.sample, 0)
        )
      case Disagree =>
        Seq(
          NoWay -> Math.max(scorer.noWay.sample, 0),
          Impossible -> Math.max(scorer.impossible.sample, 0),
          PlatitudeDisagree -> Math.max(scorer.platitudeDisagree.sample, 0)
        )
      case Neutral =>
        Seq(
          NoOpinion -> Math.max(scorer.noOpinion.sample, 0),
          DoNotCare -> Math.max(scorer.doNotCare.sample, 0),
          DoNotUnderstand -> Math.max(scorer.doNotUnderstand.sample, 0)
        )
    }
    choose(scoredQualifications, MakeRandom.nextDouble())
  }

  def choose[T](values: Seq[(T, Double)], sample: Double): Option[T] = {
    values match {
      case Seq()                                  => None
      case (value, score) +: _ if score >= sample => Some(value)
      case (_, score) +: rest                     => choose(rest, sample - score)
    }
  }
}

object SequenceSimulationTest {
  val counter = new AtomicInteger()

  def extractDeciles[T](values: Seq[T]): Seq[Seq[T]] = {
    val decileSize = Math.round(values.size.toFloat / 10)
    (0 until 10).map { i =>
      if (i != 9) { // the last decile may have one more or one less, make sure to include the whole list
        values.slice(i * decileSize, i * decileSize + decileSize)
      } else {
        values.drop(i * decileSize)
      }
    }
  }
}
