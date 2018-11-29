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

package org.make.api.proposal

import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.commons.math3.random.{MersenneTwister, RandomGenerator}
import org.make.api.sequence.SequenceConfiguration
import org.make.core.proposal.QualificationKey._
import org.make.core.proposal.VoteKey._
import org.make.core.proposal.indexed.SequencePool
import org.make.core.proposal.{BaseVote, ProposalStatus, QualificationKey, VoteKey}

object ProposalScorerHelper extends StrictLogging {
  var random: RandomGenerator = new MersenneTwister()

  case class ScoreCounts(votes: Int,
                         neutralCount: Int,
                         platitudeAgreeCount: Int,
                         platitudeDisagreeCount: Int,
                         loveCount: Int,
                         hateCount: Int,
                         doableCount: Int,
                         impossibleCount: Int)

  def voteCounts(votes: Seq[BaseVote], voteKey: VoteKey): Int = {
    votes.filter(_.key == voteKey).map(_.count).sum
  }

  def qualificationCounts(votes: Seq[BaseVote], voteKey: VoteKey, qualificationKey: QualificationKey): Int = {
    votes
      .filter(_.key == voteKey)
      .flatMap(_.qualifications.filter(_.key == qualificationKey).map(_.count))
      .sum
  }

  def scoreCounts(votes: Seq[BaseVote]): ScoreCounts = {
    val votesCount: Int = votes.map(_.count).sum
    val neutralCount: Int = voteCounts(votes, Neutral)
    val platitudeAgreeCount: Int = qualificationCounts(votes, Agree, PlatitudeAgree)
    val platitudeDisagreeCount: Int = qualificationCounts(votes, Disagree, PlatitudeDisagree)
    val loveCount: Int = qualificationCounts(votes, Agree, LikeIt)
    val hateCount: Int = qualificationCounts(votes, Disagree, NoWay)
    val doableCount: Int = qualificationCounts(votes, Agree, Doable)
    val impossibleCount: Int = qualificationCounts(votes, Agree, Impossible)

    ScoreCounts(
      votesCount,
      neutralCount,
      platitudeAgreeCount,
      platitudeDisagreeCount,
      loveCount,
      hateCount,
      doableCount,
      impossibleCount
    )
  }

  /*
   * platitude qualifications counts as neutral votes
   */
  def engagement(counts: ScoreCounts): Double = {
    1 -
      (counts.neutralCount + counts.platitudeAgreeCount + counts.platitudeDisagreeCount + 0.33) /
        (counts.votes + 1).toDouble
  }

  def engagement(votes: Seq[BaseVote]): Double = {
    engagement(scoreCounts(votes))
  }

  def adhesion(counts: ScoreCounts): Double = {
    ((counts.loveCount + 0.01) / (counts.votes - counts.neutralCount + 1).toDouble
      - (counts.hateCount + 0.01) / (counts.votes - counts.neutralCount + 1).toDouble)
  }

  def adhesion(votes: Seq[BaseVote]): Double = {
    adhesion(scoreCounts(votes))
  }

  def realistic(counts: ScoreCounts): Double = {
    ((counts.doableCount + 0.01) / (counts.votes - counts.neutralCount + 1).toDouble
      - (counts.impossibleCount + 0.01) / (counts.votes - counts.neutralCount + 1).toDouble)
  }

  def realistic(votes: Seq[BaseVote]): Double = {
    realistic(scoreCounts(votes))
  }

  def topScore(counts: ScoreCounts): Double = {
    engagement(counts) + adhesion(counts) + 2 * realistic(counts)
  }

  def topScore(votes: Seq[BaseVote]): Double = {
    topScore(scoreCounts(votes))
  }

  def controversy(counts: ScoreCounts): Double = {
    math.min(counts.loveCount + 0.01, counts.hateCount + 0.01) / (counts.votes - counts.neutralCount + 1).toDouble
  }

  def controversy(votes: Seq[BaseVote]): Double = {
    controversy(scoreCounts(votes))
  }

  def rejection(counts: ScoreCounts): Double = {
    -1 * adhesion(counts)
  }

  def rejection(votes: Seq[BaseVote]): Double = {
    rejection(scoreCounts(votes))
  }

  /*
   * Samples are taken from a beta distribution, which is the bayesian companion distribution for the binomial distribution
   * Each rate is sampled from its own beta distribution with a prior at 0.33 for votes and 0.01 for qualifications
   */
  def sampleRate(successes: Int, trials: Int, prior: Double): Double = {
    new BetaDistribution(random, successes + prior, trials - successes + 1).sample()
  }

  def sampleEngagement(counts: ScoreCounts): Double = {
    1 - sampleRate(counts.neutralCount + counts.platitudeAgreeCount + counts.platitudeDisagreeCount, counts.votes, 0.33)
  }

  def sampleEngagement(votes: Seq[BaseVote]): Double = {
    sampleEngagement(scoreCounts(votes))
  }

