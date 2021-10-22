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

package org.make.api.sequence

import eu.timepit.refined.auto._
import org.make.api.MakeUnitTest
import org.make.api.proposal._
import org.make.api.sequence.SelectionAlgorithm.ExplorationSelectionAlgorithm._
import org.make.api.sequence.SelectionAlgorithm.{
  ExplorationSelectionAlgorithm,
  RandomSelectionAlgorithm,
  RoundRobinSelectionAlgorithm
}
import org.make.api.technical.MakeRandom
import org.make.core.DateHelper
import org.make.core.idea.IdeaId
import org.make.core.proposal._
import org.make.core.proposal.indexed.Zone.Consensus
import org.make.core.proposal.indexed._
import org.make.core.question.QuestionId
import org.make.core.sequence._
import org.make.core.user.UserId
import org.scalatest.BeforeAndAfterEach

import java.time.ZonedDateTime

class SelectionAlgorithmTest extends MakeUnitTest with BeforeAndAfterEach {

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    MakeRandom.setSeed(0)
    ProposalScorer.setSeed(0)
  }

  private val roundRobinSequenceConfiguration: SequenceConfiguration = SequenceConfiguration(
    sequenceId = SequenceId("test-sequence-round-robin"),
    questionId = QuestionId("test-question-round-robin"),
    mainSequence = ExplorationSequenceConfiguration.default(ExplorationSequenceConfigurationId("main-id")),
    controversial = SpecificSequenceConfiguration(specificSequenceConfigurationId =
      SpecificSequenceConfigurationId("controversial-id")
    ),
    popular =
      SpecificSequenceConfiguration(specificSequenceConfigurationId = SpecificSequenceConfigurationId("popular-id")),
    keyword =
      SpecificSequenceConfiguration(specificSequenceConfigurationId = SpecificSequenceConfigurationId("keyword-id")),
    newProposalsVoteThreshold = 100,
    testedProposalsEngagementThreshold = Some(0.8),
    testedProposalsScoreThreshold = Some(0.0),
    testedProposalsControversyThreshold = Some(0.0),
    testedProposalsMaxVotesThreshold = Some(1500),
    nonSequenceVotesWeight = 0.5
  )
  private val randomSequenceConfiguration: SequenceConfiguration = SequenceConfiguration(
    sequenceId = SequenceId("test-sequence-random"),
    questionId = QuestionId("test-question-random"),
    mainSequence = ExplorationSequenceConfiguration.default(ExplorationSequenceConfigurationId("main-id")),
    controversial = SpecificSequenceConfiguration(specificSequenceConfigurationId =
      SpecificSequenceConfigurationId("controversial-id")
    ),
    popular =
      SpecificSequenceConfiguration(specificSequenceConfigurationId = SpecificSequenceConfigurationId("popular-id")),
    keyword =
      SpecificSequenceConfiguration(specificSequenceConfigurationId = SpecificSequenceConfigurationId("keyword-id")),
    newProposalsVoteThreshold = 100,
    testedProposalsEngagementThreshold = Some(0.8),
    testedProposalsScoreThreshold = Some(0.0),
    testedProposalsControversyThreshold = Some(0.0),
    testedProposalsMaxVotesThreshold = Some(1500),
    nonSequenceVotesWeight = 0.5
  )

  val roundRobinProposalIds: Seq[ProposalId] =
    (1 to roundRobinSequenceConfiguration.mainSequence.sequenceSize).map(i => ProposalId(s"proposal$i"))

  def fakeProposal(
    id: ProposalId,
    votes: Map[VoteKey, (Int, Map[QualificationKey, Int])],
    sequencePool: SequencePool,
    idea: Option[IdeaId] = None,
    createdAt: ZonedDateTime = DateHelper.now(),
    segment: Option[String] = None
  ): IndexedProposal = {
    indexedProposal(
      id = id,
      userId = UserId(s"fake-$id"),
      createdAt = createdAt,
      updatedAt = None,
      votes = votes.map {
        case (k, (amount, qualifications)) =>
          IndexedVote(
            key = k,
            count = amount,
            countVerified = amount,
            countSequence = amount,
            countSegment = 0,
            qualifications = qualifications.toSeq.map {
              case (key, amount) =>
                IndexedQualification(
                  key = key,
                  count = amount,
                  countSegment = amount,
                  countSequence = amount,
                  countVerified = amount
                )
            }
          )
      }.toSeq,
      questionId = QuestionId("test-question"),
      ideaId = idea,
      startDate = ZonedDateTime.parse("1968-07-03T00:00:00.000Z"),
      endDate = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
      sequencePool = sequencePool,
      sequenceSegmentPool = sequencePool,
      segment = segment
    )
  }

  def fakeProposalQualif(
    id: ProposalId,
    votes: Map[VoteKey, (Int, Map[QualificationKey, Int])],
    sequencePool: SequencePool,
    idea: Option[IdeaId] = None,
    createdAt: ZonedDateTime = DateHelper.now(),
    segment: Option[String] = None,
    boost: Int = 1
  ): IndexedProposal = {
    indexedProposal(
      id = id,
      userId = UserId(s"fake-$id"),
      content = "fake",
      createdAt = createdAt,
      updatedAt = None,
      votes = votes.map {
        case (k, (amount, qualifs)) =>
          IndexedVote(
            key = k,
            count = amount,
            countVerified = if (k == VoteKey.Agree) amount * boost else amount,
            countSequence = amount,
            countSegment = 0,
            qualifications = qualifs.map {
              case (qualifKey, count) =>
                IndexedQualification(
                  key = qualifKey,
                  count = count,
                  countVerified = if (qualifKey == QualificationKey.LikeIt) count * boost else count,
                  countSequence = count,
                  countSegment = 0
                )
            }.toSeq
          )
      }.toSeq,
      questionId = QuestionId("test-question"),
      startDate = ZonedDateTime.parse("1968-07-03T00:00:00.000Z"),
      endDate = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
      ideaId = idea,
      sequencePool = sequencePool,
      sequenceSegmentPool = sequencePool,
      segment = segment
    )
  }

  Feature("round-robin: proposal selection") {
    Scenario("no proposals") {
      val chosen =
        RoundRobinSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = roundRobinSequenceConfiguration.popular,
          includedProposals = Seq.empty,
          newProposals = Seq.empty,
          testedProposals = Seq.empty
        )
      chosen.length should be(0)
    }

    Scenario("with included proposals") {
      val proposals: Seq[IndexedProposal] =
        roundRobinProposalIds.map(id => fakeProposal(id, Map.empty, SequencePool.Tested))

      val selectedProposals =
        RoundRobinSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = roundRobinSequenceConfiguration.popular,
          includedProposals = proposals,
          newProposals = Seq.empty,
          testedProposals = Seq.empty
        )

      selectedProposals.size should be(roundRobinSequenceConfiguration.mainSequence.sequenceSize.value)
      selectedProposals.toSet.size should be(roundRobinSequenceConfiguration.mainSequence.sequenceSize.value)
    }

    Scenario("valid proposal selection") {

      val testedProposals: Seq[IndexedProposal] =
        (1 to 1000).map { i =>
          fakeProposal(
            ProposalId(s"testedProposal$i"),
            Map(VoteKey.Agree -> (i -> Map.empty)),
            SequencePool.Tested,
            None,
            DateHelper.now()
          )
        }
      val selectedProposals =
        RoundRobinSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = roundRobinSequenceConfiguration.popular,
          includedProposals = Seq.empty,
          newProposals = Seq.empty,
          testedProposals = testedProposals
        )

      selectedProposals.size should be(roundRobinSequenceConfiguration.popular.sequenceSize.value)
      selectedProposals.toSet should be(
        testedProposals.take(roundRobinSequenceConfiguration.popular.sequenceSize).toSet
      )
    }
  }

  Feature("random: proposal selection") {
    Scenario("no proposal") {
      val chosen =
        RandomSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = randomSequenceConfiguration.popular,
          includedProposals = Seq.empty,
          newProposals = Seq.empty,
          testedProposals = Seq.empty
        )
      chosen.length should be(0)
    }

    Scenario("included proposals only") {
      val proposals = (1 to 5)
        .map(i => ProposalId(s"included-$i"))
        .map(id => fakeProposal(id, Map.empty, SequencePool.Tested))

      val selectedProposals =
        RandomSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = randomSequenceConfiguration.popular,
          includedProposals = proposals,
          newProposals = Seq.empty,
          testedProposals = Seq.empty
        )

      selectedProposals should be(proposals)
    }

    Scenario("with everything") {

      val newProposals = (1 to 20)
        .map(i => ProposalId(s"new-proposal-$i"))
        .map(id => fakeProposal(id, Map.empty, SequencePool.Tested))

      val proposals = (1 to 200)
        .map(i => ProposalId(s"proposal-$i"))
        .map(id => fakeProposal(id, Map.empty, SequencePool.Tested))

      (1 to 200).foreach { _ =>
        val included = MakeRandom.shuffleSeq(proposals).take(3)
        val selectedProposals =
          RandomSelectionAlgorithm.selectProposalsForSequence(
            sequenceConfiguration = randomSequenceConfiguration.popular,
            includedProposals = included,
            newProposals = newProposals,
            testedProposals = proposals
          )

        selectedProposals.size should be(randomSequenceConfiguration.popular.sequenceSize.value)
        selectedProposals.take(included.size) should be(included)
      }
    }
  }

  Feature("exploration: equalizer") {
    Scenario("no proposal to choose") {
      TestedProposalsChooser.choose(Seq.empty, 42, Set.empty, 5) should be(Seq.empty)
    }

    Scenario("no conflict between proposals") {
      val ids = 1 to 100
      val proposals = ids.map { i =>
        indexedProposal(id = ProposalId(i.toString), userId = UserId(i.toString))
      }
      TestedProposalsChooser.choose(proposals, 10, Set.empty, 5).map(_.id.value.toInt) should be(
        Seq(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
      )
    }

    Scenario("deduplication by author") {
      val ids = 1 to 100
      val proposals = ids.map { i =>
        indexedProposal(id = ProposalId(i.toString), userId = UserId((i % 5).toString))
      }
      TestedProposalsChooser.choose(proposals, 10, Set.empty, 5).map(_.id.value.toInt) should be(Seq(1, 2, 3, 4, 5))
    }

    Scenario("deduplication by keyword") {
      val ids = 1 to 100

      val proposals = ids.map { i =>
        val first = (i  % 5).toString
        val second = (i % 7).toString
        val keyWords = Seq(
          IndexedProposalKeyword(ProposalKeywordKey(first), first),
          IndexedProposalKeyword(ProposalKeywordKey(second), second)
        )
        indexedProposal(id = ProposalId(i.toString), userId = UserId(i.toString), keywords = keyWords)
      }
      TestedProposalsChooser.choose(proposals, 10, Set.empty, 5).map(_.id.value.toInt) should be(Seq(1, 2, 3, 4, 5))
    }

    Scenario("deduplication by keyword with ignored keywords") {
      val ids = 1 to 100
      val ignored = ProposalKeywordKey("ignored")

      val proposals = ids.map { i =>
        val first = (i  % 5).toString
        val second = (i % 7).toString
        val keyWords = Seq(
          IndexedProposalKeyword(ignored, ignored.value),
          IndexedProposalKeyword(ProposalKeywordKey(first), first),
          IndexedProposalKeyword(ProposalKeywordKey(second), second)
        )
        indexedProposal(id = ProposalId(i.toString), userId = UserId(i.toString), keywords = keyWords)
      }
      TestedProposalsChooser.choose(proposals, 10, Set(ignored), 5).map(_.id.value.toInt) should be(Seq(1, 2, 3, 4, 5))
    }

    Scenario("equalizing proposals") {

      val proposals = Seq(
        indexedProposal(id = ProposalId("1"), userId = UserId("1")).copy(votesSequenceCount = 10),
        indexedProposal(id = ProposalId("2"), userId = UserId("1")).copy(votesSequenceCount = 9),
        indexedProposal(id = ProposalId("3"), userId = UserId("1")).copy(votesSequenceCount = 10),
        indexedProposal(id = ProposalId("4"), userId = UserId("1")).copy(votesSequenceCount = 10),
        indexedProposal(id = ProposalId("5"), userId = UserId("1")).copy(votesSequenceCount = 10),
        indexedProposal(id = ProposalId("6"), userId = UserId("1")).copy(votesSequenceCount = 8),
        indexedProposal(id = ProposalId("7"), userId = UserId("1")).copy(votesSequenceCount = 10),
        indexedProposal(id = ProposalId("8"), userId = UserId("2")).copy(votesSequenceCount = 10),
        indexedProposal(id = ProposalId("9"), userId = UserId("3")).copy(votesSequenceCount = 9)
      )

      TestedProposalsChooser.choose(proposals, 10, Set.empty, 10).map(_.id.value.toInt) should be(Seq(6, 9, 8))
    }
  }

  Feature("resolve keywords to ignore") {
    Scenario("empty list") {
      ExplorationSelectionAlgorithm.resolveIgnoredKeywords(Seq.empty, 0.5) should be(Set.empty)
    }

    Scenario("all excluded") {
      val proposals = Seq(
        indexedProposal(
          ProposalId(""),
          keywords = Seq(IndexedProposalKeyword(ProposalKeywordKey("ignored"), "ignored"))
        )
      )
      ExplorationSelectionAlgorithm.resolveIgnoredKeywords(proposals, 0.5) should be(Set(ProposalKeywordKey("ignored")))
    }

    Scenario("some excluded") {
      val proposals = Seq(
        indexedProposal(
          ProposalId("1"),
          keywords = Seq(IndexedProposalKeyword(ProposalKeywordKey("ignored"), "ignored"))
        ),
        indexedProposal(
          ProposalId("2"),
          keywords = Seq(IndexedProposalKeyword(ProposalKeywordKey("ignored"), "ignored"))
        ),
        indexedProposal(
          ProposalId("2"),
          keywords = Seq(IndexedProposalKeyword(ProposalKeywordKey("whatever"), "whatever"))
        ),
        indexedProposal(ProposalId("3"), keywords = Seq(IndexedProposalKeyword(ProposalKeywordKey("other"), "other")))
      )
      ExplorationSelectionAlgorithm.resolveIgnoredKeywords(proposals, 0.5) should be(Set(ProposalKeywordKey("ignored")))
    }

  }

  Feature("exploration: choosing new proposals") {
    Scenario("no proposal in pool") {
      NewProposalsChooser.choose(Seq.empty, 10) should be(Seq.empty)
    }

    Scenario("chose and deduplicate by author") {
      val now = DateHelper.now()
      val proposals = Seq(
        indexedProposal(id = ProposalId("1"), createdAt = now, userId = UserId("1")),
        indexedProposal(id = ProposalId("2"), createdAt = now.minusDays(2), userId = UserId("1")),
        indexedProposal(id = ProposalId("3"), createdAt = now.minusDays(1), userId = UserId("1")),
        indexedProposal(id = ProposalId("4"), createdAt = now.minusDays(1), userId = UserId("2")),
        indexedProposal(id = ProposalId("5"), createdAt = now.minusDays(5), userId = UserId("3"))
      )

      NewProposalsChooser.choose(proposals, 10).map(_.id.value.toInt) should be(Seq(5, 2, 4))
    }
  }

  Feature("exploration: sorting proposals") {
    val proposals = Seq(
      ZonedProposal(indexedProposal(ProposalId("1")).copy(votesSequenceCount = 1), Consensus, 0.1),
      ZonedProposal(indexedProposal(ProposalId("2")).copy(votesSequenceCount = 2), Consensus, 0.2),
      ZonedProposal(indexedProposal(ProposalId("3")).copy(votesSequenceCount = 4), Consensus, 0.4),
      ZonedProposal(indexedProposal(ProposalId("4")).copy(votesSequenceCount = 3), Consensus, 0.3),
      ZonedProposal(indexedProposal(ProposalId("5")).copy(votesSequenceCount = 6), Consensus, 0.6),
      ZonedProposal(indexedProposal(ProposalId("6")).copy(votesSequenceCount = 5), Consensus, 0.5)
    )

    Scenario("Bandit sorting") {
      BanditSorter.sort(proposals).map(_.id.value.toInt) should be(Seq(5, 6, 3, 4, 2, 1))
    }
    Scenario("random sorting") {
      RandomSorter.sort(proposals) should not be (RandomSorter.sort(proposals))
    }
    Scenario("RoundRobin sorting") {
      EqualizerSorter.sort(proposals).map(_.id.value.toInt) should be(Seq(1, 2, 4, 3, 6, 5))
    }
  }
}
