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

import org.make.api.MakeUnitTest
import org.make.api.proposal.ProposalScorerHelper.ScoreCounts
import org.make.api.sequence.SequenceConfiguration
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.QualificationKey.{Doable, Impossible, LikeIt, NoWay, PlatitudeAgree, PlatitudeDisagree}
import org.make.core.proposal.VoteKey.{Agree, Disagree, Neutral}
import org.make.core.proposal._
import org.make.core.proposal.indexed.SequencePool
import org.make.core.question.QuestionId
import org.make.core.sequence.SequenceId

import scala.collection.immutable.Seq
import scala.util.Random

class ProposalScorerHelperTest extends MakeUnitTest {

  def createVotes(
    nbVoteAgree: Int = 0,
    nbVoteDisagree: Int = 0,
    nbVoteNeutral: Int = 0,
    nbQualificationLikeIt: Int = 0,
    nbQualificationDoable: Int = 0,
    nbQualificationPlatitudeAgree: Int = 0,
    nbQualificationNoWay: Int = 0,
    nbQualificationImpossible: Int = 0,
    nbQualificationPlatitudeDisagree: Int = 0,
    nbQualificationDoNotUnderstand: Int = 0,
    nbQualificationNoOpinion: Int = 0,
    nbQualificationDoNotCare: Int = 0
  ): Seq[Vote] = {
    Seq(
      Vote(
        key = VoteKey.Agree,
        count = nbVoteAgree,
        countVerified = nbVoteAgree,
        countSequence = nbVoteAgree,
        countSegment = nbVoteAgree,
        qualifications = Seq(
          Qualification(
            key = QualificationKey.LikeIt,
            count = nbQualificationLikeIt,
            countVerified = nbQualificationLikeIt,
            countSequence = nbQualificationLikeIt,
            countSegment = nbQualificationLikeIt
          ),
          Qualification(
            key = QualificationKey.Doable,
            count = nbQualificationDoable,
            countVerified = nbQualificationDoable,
            countSequence = nbQualificationDoable,
            countSegment = nbQualificationDoable
          ),
          Qualification(
            key = QualificationKey.PlatitudeAgree,
            count = nbQualificationPlatitudeAgree,
            countVerified = nbQualificationPlatitudeAgree,
            countSequence = nbQualificationPlatitudeAgree,
            countSegment = nbQualificationPlatitudeAgree
          )
        )
      ),
      Vote(
        key = VoteKey.Disagree,
        count = nbVoteDisagree,
        countVerified = nbVoteDisagree,
        countSequence = nbVoteDisagree,
        countSegment = nbVoteDisagree,
        qualifications = Seq(
          Qualification(
            key = QualificationKey.NoWay,
            count = nbQualificationNoWay,
            countVerified = nbQualificationNoWay,
            countSequence = nbQualificationNoWay,
            countSegment = nbQualificationNoWay
          ),
          Qualification(
            key = QualificationKey.Impossible,
            count = nbQualificationImpossible,
            countVerified = nbQualificationImpossible,
            countSequence = nbQualificationImpossible,
            countSegment = nbQualificationImpossible
          ),
          Qualification(
            key = QualificationKey.PlatitudeDisagree,
            count = nbQualificationPlatitudeDisagree,
            countVerified = nbQualificationPlatitudeDisagree,
            countSequence = nbQualificationPlatitudeDisagree,
            countSegment = nbQualificationPlatitudeDisagree
          )
        )
      ),
      Vote(
        key = VoteKey.Neutral,
        count = nbVoteNeutral,
        countVerified = nbVoteNeutral,
        countSequence = nbVoteNeutral,
        countSegment = nbVoteNeutral,
        qualifications = Seq(
          Qualification(
            key = QualificationKey.DoNotUnderstand,
            count = nbQualificationDoNotUnderstand,
            countVerified = nbQualificationDoNotUnderstand,
            countSequence = nbQualificationDoNotUnderstand,
            countSegment = nbQualificationDoNotUnderstand
          ),
          Qualification(
            key = QualificationKey.NoOpinion,
            count = nbQualificationNoOpinion,
            countVerified = nbQualificationNoOpinion,
            countSequence = nbQualificationNoOpinion,
            countSegment = nbQualificationNoOpinion
          ),
          Qualification(
            key = QualificationKey.DoNotCare,
            count = nbQualificationDoNotCare,
            countVerified = nbQualificationDoNotCare,
            countSequence = nbQualificationDoNotCare,
            countSegment = nbQualificationDoNotCare
          )
        )
      )
    )
  }

