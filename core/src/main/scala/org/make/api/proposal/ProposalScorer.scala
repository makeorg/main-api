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

import enumeratum.{Enum, EnumEntry}
import grizzled.slf4j.Logging
import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.commons.math3.random.{MersenneTwister, RandomGenerator}
import org.make.api.proposal.ProposalScorer.VotesCounter.{
  SegmentVotesCounter,
  SequenceVotesCounter,
  VerifiedVotesVotesCounter
}
import org.make.api.proposal.ProposalScorer.Score
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
import org.make.core.proposal._
import org.make.core.proposal.indexed.{SequencePool, Zone}
import org.make.core.proposal.Key._
import org.make.core.sequence.SequenceConfiguration
import org.make.api.technical.types._

final class ProposalScorer(val counts: VoteCounts, val verifiedCounts: VoteCounts, nonSequenceVotesWeight: Double) {

  private def tradeOff(generalScore: Double, specificScore: Double): Double =
    nonSequenceVotesWeight * generalScore + (1 - nonSequenceVotesWeight) * specificScore

  private def individualScore(keyCount: Int, smoothing: Double, totalVotes: Int): Double =
    Math.min((keyCount + smoothing) / (1 + totalVotes), 1)

  def score(keyCount: Int, keyVerifiedCount: Int, smoothing: Double): Double = {
    tradeOff(
      individualScore(keyVerifiedCount, smoothing, verifiedCounts.totalVotes),
      individualScore(keyCount, smoothing, counts.totalVotes)
    )
  }

  private def confidence(keyCount: Int, smoothing: Double): Double = {
    val specificVotesCount = counts.totalVotes
    val specificVotes = individualScore(keyCount, smoothing, specificVotesCount)
    ProposalScorer.confidenceInterval(specificVotes, specificVotesCount)
  }

  private def keyScore[T <: Key: HasCount: HasSmoothing](key: T): Score = Score.forKey(this, key)

  lazy val agree: Score = keyScore(Agree)
  lazy val disagree: Score = keyScore(Disagree)
  lazy val neutral: Score = keyScore(Neutral)

  lazy val likeIt: Score = keyScore(LikeIt)
  lazy val doable: Score = keyScore(Doable)
  lazy val platitudeAgree: Score = keyScore(PlatitudeAgree)

  lazy val impossible: Score = keyScore(Impossible)
  lazy val noWay: Score = keyScore(NoWay)
  lazy val platitudeDisagree: Score = keyScore(PlatitudeDisagree)

  lazy val noOpinion: Score = keyScore(NoOpinion)
  lazy val doNotCare: Score = keyScore(DoNotCare)
  lazy val doNotUnderstand: Score = keyScore(DoNotUnderstand)

  lazy val platitude: Score = platitudeAgree + platitudeDisagree
  lazy val topScore: Score = agree + likeIt + doable - noWay - impossible - platitude
  lazy val controversy: Score = Score.min(agree, disagree) + Score.min(likeIt, noWay)
  lazy val rejection: Score = disagree + noWay + impossible - likeIt - doable - platitude

  lazy val adhesion: Score = agree
  lazy val greatness: Score = likeIt - noWay
  lazy val realistic: Score = doable - impossible

  lazy val engagement: Score = greatness + realistic - platitude

  lazy val zone: Zone = zone(agree.score, disagree.score, neutral.score)
  lazy val sampledZone: Zone = zone(agree.sample, disagree.sample, neutral.sample)

  /* Taken from the dial:
  if (proposition['score_v2_adhesion'] >= .6) or \
          (proposition['score_v2_neutral'] < .4 and proposition['score_v2_disagree'] < .15):
      return 'consensus'
  if (proposition['score_v2_disagree'] >= .6) or \
          (proposition['score_v2_neutral'] < .4 and proposition['score_v2_adhesion'] < .15):
      return 'rejection'
  if proposition['score_v2_neutral'] >= .4:
      return 'limbo'
  return 'controversy'
   */
  private def zone(agree: Double, disagree: Double, neutral: Double): Zone = {
    if (agree >= 0.6 || neutral < 0.4 && disagree < 0.15) {
      Zone.Consensus
    } else if (disagree >= 0.6 || (neutral < 0.4 && agree < 0.15)) {
      Zone.Rejection
    } else if (neutral >= 0.4) {
      Zone.Limbo
    } else {
      Zone.Controversy
    }
  }

  def pool(configuration: SequenceConfiguration, status: ProposalStatus): SequencePool = {
    val votesCount: Int = counts.totalVotes
    val engagementRate: Double = engagement.lowerBound
    val scoreRate: Double = topScore.lowerBound
    val controversyRate: Double = controversy.lowerBound

    def isTestedFromScoreAndControversy: Boolean =
      (configuration.testedProposalsScoreThreshold, configuration.testedProposalsControversyThreshold) match {
        case (Some(scoreBase), Some(controversyBase)) => scoreRate >= scoreBase || controversyRate >= controversyBase
        case (Some(scoreBase), _)                     => scoreRate >= scoreBase
        case (_, Some(controversyBase))               => controversyRate >= controversyBase
        case _                                        => true
      }

    if (status == ProposalStatus.Accepted && votesCount < configuration.newProposalsVoteThreshold) {
      SequencePool.New
    } else if (status == ProposalStatus.Accepted &&
               configuration.testedProposalsMaxVotesThreshold.forall(votesCount < _) &&
               configuration.testedProposalsEngagementThreshold.forall(engagementRate > _) &&
               isTestedFromScoreAndControversy) {
      SequencePool.Tested
    } else {
      SequencePool.Excluded
    }
  }
}

