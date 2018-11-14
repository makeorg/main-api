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

  case class ScoreComponent(name: String, weight: Double, mean: Double, std: Double)

  val topScoreComponents = Seq(
    ScoreComponent("engagement", 1, 0.8, 0.1),
    ScoreComponent("agreement", 1, 0.75, 0.1),
    ScoreComponent("adhesion", 1, 0.075, 0.07),
    ScoreComponent("realistic", 2, 0.1, 0.07),
    ScoreComponent("platitude", -2, 0.05, 0.05)
  )

  val combinedStd = math.sqrt(topScoreComponents.map(sc => math.pow(sc.weight, 2)).sum)

  case class ScoreCounts(votes: Int,
                         agreeCount: Int,
                         disagreeCount: Int,
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
    val agreeCount: Int = voteCounts(votes, Agree)
    val disagreeCount: Int = voteCounts(votes, Disagree)
    val neutralCount: Int = voteCounts(votes, Neutral)
    val platitudeAgreeCount: Int = qualificationCounts(votes, Agree, PlatitudeAgree)
    val platitudeDisagreeCount: Int = qualificationCounts(votes, Disagree, PlatitudeDisagree)
    val loveCount: Int = qualificationCounts(votes, Agree, LikeIt)
    val hateCount: Int = qualificationCounts(votes, Disagree, NoWay)
    val doableCount: Int = qualificationCounts(votes, Agree, Doable)
    val impossibleCount: Int = qualificationCounts(votes, Agree, Impossible)

    ScoreCounts(
      votesCount,
      agreeCount,
      disagreeCount,
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
   * Note on bayesian estimates: the estimator requires a prior,
   * i.e. an initial probability to start the estimate from.
   *
   * We choose 1/3 for the vote because there are 3 possible choices.
   * The prior for the qualification is 0.01 because we want a value close to 0 but not null
   * The prior is used with a basis of 1 vote
   */
  val VotePrior = 0.33
  val QualificationPrior = 0.01
  val CountPrior = 1

  def engagement(counts: ScoreCounts): Double = {
    (counts.agreeCount + counts.disagreeCount + 2 * VotePrior) / (counts.votes + CountPrior).toDouble
  }

  def engagement(votes: Seq[BaseVote]): Double = {
    engagement(scoreCounts(votes))
  }

  def agreement(counts: ScoreCounts): Double = {
    (counts.agreeCount + VotePrior) / (counts.votes + CountPrior).toDouble
  }

  def agreement(votes: Seq[BaseVote]): Double = {
    agreement(scoreCounts(votes))
  }

  def adhesion(counts: ScoreCounts): Double = {
    (counts.loveCount - counts.hateCount) / (counts.agreeCount + counts.disagreeCount + CountPrior).toDouble
  }

  def adhesion(votes: Seq[BaseVote]): Double = {
    adhesion(scoreCounts(votes))
  }

  def realistic(counts: ScoreCounts): Double = {
    (counts.doableCount - counts.impossibleCount) / (counts.agreeCount + counts.disagreeCount + CountPrior).toDouble
  }

  def realistic(votes: Seq[BaseVote]): Double = {
    realistic(scoreCounts(votes))
  }

  def platitude(counts: ScoreCounts): Double = {
    (counts.platitudeAgreeCount + counts.platitudeDisagreeCount) / (counts.agreeCount + counts.disagreeCount + CountPrior).toDouble
  }

  def platitude(votes: Seq[BaseVote]): Double = {
    platitude(scoreCounts(votes))
  }

  /**
    * The score components are first standardized == centered to their mean and rescaled by their standard error
    * this scale the score back to a distribution of mean=0 and var=std=1
    *
    * Then the score components are summed with their respective weigths
    *
    * Finally the score itself is normalized by the theoretical standard error of a weigthed sum of random variable of variance 1
    *
    * This should ensure that the top score itself is standardized
    *
    * */
  val topScoreFunctions: Map[String, ScoreCounts => Double] = Map(
    "engagement" -> engagement,
    "agreement" -> agreement,
    "adhesion" -> adhesion,
    "realistic" -> realistic,
    "platitude" -> platitude
  )

  def topScore(counts: ScoreCounts): Double = {
    val sumScores =
      topScoreComponents.map(sc => sc.weight * (topScoreFunctions(sc.name)(counts) - sc.mean) / sc.std).sum
    sumScores / combinedStd
  }

  def topScore(votes: Seq[BaseVote]): Double = {
    topScore(scoreCounts(votes))
  }

  def controversy(counts: ScoreCounts): Double = {
    math.min(counts.loveCount + QualificationPrior, counts.hateCount + QualificationPrior) / (counts.votes - counts.neutralCount + CountPrior).toDouble
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
    new BetaDistribution(random, successes + prior, trials - successes + CountPrior).sample()
  }

  def sampleEngagement(counts: ScoreCounts): Double = {
    sampleRate(counts.agreeCount + counts.disagreeCount, counts.votes, 2 * VotePrior)
  }

  def sampleEngagement(votes: Seq[BaseVote]): Double = {
    sampleEngagement(scoreCounts(votes))
  }

  def sampleAgreement(counts: ScoreCounts): Double = {
    sampleRate(counts.agreeCount, counts.votes, VotePrior)
  }

  def sampleAgreement(votes: Seq[BaseVote]): Double = {
    sampleAgreement(scoreCounts(votes))
  }

  def sampleAdhesion(counts: ScoreCounts): Double = {
    (sampleRate(counts.loveCount, counts.votes - counts.neutralCount, QualificationPrior)
      - sampleRate(counts.hateCount, counts.votes - counts.neutralCount, QualificationPrior))
  }

  def sampleAdhesion(votes: Seq[BaseVote]): Double = {
    sampleAdhesion(scoreCounts(votes))
  }

  def sampleRealistic(counts: ScoreCounts): Double = {
    (sampleRate(counts.doableCount, counts.votes - counts.neutralCount, QualificationPrior)
      - sampleRate(counts.impossibleCount, counts.votes - counts.neutralCount, QualificationPrior))
  }

  def sampleRealistic(votes: Seq[BaseVote]): Double = {
    sampleRealistic(scoreCounts(votes))
  }

  def samplePlatitude(counts: ScoreCounts): Double = {
    (sampleRate(counts.platitudeAgreeCount, counts.votes - counts.neutralCount, QualificationPrior)
      + sampleRate(counts.platitudeDisagreeCount, counts.votes - counts.neutralCount, QualificationPrior))
  }

  def samplePlatitude(votes: Seq[BaseVote]): Double = {
    samplePlatitude(scoreCounts(votes))
  }

  def sampleControversy(counts: ScoreCounts): Double = {
    sampleRate(math.min(counts.loveCount, counts.hateCount), counts.votes - counts.neutralCount, QualificationPrior)
  }

  def sampleControversy(votes: Seq[BaseVote]): Double = {
    sampleControversy(scoreCounts(votes))
  }

  val topScoreSampleFunctions: Map[String, ScoreCounts => Double] = Map(
    "engagement" -> sampleEngagement,
    "agreement" -> sampleAgreement,
    "adhesion" -> sampleAdhesion,
    "realistic" -> sampleRealistic,
    "platitude" -> samplePlatitude
  )

  def sampleTopScore(counts: ScoreCounts): Double = {
    val sumScores =
      topScoreComponents.map(sc => sc.weight * (topScoreSampleFunctions(sc.name)(counts) - sc.mean) / sc.std).sum
    sumScores / combinedStd
  }

  def sampleTopScore(votes: Seq[BaseVote]): Double = {
    sampleTopScore(scoreCounts(votes))
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

  /*
   * Note on confidence intervals:
   * For a normal (gausssian) random variable, the 95% confidence interval is mean +/- 2 * standard error
   */
  val ConfidenceInterval95Percent = 2

  def engagementUpperBound(votes: Seq[BaseVote]): Double = {
    val counts = scoreCounts(votes)

    val engagementEstimate: RateEstimate =
      rateEstimate(counts.agreeCount + counts.disagreeCount, counts.votes)

    engagementEstimate.rate + ConfidenceInterval95Percent * engagementEstimate.sd
  }

  def engagementConfidenceInterval(counts: ScoreCounts): Double = {
    val estimate = rateEstimate(counts.agreeCount + counts.disagreeCount, counts.votes)
    ConfidenceInterval95Percent * estimate.sd
  }

  def agreementConfidenceInterval(counts: ScoreCounts): Double = {
    val estimate = rateEstimate(counts.agreeCount, counts.votes)
    ConfidenceInterval95Percent * estimate.sd
  }

  def adhesionCondidenceInterval(counts: ScoreCounts): Double = {
    val loveEstimate = rateEstimate(counts.loveCount, counts.agreeCount + counts.disagreeCount)
    val hateEstimate = rateEstimate(counts.hateCount, counts.agreeCount + counts.disagreeCount)
    ConfidenceInterval95Percent * math.hypot(loveEstimate.sd, hateEstimate.sd)
  }

  def realisticConfidenceInterval(counts: ScoreCounts): Double = {
    val doableEstimate = rateEstimate(counts.doableCount, counts.agreeCount + counts.disagreeCount)
    val impossibleEstimate = rateEstimate(counts.impossibleCount, counts.agreeCount + counts.disagreeCount)
    ConfidenceInterval95Percent * math.hypot(doableEstimate.sd, impossibleEstimate.sd)
  }

  def platitudeConfidenceInterval(counts: ScoreCounts): Double = {
    val platitudeAgreeEstimate = rateEstimate(counts.platitudeAgreeCount, counts.agreeCount + counts.disagreeCount)
    val platitudeDisagreeEstimate =
      rateEstimate(counts.platitudeDisagreeCount, counts.agreeCount + counts.disagreeCount)
    ConfidenceInterval95Percent * math.hypot(platitudeAgreeEstimate.sd, platitudeDisagreeEstimate.sd)
  }

  val topScoreConfidenceIntervalFunctions: Map[String, ScoreCounts => Double] = Map(
    "engagement" -> engagementConfidenceInterval,
    "agreement" -> agreementConfidenceInterval,
    "adhesion" -> adhesionCondidenceInterval,
    "realistic" -> realisticConfidenceInterval,
    "platitude" -> platitudeConfidenceInterval
  )

  def topScoreConfidenceInterval(counts: ScoreCounts): Double = {
    val sumCI = math.sqrt(
      topScoreComponents
        .map(sc => math.pow(sc.weight * topScoreConfidenceIntervalFunctions(sc.name)(counts) / sc.std, 2))
        .sum
    )
    sumCI / combinedStd
  }

  def topScoreConfidenceInterval(votes: Seq[BaseVote]): Double = {
    topScoreConfidenceInterval(scoreCounts(votes))
  }

  def topScoreUpperBound(votes: Seq[BaseVote]): Double = {
    val counts = scoreCounts(votes)
    val score = topScore(counts)
    val confidenceInterval = topScoreConfidenceInterval(counts)
    score + confidenceInterval
  }

  def controversyUpperBound(votes: Seq[BaseVote]): Double = {
    val counts = scoreCounts(votes)

    val controversyEstimate: RateEstimate =
      rateEstimate(math.min(counts.loveCount, counts.hateCount), counts.votes - counts.neutralCount)

    controversyEstimate.rate + ConfidenceInterval95Percent * controversyEstimate.sd
  }

  def sequencePool(sequenceConfiguration: SequenceConfiguration,
                   votes: Seq[BaseVote],
                   status: ProposalStatus): SequencePool = {
    val votesCount: Int = votes.map(_.count).sum
    val engagementRate: Double = ProposalScorerHelper.engagementUpperBound(votes)
    val scoreRate: Double = ProposalScorerHelper.topScoreUpperBound(votes)
    val controversyRate: Double = ProposalScorerHelper.controversyUpperBound(votes)

    if (status == ProposalStatus.Accepted && votesCount < sequenceConfiguration.newProposalsVoteThreshold) {
      SequencePool.New
    } else if (status == ProposalStatus.Accepted &&
               votesCount < sequenceConfiguration.testedProposalsMaxVotesThreshold &&
               engagementRate > sequenceConfiguration.testedProposalsEngagementThreshold &&
               (scoreRate >= sequenceConfiguration.testedProposalsScoreThreshold ||
               controversyRate >= sequenceConfiguration.testedProposalsControversyThreshold)) {
      SequencePool.Tested
    } else {
      SequencePool.Excluded
    }
  }

}