  val proposalWithoutvote: Proposal = proposal(ProposalId("proposalWithoutvote"))
  val proposalWithVote: Proposal =
    proposal(
      ProposalId("proposalWithVote"),
      votes = createVotes(nbVoteAgree = 100, nbVoteNeutral = 20, nbVoteDisagree = 42)
    )
  val proposalWithVoteandQualification: Proposal =
    proposal(
      id = ProposalId("proposalWithVoteandQualification"),
      votes = createVotes(
        nbVoteAgree = 100,
        nbVoteDisagree = 42,
        nbVoteNeutral = 20,
        nbQualificationLikeIt = 10,
        nbQualificationDoable = 20,
        nbQualificationPlatitudeAgree = 30,
        nbQualificationNoWay = 30,
        nbQualificationImpossible = 10,
        nbQualificationPlatitudeDisagree = 12,
        nbQualificationDoNotUnderstand = 5,
        nbQualificationNoOpinion = 7,
        nbQualificationDoNotCare = 4
      )
    )

  val scoreCounts: ScoreCounts =
    ScoreCounts(
      votes = 20,
      agreeCount = 12,
      disagreeCount = 4,
      neutralCount = 4,
      platitudeAgreeCount = 6,
      platitudeDisagreeCount = 2,
      loveCount = 10,
      hateCount = 3,
      doableCount = 5,
      impossibleCount = 1
    )

  feature("counts") {
    scenario("count vote when proposal has no vote") {
      ScoreCounts.fromSequenceVotes(proposalWithoutvote.votes).neutralCount should be(0)
    }

    scenario("count vote when proposal has vote") {
      ScoreCounts.fromSequenceVotes(proposalWithVote.votes).neutralCount should be(20)
    }

    scenario("count qualification when proposal has no vote") {
      ScoreCounts.fromSequenceVotes(proposalWithoutvote.votes).realistic() should be(0)
    }

    scenario("count vote when proposal has vote and qualification") {
      ScoreCounts.fromSequenceVotes(proposalWithVoteandQualification.votes).loveCount should be(10)
    }
  }

  feature("scores") {
    scenario("calculate engagement") {
      ScoreCounts.fromSequenceVotes(proposalWithoutvote.votes).engagement() should equal(0.66 +- 0.01)
      ScoreCounts.fromSequenceVotes(proposalWithVote.votes).engagement() should equal(0.87 +- 0.01)
      ScoreCounts.fromSequenceVotes(proposalWithVoteandQualification.votes).engagement() should equal(0.87 +- 0.01)
      scoreCounts.engagement() should equal(0.79 +- 0.01)
    }

    scenario("calculate adhesion") {
      ScoreCounts.fromSequenceVotes(proposalWithoutvote.votes).adhesion() should equal(0.0)
      ScoreCounts.fromSequenceVotes(proposalWithVote.votes).adhesion() should equal(0.0)
      ScoreCounts.fromSequenceVotes(proposalWithVoteandQualification.votes).adhesion() should equal(-0.14 +- 0.01)
      scoreCounts.adhesion() should equal(0.41 +- 0.01)
    }

    scenario("calculate realistic") {
      ScoreCounts.fromSequenceVotes(proposalWithoutvote.votes).realistic() should equal(0.0)
      ScoreCounts.fromSequenceVotes(proposalWithVote.votes).realistic() should equal(0.0)
      ScoreCounts.fromSequenceVotes(proposalWithVoteandQualification.votes).realistic() should equal(0.07 +- 0.01)
      scoreCounts.realistic() should equal(0.23 +- 0.01)
    }

    scenario("calculate topScore from proposal") {
      ScoreCounts.fromSequenceVotes(proposalWithoutvote.votes).topScore() should equal(-1.84 +- 0.01)
      ScoreCounts.fromSequenceVotes(proposalWithVote.votes).topScore() should equal(-0.62 +- 0.01)
      ScoreCounts.fromSequenceVotes(proposalWithVoteandQualification.votes).topScore() should equal(-4.16 +- 0.01)
      scoreCounts.topScore() should equal(-2.80 +- 0.01)
    }

    scenario("calculate controversy from proposal") {
      ScoreCounts.fromSequenceVotes(proposalWithoutvote.votes).controversy() should equal(0.01 +- 0.01)
      ScoreCounts.fromSequenceVotes(proposalWithVote.votes).controversy() should equal(0.01 +- 0.01)
      scoreCounts.controversy() should equal(0.17 +- 0.01)
    }

    scenario("calculate rejection") {
      ScoreCounts.fromSequenceVotes(proposalWithoutvote.votes).rejection() should equal(0.0)
      ScoreCounts.fromSequenceVotes(proposalWithVote.votes).rejection() should equal(0.0)
      ScoreCounts
        .fromSequenceVotes(proposalWithVoteandQualification.votes)
        .rejection() should equal(0.14 +- 0.01)
      scoreCounts.rejection() should equal(-0.41 +- 0.01)
    }
  }

