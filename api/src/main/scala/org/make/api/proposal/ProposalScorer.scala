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
import org.make.api.proposal.ProposalScorer.{findSmoothing, Score, ScorePart, VotesCounter}
import org.make.api.technical.MakeRandom
import org.make.core.proposal.QualificationKey.{Doable, Impossible, LikeIt, NoWay, PlatitudeAgree, PlatitudeDisagree}
import org.make.core.proposal.VoteKey.{Agree, Disagree, Neutral}
import org.make.core.proposal._
import org.make.core.proposal.indexed.{SequencePool, Zone}
import org.make.core.sequence.SequenceConfiguration

final class ProposalScorer(votes: Seq[BaseVote], counter: VotesCounter, nonSequenceVotesWeight: Double) {

  private def tradeOff(generalScore: Double, specificScore: Double): Double = {
    nonSequenceVotesWeight * generalScore + (1 - nonSequenceVotesWeight) * specificScore
  }

  def countVotes(counter: VotesCounter): Int = {
    votes.map(counter).sum
  }

  def count(key: Key, countingFunction: VotesCounter = counter): Int = {
    val maybeVoteOrQualification = key match {
      case voteKey: VoteKey =>
        votes.find(_.key == voteKey)
      case qualificationKey: QualificationKey =>
        votes.flatMap(_.qualifications).find(_.key == qualificationKey)
    }
    maybeVoteOrQualification.map(countingFunction).getOrElse(0)
  }

  def individualScore(key: Key, countingFunction: VotesCounter = counter): Double = {
    (count(key, countingFunction) + findSmoothing(key)) / (1 + countVotes(countingFunction))
  }

  def score(key: Key): Double = {
    tradeOff(individualScore(key, _.countVerified), individualScore(key))
  }

  def confidence(key: Key): Double = {
    val specificVotesCount = countVotes(counter)
    val specificVotes = individualScore(key)

    ProposalScorer.confidenceInterval(specificVotes, specificVotesCount)
  }

  def computeScore(part: ScorePart): Score = {
    Score(
      part.score(this),
      part.confidence(this),
      nonSequenceVotesWeight => part.sample(this, this.chooseCounter(nonSequenceVotesWeight))
    )
  }

  lazy val adhesion: Score = {
    computeScore(ProposalScorer.adhesion)
  }

  lazy val engagement: Score = {
    computeScore(ProposalScorer.engagement)
  }

  lazy val realistic: Score = {
    computeScore(ProposalScorer.realistic)
  }

  lazy val platitude: Score = {
    computeScore(ProposalScorer.platitude)
  }

  lazy val topScore: Score = {
    computeScore(ProposalScorer.topScore)
  }

  lazy val controversy: Score = {
    computeScore(ProposalScorer.controversy)
  }

  lazy val rejection: Score = {
    computeScore(ProposalScorer.rejection)
  }

  lazy val neutral: Score = {
    computeScore(ProposalScorer.neutral)
  }

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
  lazy val zone: Zone = {
    if (score(Agree) >= 0.6 || (score(Neutral) < 0.4 && score(Disagree) < 0.15)) {
      Zone.Consensus
    } else if (score(Disagree) >= 0.6 || (score(Neutral) < 0.4 && score(Agree) < 0.15)) {
      Zone.Rejection
    } else if (score(Neutral) >= 0.4) {
      Zone.Limbo
    } else {
      Zone.Controversy
    }
  }

