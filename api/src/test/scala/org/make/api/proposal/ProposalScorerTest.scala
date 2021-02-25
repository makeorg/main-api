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
import org.make.api.proposal.ProposalScorer.VotesCounter
import org.make.api.proposal.ProposalScorerTest.ExpectedScores
import org.make.core.proposal.ProposalStatus.Accepted
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
import org.make.core.proposal.indexed.SequencePool
import org.make.core.question.QuestionId
import org.make.core.sequence.{SequenceConfiguration, SequenceId}

import scala.collection.immutable.Seq

class ProposalScorerTest extends MakeUnitTest {

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

  Feature("proposal pool") {
    Scenario("news proposal") {
      val configuration = SequenceConfiguration(SequenceId("fake"), QuestionId("fake-too"))

      ProposalScorer(proposalWithoutvote.votes, VotesCounter.SequenceVotesCounter, configuration.nonSequenceVotesWeight)
        .pool(configuration, Accepted) should be(SequencePool.New)
    }

  }

  Feature("score V2") {
    Scenario("Scores V2 from expected scores") {

      val precision: Double = 0.000000001

      val expectations = Seq(
        (
          Seq(
            Vote(
              key = Agree,
              count = 85,
              countVerified = 85,
              countSequence = 83,
              countSegment = 0,
              qualifications = Seq(
                Qualification(key = LikeIt, count = 17, countVerified = 17, countSequence = 17, countSegment = 0),
                Qualification(key = Doable, count = 29, countVerified = 29, countSequence = 28, countSegment = 0),
                Qualification(key = PlatitudeAgree, count = 1, countVerified = 1, countSequence = 1, countSegment = 0)
              )
            ),
            Vote(
              key = Disagree,
              count = 6,
              countVerified = 6,
              countSequence = 6,
              countSegment = 0,
              qualifications = Seq(
                Qualification(key = NoWay, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
                Qualification(key = Impossible, count = 1, countVerified = 1, countSequence = 1, countSegment = 0),
                Qualification(
                  key = PlatitudeDisagree,
                  count = 3,
                  countVerified = 3,
                  countSequence = 3,
                  countSegment = 0
                )
              )
            ),
            Vote(
              key = Neutral,
              count = 24,
              countVerified = 24,
              countSequence = 24,
              countSegment = 0,
              qualifications = Seq(
                Qualification(key = DoNotUnderstand, count = 2, countVerified = 2, countSequence = 2, countSegment = 0),
                Qualification(key = NoOpinion, count = 6, countVerified = 6, countSequence = 6, countSegment = 0),
                Qualification(key = DoNotCare, count = 4, countVerified = 4, countSequence = 4, countSegment = 0)
              )
            )
          ),
          ExpectedScores(
            topScore = 1.08527298850575,
            topScoreConfidence = 0.147083961694131,
            rejection = -0.366859497882638,
            rejectionConfidence = 0.130517119469599,
            controversy = 0.0551346037507562,
            controversyConfidence = 0.107091650406154,
            neutral = 0.211581215970962,
            neutralConfidence = 0.0769930776359298
          )
        ),
        (
          Seq(
            Vote(
              key = Agree,
              count = 11,
              countVerified = 11,
              countSequence = 11,
              countSegment = 0,
              qualifications = Seq(
                Qualification(key = LikeIt, count = 4, countVerified = 4, countSequence = 4, countSegment = 0),
                Qualification(key = Doable, count = 2, countVerified = 2, countSequence = 2, countSegment = 0),
                Qualification(key = PlatitudeAgree, count = 2, countVerified = 2, countSequence = 2, countSegment = 0)
              )
            ),
            Vote(
              key = Disagree,
              count = 1,
              countVerified = 1,
              countSequence = 1,
              countSegment = 0,
              qualifications = Seq(
                Qualification(key = NoWay, count = 1, countVerified = 1, countSequence = 1, countSegment = 0),
                Qualification(key = Impossible, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
                Qualification(
                  key = PlatitudeDisagree,
                  count = 0,
                  countVerified = 0,
                  countSequence = 0,
                  countSegment = 0
                )
              )
            ),
            Vote(
              key = Neutral,
              count = 9,
              countVerified = 9,
              countSequence = 9,
              countSegment = 0,
              qualifications = Seq(
                Qualification(key = DoNotUnderstand, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
                Qualification(key = NoOpinion, count = 2, countVerified = 2, countSequence = 2, countSegment = 0),
                Qualification(key = DoNotCare, count = 1, countVerified = 1, countSequence = 1, countSegment = 0)
              )
            )
          ),
          ExpectedScores(
            topScore = 0.650454545454545,
            controversy = 0.106363636363636,
            rejection = -0.258636363636364,
            topScoreConfidence = 0.388908334735123,
            controversyConfidence = 0.261873399227834,
            rejectionConfidence = 0.35981045551905,
            neutral = 0.424090909090909,
            neutralConfidence = 0.198367012853523
          )
        )
      )

      expectations.foreach {
        case (votes, expectedScores) =>
          val votesScorer = ProposalScorer(votes, VotesCounter.SequenceVotesCounter, 0.5)
          votesScorer.topScore.score should be(expectedScores.topScore +- precision)
          votesScorer.topScore.confidence should be(expectedScores.topScoreConfidence +- precision)
          votesScorer.controversy.score should be(expectedScores.controversy +- precision)
          votesScorer.controversy.confidence should be(expectedScores.controversyConfidence +- precision)
          votesScorer.rejection.score should be(expectedScores.rejection +- precision)
          votesScorer.rejection.confidence should be(expectedScores.rejectionConfidence +- precision)
          votesScorer.neutral.score should be(expectedScores.neutral +- precision)
          votesScorer.neutral.confidence should be(expectedScores.neutralConfidence +- precision)
      }
    }
  }
}

object ProposalScorerTest {
  final case class ExpectedScores(
    topScore: Double,
    topScoreConfidence: Double,
    rejection: Double,
    rejectionConfidence: Double,
    controversy: Double,
    controversyConfidence: Double,
    neutral: Double,
    neutralConfidence: Double
  )

}
