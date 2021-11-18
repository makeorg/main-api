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

import grizzled.slf4j.Logging
import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.commons.math3.random.{MersenneTwister, RandomGenerator}
import org.make.api.proposal.ProposalScorer.{findSmoothing, Score, VotesCounter}
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
import org.make.core.sequence.SequenceConfiguration
import org.make.api.technical.types._

final class ProposalScorer(votingOptions: VotingOptions, counter: VotesCounter, nonSequenceVotesWeight: Double) {

  private def tradeOff(generalScore: Double, specificScore: Double): Double = {
    nonSequenceVotesWeight * generalScore + (1 - nonSequenceVotesWeight) * specificScore
  }

  private def countVotes(counter: VotesCounter): Int =
    counter(votingOptions.agreeVote.vote) +
      counter(votingOptions.neutralVote.vote) +
      counter(votingOptions.disagreeVote.vote)

  private def count(key: Key, countingFunction: VotesCounter): Int =
    countingFunction {
      key match {
        case Agree             => votingOptions.agreeVote.vote
        case Disagree          => votingOptions.disagreeVote.vote
        case Neutral           => votingOptions.neutralVote.vote
        case DoNotCare         => votingOptions.neutralVote.doNotCare
        case DoNotUnderstand   => votingOptions.neutralVote.doNotUnderstand
        case Doable            => votingOptions.agreeVote.doable
        case Impossible        => votingOptions.disagreeVote.impossible
        case LikeIt            => votingOptions.agreeVote.likeIt
        case NoOpinion         => votingOptions.neutralVote.noOpinion
        case NoWay             => votingOptions.disagreeVote.noWay
        case PlatitudeAgree    => votingOptions.agreeVote.platitudeAgree
        case PlatitudeDisagree => votingOptions.disagreeVote.platitudeDisagree
      }
    }

  private def individualScore(key: Key, countingFunction: VotesCounter = counter): Double = {
    (count(key, countingFunction) + findSmoothing(key)) / (1 + countVotes(countingFunction))
  }

  def score(key: Key): Double = {
    tradeOff(individualScore(key, _.countVerified), individualScore(key))
  }

  private def confidence(key: Key): Double = {
    val specificVotesCount = countVotes(counter)
    val specificVotes = individualScore(key)
    ProposalScorer.confidenceInterval(specificVotes, specificVotesCount)
  }

  private def keyScore(key: Key): Score = Score.forKey(this, key, this.counter)

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
    val votesCount: Int = countVotes(counter)
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
    new ProposalScorer(votingOptions, counter, nonSequenceVotesWeight)
  }

  private val random: RandomGenerator = new MersenneTwister()

  def setSeed(seed: Int): Unit = {
    random.setSeed(seed)
  }

  private val votesSmoothing: Double = 0.33
  private val qualificationsSmoothing: Double = 0.01

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

    def forKey(votes: ProposalScorer, key: Key, scorer: VotesCounter): Score = {
      val successes = votes.count(key, scorer)
      val trials = votes.countVotes(scorer)
      val smoothing = findSmoothing(key)
      val sample = new BetaDistribution(random, successes + smoothing, trials - successes + 1).sample()
      Score(score = votes.score(key), confidence = votes.confidence(key), sample = sample)
    }
  }

  def findSmoothing(key: Key): Double = {
    key match {
      case _: VoteKey          => ProposalScorer.votesSmoothing
      case _: QualificationKey => ProposalScorer.qualificationsSmoothing
    }
  }

  private def sumConfidenceIntervals(firstConfidence: Double, secondConfidence: Double): Double =
    Math.sqrt(Math.pow(firstConfidence, 2) + Math.pow(secondConfidence, 2))

  type VotesCounter = BaseVoteOrQualification[_] => Int

  object VotesCounter {
    val SequenceVotesCounter: VotesCounter = _.countSequence
    val SegmentVotesCounter: VotesCounter = _.countSegment
    val VerifiedVotesVotesCounter: VotesCounter = _.countVerified
  }
}
