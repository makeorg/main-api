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
import org.make.core.idea.IdeaId
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.QualificationKey.{Doable, Impossible, LikeIt, NoWay, PlatitudeAgree, PlatitudeDisagree}
import org.make.core.proposal.VoteKey.{Agree, Disagree, Neutral}
import org.make.core.proposal._
import org.make.core.proposal.indexed.SequencePool
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.sequence.SequenceId
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}

import scala.collection.immutable.Seq
import scala.util.Random

class ProposalScorerHelperTest extends MakeUnitTest {

  def createProposal(nbVoteAgree: Int = 0,
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
                     nbQualificationDoNotCare: Int = 0): Proposal = Proposal(
    proposalId = ProposalId("99999999-9999-9999-9999-999999999999"),
    slug = "il-faut-faire-une-proposition",
    country = Some(Country("FR")),
    language = Some(Language("fr")),
    content = "Il faut faire une proposition",
    author = UserId("99999999-9999-9999-9999-999999999999"),
    labels = Seq.empty,
    theme = Some(ThemeId("foo-theme")),
    status = ProposalStatus.Accepted,
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now()),
    votes = Seq(
      Vote(
        key = VoteKey.Agree,
        countVerified = nbVoteAgree,
        qualifications = Seq(
          Qualification(
            key = QualificationKey.LikeIt,
            count = nbQualificationLikeIt,
            countVerified = nbQualificationLikeIt
          ),
          Qualification(key = QualificationKey.Doable, countVerified = nbQualificationDoable),
          Qualification(key = QualificationKey.PlatitudeAgree, countVerified = nbQualificationPlatitudeAgree)
        )
      ),
      Vote(
        key = VoteKey.Disagree,
        countVerified = nbVoteDisagree,
        qualifications = Seq(
          Qualification(key = QualificationKey.NoWay, countVerified = nbQualificationNoWay),
          Qualification(key = QualificationKey.Impossible, countVerified = nbQualificationImpossible),
          Qualification(key = QualificationKey.PlatitudeDisagree, countVerified = nbQualificationPlatitudeDisagree)
        )
      ),
      Vote(
        key = VoteKey.Neutral,
        count = nbVoteNeutral,
        countVerified = nbVoteNeutral,
        qualifications = Seq(
          Qualification(key = QualificationKey.DoNotUnderstand, countVerified = nbQualificationDoNotUnderstand),
          Qualification(key = QualificationKey.NoOpinion, countVerified = nbQualificationNoOpinion),
          Qualification(key = QualificationKey.DoNotCare, countVerified = nbQualificationDoNotCare)
        )
      )
    ),
    tags = Seq.empty,
    organisations = Seq.empty,
    creationContext = RequestContext.empty,
    idea = Some(IdeaId("idea-id")),
    operation = None,
    events = List.empty
  )

  val proposalWithoutvote: Proposal = createProposal()
  val proposalWithVote: Proposal = createProposal(nbVoteAgree = 100, nbVoteNeutral = 20, nbVoteDisagree = 42)
  val proposalWithVoteandQualification: Proposal =
    createProposal(
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
      ScoreCounts(proposalWithoutvote.votes, _.countVerified, _.countVerified).neutralCount should be(0)
    }

    scenario("count vote when proposal has vote") {
      ScoreCounts(proposalWithVote.votes, _.countVerified, _.countVerified).neutralCount should be(20)
    }

    scenario("count qualification when proposal has no vote") {
      ScoreCounts(proposalWithoutvote.votes, _.countVerified, _.countVerified).realistic() should be(0)
    }

    scenario("count vote when proposal has vote and qualification") {
      ScoreCounts(proposalWithVoteandQualification.votes, _.countVerified, _.countVerified).loveCount should be(10)
    }
  }

  feature("scores") {
    scenario("calculate engagement") {
      ScoreCounts(proposalWithoutvote.votes, _.countVerified, _.countVerified).engagement() should equal(0.66 +- 0.01)
      ScoreCounts(proposalWithVote.votes, _.countVerified, _.countVerified).engagement() should equal(0.87 +- 0.01)
      ScoreCounts(proposalWithVoteandQualification.votes, _.countVerified, _.countVerified).engagement() should equal(
        0.87 +- 0.01
      )
      scoreCounts.engagement() should equal(0.79 +- 0.01)
    }

    scenario("calculate adhesion") {
      ScoreCounts(proposalWithoutvote.votes, _.countVerified, _.countVerified).adhesion() should equal(0.0)
      ScoreCounts(proposalWithVote.votes, _.countVerified, _.countVerified).adhesion() should equal(0.0)
      ScoreCounts(proposalWithVoteandQualification.votes, _.countVerified, _.countVerified).adhesion() should equal(
        -0.14 +- 0.01
      )
      scoreCounts.adhesion() should equal(0.41 +- 0.01)
    }

    scenario("calculate realistic") {
      ScoreCounts(proposalWithoutvote.votes, _.countVerified, _.countVerified).realistic() should equal(0.0)
      ScoreCounts(proposalWithVote.votes, _.countVerified, _.countVerified).realistic() should equal(0.0)
      ScoreCounts(proposalWithVoteandQualification.votes, _.countVerified, _.countVerified).realistic() should equal(
        0.07 +- 0.01
      )
      scoreCounts.realistic() should equal(0.23 +- 0.01)
    }

    scenario("calculate topScore from proposal") {
      ScoreCounts(proposalWithoutvote.votes, _.countVerified, _.countVerified).topScore() should equal(-1.84 +- 0.01)
      ScoreCounts(proposalWithVote.votes, _.countVerified, _.countVerified).topScore() should equal(-0.62 +- 0.01)
      ScoreCounts(proposalWithVoteandQualification.votes, _.countVerified, _.countVerified).topScore() should equal(
        -4.16 +- 0.01
      )
      scoreCounts.topScore() should equal(-2.80 +- 0.01)
    }

    scenario("calculate controversy from proposal") {
      ScoreCounts(proposalWithoutvote.votes, _.countVerified, _.countVerified).controversy() should equal(0.01 +- 0.01)
      ScoreCounts(proposalWithVote.votes, _.countVerified, _.countVerified).controversy() should equal(0.01 +- 0.01)
      scoreCounts.controversy() should equal(0.17 +- 0.01)
    }

    scenario("calculate rejection") {
      ScoreCounts(proposalWithoutvote.votes, _.countVerified, _.countVerified).rejection() should equal(0.0)
      ScoreCounts(proposalWithVote.votes, _.countVerified, _.countVerified).rejection() should equal(0.0)
      ScoreCounts(proposalWithVoteandQualification.votes, _.countVerified, _.countVerified)
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

        val proposal = createProposal(
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

        val counts = ScoreCounts(proposal.votes, _.countVerified, _.countVerified)
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
        .sequencePool(configuration, proposalWithoutvote.votes, Accepted, _.countVerified, _.countVerified) should be(
        SequencePool.New
      )
    }

  }

  feature("score counts") {
    def votes(agree: Int,
              disagree: Int,
              neutral: Int,
              qualifsAgree: Seq[Qualification] = Seq.empty,
              qualifsNeutral: Seq[Qualification] = Seq.empty,
              qualifsDisagree: Seq[Qualification] = Seq.empty): Seq[Vote] =
      Seq(
        Vote(Agree, agree, agree, agree, 0, qualifsAgree),
        Vote(Neutral, neutral, neutral, neutral, 0, qualifsNeutral),
        Vote(Disagree, disagree, disagree, disagree, 0, qualifsDisagree)
      )
    def qualifications(platitudeAgree: Int,
                       platitudeDisagree: Int,
                       likeIt: Int,
                       noWay: Int,
                       doable: Int,
                       impossible: Int): Seq[Qualification] =
      Seq(
        Qualification(PlatitudeAgree, 0, platitudeAgree),
        Qualification(PlatitudeDisagree, 0, platitudeDisagree),
        Qualification(LikeIt, 0, likeIt),
        Qualification(NoWay, 0, noWay),
        Qualification(Doable, 0, doable),
        Qualification(Impossible, 0, impossible)
      )
    scenario("votes") {
      ScoreCounts(votes(1, 2, 3), _.countVerified, _.countVerified).votes shouldBe 6
      ScoreCounts(votes(10, 20, 30), _.countVerified, _.countVerified).votes shouldBe 60
      ScoreCounts(votes(17, 15, 10), _.countVerified, _.countVerified).votes shouldBe 42
    }

    scenario("agreeCount") {
      ScoreCounts(votes(42, 0, 1), _.countVerified, _.countVerified).agreeCount shouldBe 42
      ScoreCounts(votes(0, 1, 2), _.countVerified, _.countVerified).agreeCount shouldBe 0
      ScoreCounts(votes(1, 564, 126), _.countVerified, _.countVerified).agreeCount shouldBe 1
    }

    scenario("disagreeCount") {
      ScoreCounts(votes(42, 0, 1), _.countVerified, _.countVerified).disagreeCount shouldBe 0
      ScoreCounts(votes(0, 1, 2), _.countVerified, _.countVerified).disagreeCount shouldBe 1
      ScoreCounts(votes(1, 564, 126), _.countVerified, _.countVerified).disagreeCount shouldBe 564
    }

    scenario("neutralCount") {
      ScoreCounts(votes(42, 0, 0), _.countVerified, _.countVerified).neutralCount shouldBe 0
      ScoreCounts(votes(0, 1, 2), _.countVerified, _.countVerified).neutralCount shouldBe 2
      ScoreCounts(votes(1, 564, 126), _.countVerified, _.countVerified).neutralCount shouldBe 126
    }

    scenario("platitudeAgreeCount") {
      ScoreCounts(votes(0, 0, 0, qualifications(0, 0, 0, 0, 0, 0)), _.countVerified, _.countVerified).platitudeAgreeCount shouldBe 0
      ScoreCounts(votes(0, 0, 0, qualifications(42, 0, 0, 0, 0, 0)), _.countVerified, _.countVerified).platitudeAgreeCount shouldBe 42
      ScoreCounts(votes(1102, 564, 126, qualifications(500, 12, 423, 14, 324, 210)), _.countVerified, _.countVerified).platitudeAgreeCount shouldBe 500
    }

    scenario("platitudeDisagreeCount") {
      ScoreCounts(
        votes(0, 0, 0, Seq.empty, Seq.empty, qualifications(0, 0, 0, 0, 0, 0)),
        _.countVerified,
        _.countVerified
      ).platitudeDisagreeCount shouldBe 0
      ScoreCounts(
        votes(0, 0, 0, Seq.empty, Seq.empty, qualifications(0, 42, 0, 0, 0, 0)),
        _.countVerified,
        _.countVerified
      ).platitudeDisagreeCount shouldBe 42
      ScoreCounts(
        votes(1102, 564, 126, Seq.empty, Seq.empty, qualifications(500, 12, 423, 14, 324, 210)),
        _.countVerified,
        _.countVerified
      ).platitudeDisagreeCount shouldBe 12
    }

    scenario("loveCount") {
      ScoreCounts(votes(0, 0, 0, qualifications(0, 0, 0, 0, 0, 0)), _.countVerified, _.countVerified).loveCount shouldBe 0
      ScoreCounts(votes(0, 0, 0, qualifications(0, 0, 42, 0, 0, 0)), _.countVerified, _.countVerified).loveCount shouldBe 42
      ScoreCounts(votes(1102, 564, 126, qualifications(500, 12, 423, 14, 324, 210)), _.countVerified, _.countVerified).loveCount shouldBe 423
    }

    scenario("hateCount") {
      ScoreCounts(
        votes(0, 0, 0, Seq.empty, Seq.empty, qualifications(0, 0, 0, 0, 0, 0)),
        _.countVerified,
        _.countVerified
      ).hateCount shouldBe 0
      ScoreCounts(
        votes(0, 0, 0, Seq.empty, Seq.empty, qualifications(0, 0, 0, 42, 0, 0)),
        _.countVerified,
        _.countVerified
      ).hateCount shouldBe 42
      ScoreCounts(
        votes(1102, 564, 126, Seq.empty, Seq.empty, qualifications(500, 12, 423, 14, 324, 210)),
        _.countVerified,
        _.countVerified
      ).hateCount shouldBe 14
    }

    scenario("doableCount") {
      ScoreCounts(votes(0, 0, 0, qualifications(0, 0, 0, 0, 0, 0)), _.countVerified, _.countVerified).doableCount shouldBe 0
      ScoreCounts(votes(0, 0, 0, qualifications(0, 0, 0, 0, 42, 0)), _.countVerified, _.countVerified).doableCount shouldBe 42
      ScoreCounts(votes(1102, 564, 126, qualifications(500, 12, 423, 14, 324, 210)), _.countVerified, _.countVerified).doableCount shouldBe 324
    }

    scenario("impossibleCount") {
      ScoreCounts(
        votes(0, 0, 0, Seq.empty, Seq.empty, qualifications(0, 0, 0, 0, 0, 0)),
        _.countVerified,
        _.countVerified
      ).impossibleCount shouldBe 0
      ScoreCounts(
        votes(0, 0, 0, Seq.empty, Seq.empty, qualifications(0, 0, 0, 0, 0, 42)),
        _.countVerified,
        _.countVerified
      ).impossibleCount shouldBe 42
      ScoreCounts(
        votes(1102, 564, 126, Seq.empty, Seq.empty, qualifications(500, 12, 423, 14, 324, 210)),
        _.countVerified,
        _.countVerified
      ).impossibleCount shouldBe 210
    }

  }
}