  feature("score confidence interval") {
    val rgen = Random
    rgen.setSeed(0)
    val nTrials = 1000
    val success = (1 to nTrials)
      .map(_ => {
        val nbVoteAgree = rgen.nextInt(100)
        val nbVoteDisagree = rgen.nextInt(100 - nbVoteAgree)
        val nbVoteNeutral = 100 - nbVoteAgree - nbVoteDisagree

        val votes = createVotes(
          nbVoteAgree,
          nbVoteDisagree,
          nbVoteNeutral,
          nbQualificationLikeIt = rgen.nextInt(1 + nbVoteAgree / 2),
          nbQualificationDoable = rgen.nextInt(1 + nbVoteAgree / 2),
          nbQualificationPlatitudeAgree = rgen.nextInt(1 + nbVoteAgree / 3),
          nbQualificationNoWay = rgen.nextInt(1 + nbVoteDisagree / 2),
          nbQualificationImpossible = rgen.nextInt(1 + nbVoteDisagree / 2),
          nbQualificationPlatitudeDisagree = rgen.nextInt(1 + nbVoteDisagree / 3)
        )

        val counts = ScoreCounts.fromSequenceVotes(votes)
        val score = counts.topScore()
        val confidence_interval = counts.topScoreConfidenceInterval()
        val sampleScore = counts.sampleTopScore()

        if (sampleScore > score - confidence_interval && sampleScore < score + confidence_interval) {
          1
        } else {
          0
        }
      })
      .sum
      .toDouble / nTrials.toDouble

    logger.debug(success.toString)

    success should be(0.95 +- 0.05)
  }

  feature("proposal pool") {
    scenario("news proposal") {
      val configuration = SequenceConfiguration(SequenceId("fake"), QuestionId("fake-too"))
      ProposalScorerHelper
        .sequencePool(configuration, Accepted, ScoreCounts.fromSequenceVotes(proposalWithoutvote.votes)) should be(
        SequencePool.New
      )
    }

  }

