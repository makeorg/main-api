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
import org.make.core.proposal.QualificationKey._
import org.make.core.proposal.VoteKey._
import org.make.core.proposal._

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

  def voteCounts(proposal: Proposal, voteKey: VoteKey): Int = {
    proposal.votes.filter(_.key == voteKey).map(_.count).sum
  }

  def qualificationCounts(proposal: Proposal, voteKey: VoteKey, qualificationKey: QualificationKey): Int = {
    proposal.votes
      .filter(_.key == voteKey)
      .map(_.qualifications.filter(_.key == qualificationKey).map(_.count).sum)
      .sum
  }

  def scoreCounts(proposal: Proposal): ScoreCounts = {
    val votes: Int = proposal.votes.map(_.count).sum
    val neutralCount: Int = voteCounts(proposal, Neutral)
    val platitudeAgreeCount: Int = qualificationCounts(proposal, Agree, PlatitudeAgree)
    val platitudeDisagreeCount: Int = qualificationCounts(proposal, Disagree, PlatitudeDisagree)
    val loveCount: Int = qualificationCounts(proposal, Agree, LikeIt)
    val hateCount: Int = qualificationCounts(proposal, Disagree, NoWay)
    val doableCount: Int = qualificationCounts(proposal, Agree, Doable)
    val impossibleCount: Int = qualificationCounts(proposal, Agree, Impossible)

    ScoreCounts(
      votes,
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

  def engagement(proposal: Proposal): Double = {
    engagement(scoreCounts(proposal))
  }

  def adhesion(counts: ScoreCounts): Double = {
    ((counts.loveCount + 0.01) / (counts.votes - counts.neutralCount + 1).toDouble
      - (counts.hateCount + 0.01) / (counts.votes - counts.neutralCount + 1).toDouble)
  }

  def adhesion(proposal: Proposal): Double = {
    adhesion(scoreCounts(proposal))
  }

  def realistic(counts: ScoreCounts): Double = {
    ((counts.doableCount + 0.01) / (counts.votes - counts.neutralCount + 1).toDouble
      - (counts.impossibleCount + 0.01) / (counts.votes - counts.neutralCount + 1).toDouble)
  }

  def realistic(proposal: Proposal): Double = {
    realistic(scoreCounts(proposal))
  }

  def score(counts: ScoreCounts): Double = {
    engagement(counts) + adhesion(counts) + 2 * realistic(counts)
  }

  def score(proposal: Proposal): Double = {
    score(scoreCounts(proposal))
  }

  def controversy(counts: ScoreCounts): Double = {
    math.min(counts.loveCount + 0.01, counts.hateCount + 0.01) / (counts.votes - counts.neutralCount + 1).toDouble
  }

  def controversy(proposal: Proposal): Double = {
    controversy(scoreCounts(proposal))
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

  def sampleEngagement(proposal: Proposal): Double = {
    sampleEngagement(scoreCounts(proposal))
  }

  def sampleAdhesion(counts: ScoreCounts): Double = {
    (sampleRate(counts.loveCount, counts.votes - counts.neutralCount, 0.01)
      - sampleRate(counts.hateCount, counts.votes - counts.neutralCount, 0.01))
  }

  def sampleAdhesion(proposal: Proposal): Double = {
    sampleAdhesion(scoreCounts(proposal))
  }

  def sampleRealistic(counts: ScoreCounts): Double = {
    (sampleRate(counts.doableCount, counts.votes - counts.neutralCount, 0.01)
      - sampleRate(counts.impossibleCount, counts.votes - counts.neutralCount, 0.01))
  }

  def sampleRealistic(proposal: Proposal): Double = {
    sampleRealistic(scoreCounts(proposal))
  }

  def sampleControversy(counts: ScoreCounts): Double = {
    sampleRate(math.min(counts.loveCount, counts.hateCount), counts.votes - counts.neutralCount, 0.01)
  }

  def sampleControversy(proposal: Proposal): Double = {
    sampleControversy(scoreCounts(proposal))
  }

  def sampleScore(counts: ScoreCounts): Double = {
    sampleEngagement(counts) + sampleAdhesion(counts) + 2 * sampleRealistic(counts)
  }

  def sampleScore(proposal: Proposal): Double = {
    sampleScore(scoreCounts(proposal))
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

  def engagementUpperBound(proposal: Proposal): Double = {
    val counts = scoreCounts(proposal)

    val engagementEstimate: RateEstimate =
      rateEstimate(counts.neutralCount + counts.platitudeAgreeCount + counts.platitudeDisagreeCount, counts.votes)

    1 - engagementEstimate.rate + 2 * engagementEstimate.sd
  }

  def scoreUpperBound(proposal: Proposal): Double = {
    val counts = scoreCounts(proposal)

    val engagementEstimate: RateEstimate =
      rateEstimate(counts.neutralCount + counts.platitudeAgreeCount + counts.platitudeDisagreeCount, counts.votes)

    val adhesionEstimate: RateEstimate =
      rateEstimate(math.max(counts.loveCount, counts.hateCount), counts.votes - counts.neutralCount)

    val realisticEstimate: RateEstimate =
      rateEstimate(math.max(counts.doableCount, counts.impossibleCount), counts.votes - counts.neutralCount)

    val scoreEstimate: Double = score(counts)
    val confidenceInterval: Double = 2 * math.sqrt(
      math.pow(engagementEstimate.sd, 2) +
        math.pow(adhesionEstimate.sd, 2) +
        2 * math.pow(realisticEstimate.sd, 2)
    )

    scoreEstimate + confidenceInterval
  }

  def controversyUpperBound(proposal: Proposal): Double = {
    val counts = scoreCounts(proposal)

    val controversyEstimate: RateEstimate =
      rateEstimate(math.min(counts.loveCount, counts.hateCount), counts.votes - counts.neutralCount)

    controversyEstimate.rate + 2 * controversyEstimate.sd
  }
}