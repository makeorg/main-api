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

import com.github.tototoshi.csv.CSVReader
import eu.timepit.refined.auto._
import org.make.api.proposal.ProposalScorer
import org.make.api.proposal.ProposalScorer.VotesCounter
import org.make.api.sequence.SelectionAlgorithm.ExplorationSelectionAlgorithm
import org.make.api.sequence.SequenceSimulationTest.{counter, extractDeciles}
import org.make.api.technical.MakeRandom
import org.make.api.technical.elasticsearch.ProposalIndexationStream.buildScore
import org.make.api.{MakeUnitTest, TestUtils}
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
import org.make.core.proposal.indexed.{IndexedProposal, IndexedProposalKeyword, IndexedQualification, IndexedVote}
import org.make.core.proposal.{ProposalId, ProposalKeywordKey, QualificationKey, VoteKey}
import org.make.core.sequence.{ExplorationSequenceConfiguration, ExplorationSequenceConfigurationId}
import org.make.core.user.UserId

import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import scala.io.Source

class SequenceSimulationTest extends MakeUnitTest {

  val nonSequenceRatio: Double = 0.5
  val maxVotes: Int = 2000
  val votesPerProposalTarget = 150
  private val votesCounter: VotesCounter = VotesCounter.SequenceVotesCounter
  val logSteps: Boolean = false

  val seed = 0
  MakeRandom.setSeed(seed)
  ProposalScorer.setSeed(seed)

  val endCondition: Seq[IndexedProposal] => Boolean =
    proposals => proposals.map(_.votesSequenceCount).sum / proposals.size >= votesPerProposalTarget

  // load the data
  val proposals: Seq[IndexedProposal] =
    CSVReader
      .open(Source.fromResource("sequence_simulation.csv"))
      .iteratorWithHeaders
      .map(SequenceSimulationTest.parseProposal)
      .toSeq

  // For each proposal compute a score that is the "real" score,
  // use it to choose how to vote on proposals
  val targetScores: Map[ProposalId, ProposalScorer] = proposals.map { p =>
    p.id -> ProposalScorer(p.votes, votesCounter, nonSequenceRatio)
  }.toMap

  Feature("choosing proposals to vote on") {
    Scenario("Using the bandit") {
      counter.set(0)
      val configuration = ExplorationSequenceConfiguration.default(ExplorationSequenceConfigurationId("bandit"))
      val result = runSimulationRound(configuration, proposals, endCondition)

      logger.info(s"Simulation ended in ${counter.get()} steps")
      result.groupBy(_.scores.zone).toSeq.foreach {
        case (zone, proposals) =>
          logger.info(s"Zone $zone has ${proposals.size} proposals")
      }

      val deciles = extractDeciles(result.sortBy(_.scores.topScore))
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
    val ratio = scorer.agree.cachedSample + scorer.disagree.cachedSample + scorer.neutral.cachedSample
    val normalizedKeys = Seq(
      (Agree, scorer.agree.cachedSample / ratio),
      (Disagree, scorer.disagree.cachedSample / ratio),
      (Neutral, scorer.neutral.cachedSample / ratio)
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
      scores = buildScore(ProposalScorer(updatedVotes, votesCounter, nonSequenceRatio))
    )
  }

  private def chooseQualifications(scorer: ProposalScorer, key: VoteKey): Option[QualificationKey] = {
    val scoredQualifications = key match {
      case Agree =>
        Seq(
          LikeIt -> Math.max(scorer.likeIt.cachedSample, 0),
          Doable -> Math.max(scorer.doable.cachedSample, 0),
          PlatitudeAgree -> Math.max(scorer.platitudeAgree.cachedSample, 0)
        )
      case Disagree =>
        Seq(
          NoWay -> Math.max(scorer.noWay.cachedSample, 0),
          Impossible -> Math.max(scorer.impossible.cachedSample, 0),
          PlatitudeDisagree -> Math.max(scorer.platitudeDisagree.cachedSample, 0)
        )
      case Neutral =>
        Seq(
          NoOpinion -> Math.max(scorer.noOpinion.cachedSample, 0),
          DoNotCare -> Math.max(scorer.doNotCare.cachedSample, 0),
          DoNotUnderstand -> Math.max(scorer.doNotUnderstand.cachedSample, 0)
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

  def parseProposal(values: Map[String, String]): IndexedProposal = {
    TestUtils.indexedProposal(
      id = ProposalId(values("id")),
      userId = UserId(values("id")),
      votes = parseVotes(values),
      createdAt = ZonedDateTime.parse(values("date_created")),
      keywords = values("keywords")
        .split('|')
        .map(k => IndexedProposalKeyword(ProposalKeywordKey(k), k))
        .toSeq
    )
  }

  private def parseVotes(values: Map[String, String]): Seq[IndexedVote] = {
    val agree = parseVote(
      values = values,
      key = Agree,
      qualifications = Seq(LikeIt -> "like", Doable -> "doable", PlatitudeAgree -> "platitudeagree")
    )
    val disagree = parseVote(
      values = values,
      key = Disagree,
      qualifications = Seq(NoWay -> "noway", Impossible -> "impossible", PlatitudeDisagree -> "platitudedisagree")
    )
    val neutral = parseVote(
      values = values,
      key = Neutral,
      qualifications = Seq(DoNotUnderstand -> "donotunderstand", NoOpinion -> "noopinion", DoNotCare -> "donotcare")
    )
    Seq(agree, disagree, neutral)
  }

  private def parseVote(
    values: Map[String, String],
    key: VoteKey,
    qualifications: Seq[(QualificationKey, String)]
  ): IndexedVote = {
    IndexedVote(
      key = key,
      count = values(s"vote_verified_${key.value}").toInt,
      countVerified = values(s"vote_verified_${key.value}").toInt,
      countSequence = values(s"vote_sequence_${key.value}").toInt,
      countSegment = 0,
      qualifications = qualifications.map {
        case (k, label) =>
          IndexedQualification(
            key = k,
            count = values(s"vote_verified_${key.value}_$label").toInt,
            countVerified = values(s"vote_verified_${key.value}_$label").toInt,
            countSequence = values(s"vote_sequence_${key.value}_$label").toInt,
            countSegment = 0
          )
      }
    )
  }

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