/**
  * Define scores and the way to compute it.
  *
  * See https://gitlab.com/makeorg/data-science/make-data/-/blob/preproduction/make_data/score_v2.py
  * it defines the computations from the dial.
  */
object ProposalScorer extends Logging {

  private def findVoteOrFallback(votes: Seq[BaseVote], key: VoteKey): BaseVote =
    votes.find(_.key == key).getOrElse(Vote(key, 0, 0, 0, 0, Seq.empty))

  private def findQualificationOrFallback(
    qualifications: Seq[BaseQualification],
    key: QualificationKey
  ): BaseQualification =
    qualifications.find(_.key == key).getOrElse(Qualification(key, 0, 0, 0, 0))

  def apply(votes: Seq[BaseVote], counter: VotesCounter, nonSequenceVotesWeight: Double): ProposalScorer = {
    val agreeOption = findVoteOrFallback(votes, Agree)
    val neutralOption = findVoteOrFallback(votes, Neutral)
    val disagreeOption = findVoteOrFallback(votes, Disagree)
    val votingOptions = VotingOptions(
      AgreeWrapper(
        vote = agreeOption,
        likeIt = findQualificationOrFallback(agreeOption.qualifications, LikeIt),
        platitudeAgree = findQualificationOrFallback(agreeOption.qualifications, PlatitudeAgree),
        doable = findQualificationOrFallback(agreeOption.qualifications, Doable)
      ),
      NeutralWrapper(
        vote = neutralOption,
        noOpinion = findQualificationOrFallback(neutralOption.qualifications, NoOpinion),
        doNotUnderstand = findQualificationOrFallback(neutralOption.qualifications, DoNotUnderstand),
        doNotCare = findQualificationOrFallback(neutralOption.qualifications, DoNotCare)
      ),
      DisagreeWrapper(
        vote = disagreeOption,
        impossible = findQualificationOrFallback(disagreeOption.qualifications, Impossible),
        noWay = findQualificationOrFallback(disagreeOption.qualifications, NoWay),
        platitudeDisagree = findQualificationOrFallback(disagreeOption.qualifications, PlatitudeDisagree)
      )
    )
    val counts = counter match {
      case SequenceVotesCounter      => votingOptions.sequenceCounts
      case SegmentVotesCounter       => votingOptions.segmentCounts
      case VerifiedVotesVotesCounter => votingOptions.verifiedCounts
    }
    new ProposalScorer(counts, votingOptions.verifiedCounts, nonSequenceVotesWeight)
  }

  private val random: RandomGenerator = new MersenneTwister()

  def setSeed(seed: Int): Unit = {
    random.setSeed(seed)
  }

  val votesSmoothing: Double = 0.33
  val qualificationsSmoothing: Double = 0.01

  def confidenceInterval(probability: Double, observations: Double): Double = {
    val smoothedObservations: Double = observations + 4
    val smoothedProbability: Double = (observations * probability + 2) / smoothedObservations
    val standardDeviation: Double = Math.sqrt(smoothedProbability * (1 - smoothedProbability) / smoothedObservations)
    2 * standardDeviation
  }

  final case class Score(score: Double, confidence: Double, sample: Double) {
    val upperBound: Double = score + confidence
    val lowerBound: Double = score - confidence

    def +(other: Score): Score = {
      Score(
        score = this.score + other.score,
        confidence = sumConfidenceIntervals(this.confidence, other.confidence),
        sample = this.sample + other.sample
      )
    }

    def -(other: Score): Score = {
      Score(
        score = this.score - other.score,
        confidence = sumConfidenceIntervals(this.confidence, other.confidence),
        sample = this.sample - other.sample
      )
    }
  }

  object Score {
    def min(first: Score, second: Score): Score = {
      Score(
        score = Math.min(first.score, second.score),
        confidence = Math.max(first.confidence, second.confidence),
        sample = Math.min(first.sample, second.sample)
      )
    }

    def forKey[T <: Key: HasCount: HasSmoothing](votes: ProposalScorer, key: T): Score = {
      val successes = key.count(votes.counts)
      val verifiedSuccesses = key.count(votes.verifiedCounts)
      val trials = votes.counts.totalVotes
      val smoothing = key.smoothing

      val sample = new BetaDistribution(random, successes + smoothing, trials - successes + 1).sample()
      Score(
        score = votes.score(successes, verifiedSuccesses, smoothing),
        confidence = votes.confidence(successes, smoothing),
        sample = sample
      )
    }
  }

  private def sumConfidenceIntervals(firstConfidence: Double, secondConfidence: Double): Double =
    Math.sqrt(Math.pow(firstConfidence, 2) + Math.pow(secondConfidence, 2))

  sealed abstract class VotesCounter extends EnumEntry

  object VotesCounter extends Enum[VotesCounter] {

    case object SequenceVotesCounter extends VotesCounter
    case object SegmentVotesCounter extends VotesCounter
    case object VerifiedVotesVotesCounter extends VotesCounter

    override val values: IndexedSeq[VotesCounter] = findValues
  }
}