  def sampleAdhesion(counts: ScoreCounts): Double = {
    (sampleRate(counts.loveCount, counts.votes - counts.neutralCount, 0.01)
      - sampleRate(counts.hateCount, counts.votes - counts.neutralCount, 0.01))
  }

  def sampleAdhesion(votes: Seq[BaseVote]): Double = {
    sampleAdhesion(scoreCounts(votes))
  }

  def sampleRealistic(counts: ScoreCounts): Double = {
    (sampleRate(counts.doableCount, counts.votes - counts.neutralCount, 0.01)
      - sampleRate(counts.impossibleCount, counts.votes - counts.neutralCount, 0.01))
  }

  def sampleRealistic(votes: Seq[BaseVote]): Double = {
    sampleRealistic(scoreCounts(votes))
  }

  def sampleControversy(counts: ScoreCounts): Double = {
    sampleRate(math.min(counts.loveCount, counts.hateCount), counts.votes - counts.neutralCount, 0.01)
  }

  def sampleControversy(votes: Seq[BaseVote]): Double = {
    sampleControversy(scoreCounts(votes))
  }

  def sampleScore(counts: ScoreCounts): Double = {
    sampleEngagement(counts) + sampleAdhesion(counts) + 2 * sampleRealistic(counts)
  }

  def sampleScore(votes: Seq[BaseVote]): Double = {
    sampleScore(scoreCounts(votes))
  }

  /*
   * Returns the rate estimate and standard error
   * it uses Agresti-Coull estimate: "add 2 successes and 2 failures"
   * https://en.wikipedia.org/wiki/Binomial_proportion_confidence_interval#Agresti–Coull_interval
   * nn is the number of trials estimate
   * pp is the probability estimate
   * sd is the standard deviation estimate
   * */
  case class RateEstimate(rate: Double, sd: Double)

  def rateEstimate(successes: Int, trials: Int): RateEstimate = {
    val nn: Double = (trials + 4).toDouble
    val pp: Double = (successes + 2) / nn
    val sd: Double = Math.sqrt(pp * (1 - pp) / nn)

    RateEstimate(pp, sd)
  }

  def engagementUpperBound(votes: Seq[BaseVote]): Double = {
    val counts = scoreCounts(votes)

    val engagementEstimate: RateEstimate =
      rateEstimate(counts.neutralCount + counts.platitudeAgreeCount + counts.platitudeDisagreeCount, counts.votes)

    1 - engagementEstimate.rate + 2 * engagementEstimate.sd
  }

  def scoreUpperBound(votes: Seq[BaseVote]): Double = {
    val counts = scoreCounts(votes)

    val engagementEstimate: RateEstimate =
      rateEstimate(counts.neutralCount + counts.platitudeAgreeCount + counts.platitudeDisagreeCount, counts.votes)

    val adhesionEstimate: RateEstimate =
      rateEstimate(math.max(counts.loveCount, counts.hateCount), counts.votes - counts.neutralCount)

    val realisticEstimate: RateEstimate =
      rateEstimate(math.max(counts.doableCount, counts.impossibleCount), counts.votes - counts.neutralCount)

    val scoreEstimate: Double = topScore(counts)
    val confidenceInterval: Double = 2 * math.sqrt(
      math.pow(engagementEstimate.sd, 2) +
        math.pow(adhesionEstimate.sd, 2) +
        2 * math.pow(realisticEstimate.sd, 2)
    )

    scoreEstimate + confidenceInterval
  }

  def controversyUpperBound(votes: Seq[BaseVote]): Double = {
    val counts = scoreCounts(votes)

    val controversyEstimate: RateEstimate =
      rateEstimate(math.min(counts.loveCount, counts.hateCount), counts.votes - counts.neutralCount)

    controversyEstimate.rate + 2 * controversyEstimate.sd
  }

  def sequencePool(sequenceConfiguration: SequenceConfiguration,
                   votes: Seq[BaseVote],
                   status: ProposalStatus): SequencePool = {
    val votesCount: Int = votes.map(_.count).sum
    val engagementRate: Double = ProposalScorerHelper.engagementUpperBound(votes)
    val scoreRate: Double = ProposalScorerHelper.scoreUpperBound(votes)
    val controversyRate: Double = ProposalScorerHelper.controversyUpperBound(votes)

    if (status == ProposalStatus.Accepted && votesCount < sequenceConfiguration.newProposalsVoteThreshold) {
      SequencePool.New
    } else if (status == ProposalStatus.Accepted && votesCount >= sequenceConfiguration.newProposalsVoteThreshold
               && engagementRate > sequenceConfiguration.testedProposalsEngagementThreshold
               && (scoreRate > sequenceConfiguration.testedProposalsScoreThreshold
               || controversyRate > sequenceConfiguration.testedProposalsControversyThreshold)) {
      SequencePool.Tested
    } else {
      SequencePool.Excluded
    }
  }

}