  def pool(sequenceConfiguration: SequenceConfiguration, status: ProposalStatus): SequencePool = {
    val votesCount: Int = countVotes(counter)
    val engagementRate: Double = engagement.lowerBound
    val scoreRate: Double = topScore.lowerBound
    val controversyRate: Double = controversy.lowerBound

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

  def chooseCounter(nonSequenceVotesWeight: Double): VotesCounter = {
    val r = MakeRandom.nextDouble()
    if (r < nonSequenceVotesWeight) {
      VotesCounter.VerifiedVotesVotesCounter
    } else {
      this.counter
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
  def apply(votes: Seq[BaseVote], counter: VotesCounter, nonSequenceVotesWeight: Double): ProposalScorer = {
    new ProposalScorer(votes, counter, nonSequenceVotesWeight)
  }

  val random: RandomGenerator = new MersenneTwister()

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

  val adhesion: ScorePart = ScorePart(Plus, Agree)

  val platitude: ScorePart = ScorePart.sum(ScorePart(Plus, PlatitudeAgree), ScorePart(Plus, PlatitudeDisagree))

  val greatness: ScorePart = ScorePart.sum(ScorePart(Plus, LikeIt), ScorePart(Minus, NoWay))

  val realistic: ScorePart = ScorePart.sum(ScorePart(Plus, Doable), ScorePart(Minus, Impossible))

  val engagement: ScorePart =
    ScorePart.sum(ScorePart(Plus, greatness), ScorePart(Plus, realistic), ScorePart(Minus, platitude))

  val topScore: ScorePart = ScorePart.sum(ScorePart(Plus, adhesion), ScorePart(Plus, engagement))

  val controversy: ScorePart = ScorePart.sum(
    CompoundScorePart(Plus, ScorePart(Plus, adhesion), ScorePart(Plus, Disagree)),
    CompoundScorePart(Plus, ScorePart(Plus, LikeIt), ScorePart(Plus, NoWay))
  )

  val rejection: ScorePart = ScorePart.sum(
    ScorePart(Plus, Disagree),
    ScorePart(Minus, realistic),
    ScorePart(Minus, greatness),
    ScorePart(Minus, platitude)
  )

  val neutral: ScorePart = ScorePart(Plus, Neutral)

  final case class Score(score: Double, confidence: Double, sample: Double => Double) {
    val upperBound: Double = score + confidence
    val lowerBound: Double = score - confidence
  }

  sealed trait Sign {
    def signValue(value: Double): Double
  }
  case object Plus extends Sign {
    override def signValue(value: Double): Double = value
  }
  case object Minus extends Sign {
    override def signValue(value: Double): Double = -value
  }

  sealed trait ScorePart {
    def score(votes: ProposalScorer): Double
    def confidence(votes: ProposalScorer): Double
    def sample(votes: ProposalScorer, scorer: VotesCounter): Double
  }

  object ScorePart {
    def apply(sign: Sign, key: Key): ScorePart = {
      BasicScorePart(sign, key)
    }

    def apply(sign: Sign, part: ScorePart): ScorePart = new ScorePart {
      override def score(votes: ProposalScorer): Double = {
        sign.signValue(part.score(votes))
      }
      override def confidence(votes: ProposalScorer): Double = {
        part.confidence(votes)
      }
      override def sample(votes: ProposalScorer, scorer: VotesCounter): Double = {
        sign.signValue(part.sample(votes, scorer))
      }
    }

    def sum(parts: ScorePart*): ScorePart = {
      SumScorePart(parts)
    }
  }

  def findSmoothing(key: Key): Double = {
    key match {
      case _: VoteKey          => ProposalScorer.votesSmoothing
      case _: QualificationKey => ProposalScorer.qualificationsSmoothing
    }
  }

  final case class BasicScorePart(sign: Sign, key: Key) extends ScorePart {
    override def score(votes: ProposalScorer): Double = {
      sign.signValue(votes.score(key))
    }
    override def confidence(votes: ProposalScorer): Double = {
      votes.confidence(key)
    }
    override def sample(votes: ProposalScorer, scorer: VotesCounter): Double = {
      val successes = votes.count(key, scorer)
      val trials: Int = votes.countVotes(scorer)
      val smoothing = findSmoothing(key)
      sign.signValue(new BetaDistribution(random, successes + smoothing, trials - successes + 1).sample())
    }
  }

  final case class CompoundScorePart(sign: Sign, first: ScorePart, second: ScorePart) extends ScorePart {
    override def score(votes: ProposalScorer): Double = {
      sign.signValue(Math.min(first.score(votes), second.score(votes)))
    }

    override def confidence(votes: ProposalScorer): Double = {
      Math.max(first.confidence(votes), second.confidence(votes))
    }
    override def sample(votes: ProposalScorer, scorer: VotesCounter): Double = {
      sign.signValue(Math.min(first.sample(votes, scorer), second.sample(votes, scorer)))
    }
  }

  final case class SumScorePart(parts: Seq[ScorePart]) extends ScorePart {
    override def score(votes: ProposalScorer): Double = {
      parts.map(_.score(votes)).sum
    }

    override def confidence(votes: ProposalScorer): Double = {
      SumScorePart.sumConfidenceIntervals(parts.map(_.confidence(votes)): _*)
    }

    override def sample(votes: ProposalScorer, scorer: VotesCounter): Double = {
      parts.map(_.sample(votes, scorer)).sum
    }
  }

  object SumScorePart {
    def sumConfidenceIntervals(confidences: Double*): Double = {
      Math.sqrt(confidences.map(Math.pow(_, 2)).sum)
    }
  }

  type VotesCounter = BaseVoteOrQualification[_] => Int

  object VotesCounter {
    val SequenceVotesCounter: VotesCounter = _.countSequence
    val SegmentVotesCounter: VotesCounter = _.countSegment
    val VerifiedVotesVotesCounter: VotesCounter = _.countVerified
  }
}
