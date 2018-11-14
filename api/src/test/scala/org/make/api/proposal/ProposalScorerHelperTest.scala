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

import java.time.ZonedDateTime
import scala.util.Random

import org.make.api.MakeUnitTest
import org.make.api.proposal.ProposalScorerHelper.ScoreCounts
import org.make.api.sequence.SequenceConfiguration
import org.make.core.RequestContext
import org.make.core.idea.IdeaId
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal._
import org.make.core.proposal.indexed.SequencePool
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.sequence.SequenceId
import org.make.core.user.UserId

import scala.collection.immutable.Seq

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
    createdAt = Some(ZonedDateTime.now),
    updatedAt = Some(ZonedDateTime.now),
    votes = Seq(
      Vote(
        key = VoteKey.Agree,
        count = nbVoteAgree,
        qualifications = Seq(
          Qualification(key = QualificationKey.LikeIt, count = nbQualificationLikeIt),
          Qualification(key = QualificationKey.Doable, count = nbQualificationDoable),
          Qualification(key = QualificationKey.PlatitudeAgree, count = nbQualificationPlatitudeAgree)
        )
      ),
      Vote(
        key = VoteKey.Disagree,
        count = nbVoteDisagree,
        qualifications = Seq(
          Qualification(key = QualificationKey.NoWay, count = nbQualificationNoWay),
          Qualification(key = QualificationKey.Impossible, count = nbQualificationImpossible),
          Qualification(key = QualificationKey.PlatitudeDisagree, count = nbQualificationPlatitudeDisagree)
        )
      ),
      Vote(
        key = VoteKey.Neutral,
        count = nbVoteNeutral,
        qualifications = Seq(
          Qualification(key = QualificationKey.DoNotUnderstand, count = nbQualificationDoNotUnderstand),
          Qualification(key = QualificationKey.NoOpinion, count = nbQualificationNoOpinion),
          Qualification(key = QualificationKey.DoNotCare, count = nbQualificationDoNotCare)
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
  val proposalWithVote: Proposal = createProposal(nbVoteAgree = 100, nbVoteNeutral = 20)
  val proposalWithVoteandQualification: Proposal =
    createProposal(
      nbVoteAgree = 100,
      nbVoteNeutral = 20,
      nbQualificationLikeIt = 10,
      nbQualificationDoable = 20,
      nbQualificationPlatitudeAgree = 30
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

  feature("count vote by voteKey") {
    scenario("count vote when proposal has no vote") {
      ProposalScorerHelper.voteCounts(proposalWithoutvote.votes, VoteKey.Neutral) should be(0)
    }

    scenario("count vote when proposal has vote") {
      ProposalScorerHelper.voteCounts(proposalWithVote.votes, VoteKey.Neutral) should be(20)
    }
  }

  feature("count qualification by voteKey and qualification key") {
    scenario("count qualification when proposal has no vote") {
      ProposalScorerHelper.qualificationCounts(proposalWithoutvote.votes, VoteKey.Neutral, QualificationKey.NoOpinion) should be(
        0
      )
    }

    scenario("count vote when proposal has vote and qualification") {
      ProposalScorerHelper.qualificationCounts(
        proposalWithVoteandQualification.votes,
        VoteKey.Agree,
        QualificationKey.LikeIt
      ) should be(10)
    }
  }

  feature("count score of a proposal") {
    scenario("return a ScoreCounts object") {
      ProposalScorerHelper.scoreCounts(proposalWithVoteandQualification.votes) shouldBe a[ScoreCounts]
    }
  }

  feature("calculate engagement") {
    scenario("calculate engagement from proposal") {
      ProposalScorerHelper.engagement(proposalWithoutvote.votes) should equal(0.66 +- 0.01)
      ProposalScorerHelper.engagement(proposalWithVote.votes) should equal(0.83 +- 0.01)
      ProposalScorerHelper.engagement(proposalWithVoteandQualification.votes) should equal(0.83 +- 0.01)
    }

    scenario("calculate engagement from count score") {
      ProposalScorerHelper.engagement(scoreCounts) should equal(0.79 +- 0.01)
    }
  }

  feature("calculate adhesion") {
    scenario("calculate adhesion from proposal") {
      ProposalScorerHelper.adhesion(proposalWithoutvote.votes) should equal(0.0)
      ProposalScorerHelper.adhesion(proposalWithVote.votes) should equal(0.0)
      ProposalScorerHelper.adhesion(proposalWithVoteandQualification.votes) should equal(0.1 +- 0.01)
    }

    scenario("calculate adhesion from count score") {
      ProposalScorerHelper.adhesion(scoreCounts) should equal(0.41 +- 0.01)
    }
  }

  feature("calculate realistic") {
    scenario("calculate realistic from proposal") {
      ProposalScorerHelper.realistic(proposalWithoutvote.votes) should equal(0.0)
      ProposalScorerHelper.realistic(proposalWithVote.votes) should equal(0.0)
      ProposalScorerHelper.realistic(proposalWithVoteandQualification.votes) should equal(0.2 +- 0.01)
    }

    scenario("calculate realistic from count score") {
      ProposalScorerHelper.realistic(scoreCounts) should equal(0.23 +- 0.01)
    }
  }

  feature("calculate topScore") {
    scenario("calculate topScore from proposal") {
      ProposalScorerHelper.topScore(proposalWithoutvote.votes) should equal(-2.27 +- 0.01)
      ProposalScorerHelper.topScore(proposalWithVote.votes) should equal(-0.25 +- 0.01)
      ProposalScorerHelper.topScore(proposalWithVoteandQualification.votes) should equal(-1.7 +- 0.01)
    }

    scenario("calculate topScore from count score") {
      ProposalScorerHelper.topScore(scoreCounts) should equal(-2.97 +- 0.01)
    }
  }

  feature("calculate controversy") {
    scenario("calculate controversy from proposal") {
      ProposalScorerHelper.controversy(proposalWithoutvote.votes) should equal(0.01 +- 0.01)
      ProposalScorerHelper.controversy(proposalWithVote.votes) should equal(0.01 +- 0.01)
      //ProposalScorerHelper.controversy(proposalWithVoteandQualification.votes) should equal(0.0009 +- 0.01)
    }

    scenario("calculate controversy from count score") {
      ProposalScorerHelper.controversy(scoreCounts) should equal(0.17 +- 0.01)
    }
  }

  feature("calculate rejection") {
    scenario("calculate rejection from proposal") {
      ProposalScorerHelper.rejection(proposalWithoutvote.votes) should equal(0.0)
      ProposalScorerHelper.rejection(proposalWithVote.votes) should equal(0.0)
      ProposalScorerHelper.rejection(proposalWithVoteandQualification.votes) should equal(-0.1 +- 0.01)
    }

    scenario("calculate rejection from count score") {
      ProposalScorerHelper.rejection(scoreCounts) should equal(-0.41 +- 0.01)
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

        val score = ProposalScorerHelper.topScore(proposal.votes)
        val confidence_interval = ProposalScorerHelper.topScoreConfidenceInterval(proposal.votes)
        val sampleScore = ProposalScorerHelper.sampleTopScore(proposal.votes)

        if (sampleScore > score - confidence_interval && sampleScore < score + confidence_interval) {
          1
        } else {
          0
        }
      })
      .sum
      .toDouble / nTrials.toDouble

    logger.debug(success.toString)

    (success should be).equals(0.95 +- 0.05)
  }

  feature("proposal pool") {
    scenario("news proposal") {
      val configuration = SequenceConfiguration(SequenceId("fake"), QuestionId("fake-too"))
      ProposalScorerHelper.sequencePool(configuration, proposalWithoutvote.votes, Accepted) should be(SequencePool.New)
    }

  }
}