  feature("score counts") {
    def votes(
      agree: Int,
      disagree: Int,
      neutral: Int,
      qualifsAgree: Seq[Qualification] = Seq.empty,
      qualifsNeutral: Seq[Qualification] = Seq.empty,
      qualifsDisagree: Seq[Qualification] = Seq.empty
    ): Seq[Vote] =
      Seq(
        Vote(Agree, agree, agree, agree, 0, qualifsAgree),
        Vote(Neutral, neutral, neutral, neutral, 0, qualifsNeutral),
        Vote(Disagree, disagree, disagree, disagree, 0, qualifsDisagree)
      )
    def qualifications(
      platitudeAgree: Int,
      platitudeDisagree: Int,
      likeIt: Int,
      noWay: Int,
      doable: Int,
      impossible: Int
    ): Seq[Qualification] =
      Seq(
        Qualification(PlatitudeAgree, 0, 0, platitudeAgree, 0),
        Qualification(PlatitudeDisagree, 0, 0, platitudeDisagree, 0),
        Qualification(LikeIt, 0, 0, likeIt, 0),
        Qualification(NoWay, 0, 0, noWay, 0),
        Qualification(Doable, 0, 0, doable, 0),
        Qualification(Impossible, 0, 0, impossible, 0)
      )
    scenario("votes") {
      ScoreCounts.fromSequenceVotes(votes(1, 2, 3)).votes shouldBe 6
      ScoreCounts.fromSequenceVotes(votes(10, 20, 30)).votes shouldBe 60
      ScoreCounts.fromSequenceVotes(votes(17, 15, 10)).votes shouldBe 42
    }

    scenario("agreeCount") {
      ScoreCounts.fromSequenceVotes(votes(42, 0, 1)).agreeCount shouldBe 42
      ScoreCounts.fromSequenceVotes(votes(0, 1, 2)).agreeCount shouldBe 0
      ScoreCounts.fromSequenceVotes(votes(1, 564, 126)).agreeCount shouldBe 1
    }

    scenario("disagreeCount") {
      ScoreCounts.fromSequenceVotes(votes(42, 0, 1)).disagreeCount shouldBe 0
      ScoreCounts.fromSequenceVotes(votes(0, 1, 2)).disagreeCount shouldBe 1
      ScoreCounts.fromSequenceVotes(votes(1, 564, 126)).disagreeCount shouldBe 564
    }

    scenario("neutralCount") {
      ScoreCounts.fromSequenceVotes(votes(42, 0, 0)).neutralCount shouldBe 0
      ScoreCounts.fromSequenceVotes(votes(0, 1, 2)).neutralCount shouldBe 2
      ScoreCounts.fromSequenceVotes(votes(1, 564, 126)).neutralCount shouldBe 126
    }

    scenario("platitudeAgreeCount") {
      ScoreCounts.fromSequenceVotes(votes(0, 0, 0, qualifications(0, 0, 0, 0, 0, 0))).platitudeAgreeCount shouldBe 0
      ScoreCounts.fromSequenceVotes(votes(0, 0, 0, qualifications(42, 0, 0, 0, 0, 0))).platitudeAgreeCount shouldBe 42
      ScoreCounts
        .fromSequenceVotes(votes(1102, 564, 126, qualifications(500, 12, 423, 14, 324, 210)))
        .platitudeAgreeCount shouldBe 500
    }

    scenario("platitudeDisagreeCount") {
      ScoreCounts
        .fromSequenceVotes(votes(0, 0, 0, Seq.empty, Seq.empty, qualifications(0, 0, 0, 0, 0, 0)))
        .platitudeDisagreeCount shouldBe 0
      ScoreCounts
        .fromSequenceVotes(votes(0, 0, 0, Seq.empty, Seq.empty, qualifications(0, 42, 0, 0, 0, 0)))
        .platitudeDisagreeCount shouldBe 42
      ScoreCounts
        .fromSequenceVotes(votes(1102, 564, 126, Seq.empty, Seq.empty, qualifications(500, 12, 423, 14, 324, 210)))
        .platitudeDisagreeCount shouldBe 12
    }

    scenario("loveCount") {
      ScoreCounts.fromSequenceVotes(votes(0, 0, 0, qualifications(0, 0, 0, 0, 0, 0))).loveCount shouldBe 0
      ScoreCounts.fromSequenceVotes(votes(0, 0, 0, qualifications(0, 0, 42, 0, 0, 0))).loveCount shouldBe 42
      ScoreCounts
        .fromSequenceVotes(votes(1102, 564, 126, qualifications(500, 12, 423, 14, 324, 210)))
        .loveCount shouldBe 423
    }

    scenario("hateCount") {
      ScoreCounts
        .fromSequenceVotes(votes(0, 0, 0, Seq.empty, Seq.empty, qualifications(0, 0, 0, 0, 0, 0)))
        .hateCount shouldBe 0
      ScoreCounts
        .fromSequenceVotes(votes(0, 0, 0, Seq.empty, Seq.empty, qualifications(0, 0, 0, 42, 0, 0)))
        .hateCount shouldBe 42
      ScoreCounts
        .fromSequenceVotes(votes(1102, 564, 126, Seq.empty, Seq.empty, qualifications(500, 12, 423, 14, 324, 210)))
        .hateCount shouldBe 14
    }

    scenario("doableCount") {
      ScoreCounts.fromSequenceVotes(votes(0, 0, 0, qualifications(0, 0, 0, 0, 0, 0))).doableCount shouldBe 0
      ScoreCounts.fromSequenceVotes(votes(0, 0, 0, qualifications(0, 0, 0, 0, 42, 0))).doableCount shouldBe 42
      ScoreCounts
        .fromSequenceVotes(votes(1102, 564, 126, qualifications(500, 12, 423, 14, 324, 210)))
        .doableCount shouldBe 324
    }

    scenario("impossibleCount") {
      ScoreCounts
        .fromSequenceVotes(votes(0, 0, 0, Seq.empty, Seq.empty, qualifications(0, 0, 0, 0, 0, 0)))
        .impossibleCount shouldBe 0
      ScoreCounts
        .fromSequenceVotes(votes(0, 0, 0, Seq.empty, Seq.empty, qualifications(0, 0, 0, 0, 0, 42)))
        .impossibleCount shouldBe 42
      ScoreCounts
        .fromSequenceVotes(votes(1102, 564, 126, Seq.empty, Seq.empty, qualifications(500, 12, 423, 14, 324, 210)))
        .impossibleCount shouldBe 210
    }

    scenario("combined top score") {
      val counts1 = ScoreCounts.fromSequenceVotes(proposalWithVote.votes)
      val counts2 = ScoreCounts.fromSequenceVotes(proposalWithVoteandQualification.votes)

      counts1.topScore() should equal(-0.62 +- 0.01)
      counts2.topScore() should equal(-4.16 +- 0.01)

      val sequenceConfiguration =
        SequenceConfiguration(
          sequenceId = SequenceId("fake"),
          questionId = QuestionId("fake"),
          nonSequenceVotesWeight = 0.4
        )

      ScoreCounts.topScore(sequenceConfiguration, counts1, counts2) should equal(-2.74 +- 0.02)
      ScoreCounts.topScoreLowerBound(sequenceConfiguration, counts1, counts2) should equal(-4.02 +- 0.02)
      ScoreCounts.topScoreUpperBound(sequenceConfiguration, counts1, counts2) should equal(-1.46 +- 0.02)
    }

  }
}
