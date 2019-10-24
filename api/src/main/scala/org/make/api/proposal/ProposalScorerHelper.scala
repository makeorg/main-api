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
import org.make.core.proposal.{BaseQualification, BaseVote, ProposalStatus, QualificationKey, VoteKey}

object ProposalScorerHelper extends StrictLogging {
  var random: RandomGenerator = new MersenneTwister()

  case class ScoreComponent(name: String, weight: Double, mean: Double, std: Double)

  val topScoreComponents: Seq[ScoreComponent] = Seq(
    ScoreComponent("engagement", 1, 0.8, 0.1),
    ScoreComponent("agreement", 1, 0.75, 0.15),
    ScoreComponent("adhesion", 1, 0.075, 0.07),
    ScoreComponent("realistic", 2, 0.1, 0.07),
    ScoreComponent("platitude", -2, 0.05, 0.05)
  )

  val combinedStd: Double = math.sqrt(topScoreComponents.map(sc => math.pow(sc.weight, 2)).sum)

  case class ScoreCounts(votes: Int,
                         agreeCount: Int,
                         disagreeCount: Int,
                         neutralCount: Int,
                         platitudeAgreeCount: Int,
                         platitudeDisagreeCount: Int,
                         loveCount: Int,
                         hateCount: Int,
                         doableCount: Int,
                         impossibleCount: Int) {

    def engagement(): Double = {
      (agreeCount + disagreeCount + 2 * ScoreCounts.VotePrior) / (votes + ScoreCounts.CountPrior).toDouble
    }

    def agreement(): Double = {
      (agreeCount + ScoreCounts.VotePrior) / (votes + ScoreCounts.CountPrior).toDouble
    }

    def adhesion(): Double = {
      (loveCount - hateCount) / (agreeCount + disagreeCount + ScoreCounts.CountPrior).toDouble
    }

    def realistic(): Double = {
      (doableCount - impossibleCount) / (agreeCount + disagreeCount + ScoreCounts.CountPrior).toDouble
    }

    def platitude(): Double = {
      (platitudeAgreeCount + platitudeDisagreeCount) / (agreeCount + disagreeCount + ScoreCounts.CountPrior).toDouble
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
    val topScoreFunctions: Map[String, () => Double] = Map(
      "engagement" -> engagement,
      "agreement" -> agreement,
      "adhesion" -> adhesion,
      "realistic" -> realistic,
      "platitude" -> platitude
    )

    def topScore(): Double = {
      val sumScores =
        topScoreComponents.map(sc => sc.weight * (topScoreFunctions(sc.name)() - sc.mean) / sc.std).sum
      sumScores / combinedStd
    }

    def controversy(): Double = {
      math.min(loveCount + ScoreCounts.QualificationPrior, hateCount + ScoreCounts.QualificationPrior) / (votes - neutralCount + ScoreCounts.CountPrior).toDouble
    }

    def rejection(): Double = {
      -1 * adhesion()
    }

    def sampleEngagement(): Double = {
      ScoreCounts.sampleRate(agreeCount + disagreeCount, votes, 2 * ScoreCounts.VotePrior)
    }

    def sampleAgreement(): Double = {
      ScoreCounts.sampleRate(agreeCount, votes, ScoreCounts.VotePrior)
    }

    def sampleAdhesion(): Double = {
      (ScoreCounts.sampleRate(loveCount, votes - neutralCount, ScoreCounts.QualificationPrior)
        - ScoreCounts.sampleRate(hateCount, votes - neutralCount, ScoreCounts.QualificationPrior))
    }

    def sampleRealistic(): Double = {
      (ScoreCounts.sampleRate(doableCount, votes - neutralCount, ScoreCounts.QualificationPrior)
        - ScoreCounts.sampleRate(impossibleCount, votes - neutralCount, ScoreCounts.QualificationPrior))
    }

    def samplePlatitude(): Double = {
      (ScoreCounts.sampleRate(platitudeAgreeCount, votes - neutralCount, ScoreCounts.QualificationPrior)
        + ScoreCounts.sampleRate(platitudeDisagreeCount, votes - neutralCount, ScoreCounts.QualificationPrior))
    }

    def sampleControversy(): Double = {
      ScoreCounts.sampleRate(math.min(loveCount, hateCount), votes - neutralCount, ScoreCounts.QualificationPrior)
    }

    val topScoreSampleFunctions: Map[String, () => Double] = Map(
      "engagement" -> sampleEngagement,
      "agreement" -> sampleAgreement,
      "adhesion" -> sampleAdhesion,
      "realistic" -> sampleRealistic,
      "platitude" -> samplePlatitude
    )

    def sampleTopScore(): Double = {
      val sumScores =
        topScoreComponents.map(sc => sc.weight * (topScoreSampleFunctions(sc.name)() - sc.mean) / sc.std).sum
      sumScores / combinedStd
    }

    def engagementUpperBound(): Double = {
      val engagementEstimate: RateEstimate =
        rateEstimate(agreeCount + disagreeCount, votes)

      engagementEstimate.rate + ScoreCounts.ConfidenceInterval95Percent * engagementEstimate.sd
    }

    def engagementConfidenceInterval(): Double = {
      val estimate = rateEstimate(agreeCount + disagreeCount, votes)
      ScoreCounts.ConfidenceInterval95Percent * estimate.sd
    }

    def agreementConfidenceInterval(): Double = {
      val estimate = rateEstimate(agreeCount, votes)
      ScoreCounts.ConfidenceInterval95Percent * estimate.sd
    }

    def adhesionCondidenceInterval(): Double = {
      val loveEstimate = rateEstimate(loveCount, agreeCount + disagreeCount)
      val hateEstimate = rateEstimate(hateCount, agreeCount + disagreeCount)
      ScoreCounts.ConfidenceInterval95Percent * math.hypot(loveEstimate.sd, hateEstimate.sd)
    }

    def realisticConfidenceInterval(): Double = {
      val doableEstimate = rateEstimate(doableCount, agreeCount + disagreeCount)
      val impossibleEstimate = rateEstimate(impossibleCount, agreeCount + disagreeCount)
      ScoreCounts.ConfidenceInterval95Percent * math.hypot(doableEstimate.sd, impossibleEstimate.sd)
    }

    def platitudeConfidenceInterval(): Double = {
      val platitudeAgreeEstimate = rateEstimate(platitudeAgreeCount, agreeCount + disagreeCount)
      val platitudeDisagreeEstimate =
        rateEstimate(platitudeDisagreeCount, agreeCount + disagreeCount)
      ScoreCounts.ConfidenceInterval95Percent * math.hypot(platitudeAgreeEstimate.sd, platitudeDisagreeEstimate.sd)
    }

    val topScoreConfidenceIntervalFunctions: Map[String, () => Double] = Map(
      "engagement" -> engagementConfidenceInterval,
      "agreement" -> agreementConfidenceInterval,
      "adhesion" -> adhesionCondidenceInterval,
      "realistic" -> realisticConfidenceInterval,
      "platitude" -> platitudeConfidenceInterval
    )

    def topScoreConfidenceInterval(): Double = {
      val sumCI = math.sqrt(
        topScoreComponents
          .map(sc => math.pow(sc.weight * topScoreConfidenceIntervalFunctions(sc.name)() / sc.std, 2))
          .sum
      )
      sumCI / combinedStd
    }

    def topScoreUpperBound(): Double = {
      val score = topScore()
      val confidenceInterval = topScoreConfidenceInterval()
      score + confidenceInterval
    }

    def controversyUpperBound(): Double = {
      val controversyEstimate: RateEstimate =
        rateEstimate(math.min(loveCount, hateCount), votes - neutralCount)

      controversyEstimate.rate + ScoreCounts.ConfidenceInterval95Percent * controversyEstimate.sd
    }
  }

  object ScoreCounts {

    def fromSequenceVotes(votes: Seq[BaseVote]): ScoreCounts = apply(votes, _.countSequence, _.countSequence)
    def fromSegmentVotes(votes: Seq[BaseVote]): ScoreCounts = apply(votes, _.countSegment, _.countSegment)

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

    /*
     * Note on confidence intervals:
     * For a normal (gaussian) random variable, the 95% confidence interval is mean +/- 2 * standard error
     */
    val ConfidenceInterval95Percent = 2

    private def totalVoteCount(votes: Seq[BaseVote], counter: BaseVote => Int): Int = {
      votes.map(counter).sum
    }

    private def voteCount(votes: Seq[BaseVote], voteKey: VoteKey, counter: BaseVote => Int): Int = {
      votes.filter(_.key == voteKey).map(counter).sum
    }

    private def qualificationCount(votes: Seq[BaseVote],
                                   voteKey: VoteKey,
                                   qualificationKey: QualificationKey,
                                   counter: BaseQualification => Int): Int = {
      votes
        .filter(_.key == voteKey)
        .flatMap(_.qualifications.filter(_.key == qualificationKey).map(counter))
        .sum
    }

    /*
     * Samples are taken from a beta distribution, which is the bayesian companion distribution for the binomial distribution
     * Each rate is sampled from its own beta distribution with a prior at 0.33 for votes and 0.01 for qualifications
     */
    def sampleRate(successes: Int, trials: Int, prior: Double): Double = {
      new BetaDistribution(random, successes + prior, trials - successes + ScoreCounts.CountPrior).sample()
    }

    private def apply(votes: Seq[BaseVote],
                      voteCounter: BaseVote                   => Int,
                      qualificationCounter: BaseQualification => Int): ScoreCounts = {
      ScoreCounts(
        votes = totalVoteCount(votes, voteCounter),
        agreeCount = voteCount(votes, Agree, voteCounter),
        disagreeCount = voteCount(votes, Disagree, voteCounter),
        neutralCount = voteCount(votes, Neutral, voteCounter),
        platitudeAgreeCount = qualificationCount(votes, Agree, PlatitudeAgree, qualificationCounter),
        platitudeDisagreeCount = qualificationCount(votes, Disagree, PlatitudeDisagree, qualificationCounter),
        loveCount = qualificationCount(votes, Agree, LikeIt, qualificationCounter),
        hateCount = qualificationCount(votes, Disagree, NoWay, qualificationCounter),
        doableCount = qualificationCount(votes, Agree, Doable, qualificationCounter),
        impossibleCount = qualificationCount(votes, Disagree, Impossible, qualificationCounter)
      )
    }
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

  def sequencePool(sequenceConfiguration: SequenceConfiguration,
                   status: ProposalStatus,
                   scores: ScoreCounts): SequencePool = {

    val votesCount: Int = scores.votes
    val engagementRate: Double = scores.engagementUpperBound()
    val scoreRate: Double = scores.topScoreUpperBound()
    val controversyRate: Double = scores.controversyUpperBound()

    def isTestedFromScoreAndControversy: Boolean =
      (sequenceConfiguration.testedProposalsScoreThreshold, sequenceConfiguration.testedProposalsControversyThreshold) match {
        case (Some(scoreBase), Some(controversyBase)) => scoreRate >= scoreBase || controversyRate >= controversyBase
        case (Some(scoreBase), _)                     => scoreRate >= scoreBase
        case (_, Some(controversyBase))               => controversyRate >= controversyBase
        case _                                        => true
      }

    if (status == ProposalStatus.Accepted && votesCount < sequenceConfiguration.newProposalsVoteThreshold) {
      SequencePool.New
    } else if (status == ProposalStatus.Accepted &&
               sequenceConfiguration.testedProposalsMaxVotesThreshold.fold(true)(votesCount < _) &&
               sequenceConfiguration.testedProposalsEngagementThreshold.fold(true)(engagementRate > _) &&
               isTestedFromScoreAndControversy) {
      SequencePool.Tested
    } else {
      SequencePool.Excluded
    }
  }

}
