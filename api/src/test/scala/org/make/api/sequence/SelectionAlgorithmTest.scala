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

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import cats.data.NonEmptyList
import org.make.api.MakeUnitTest
import org.make.api.proposal.ProposalScorerHelper.ScoreCounts
import org.make.api.proposal._
import org.make.api.proposal.DefaultSelectionAlgorithmComponent.Scored
import org.make.api.technical.MakeRandom
import org.make.core.idea.IdeaId
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.user.{UserId, UserType}
import org.make.core.DateHelper

import Ordering.Double.TotalOrdering
import scala.collection.mutable

class SelectionAlgorithmTest extends MakeUnitTest with DefaultSelectionAlgorithmComponent {

  MakeRandom.setSeed(0)
  ProposalScorerHelper.setSeed(0)

  val banditSequenceConfiguration: SequenceConfiguration = SequenceConfiguration(
    sequenceId = SequenceId("test-sequence"),
    questionId = QuestionId("test-question"),
    newProposalsRatio = 0.5,
    newProposalsVoteThreshold = 100,
    testedProposalsEngagementThreshold = Some(0.8),
    testedProposalsScoreThreshold = Some(0.0),
    testedProposalsControversyThreshold = Some(0.0),
    intraIdeaMinCount = 3,
    intraIdeaProposalsRatio = 1.0 / 3.0,
    interIdeaCompetitionEnabled = false,
    selectionAlgorithmName = SelectionAlgorithmName.Bandit
  )

  val roundRobinSequenceConfiguration: SequenceConfiguration = SequenceConfiguration(
    sequenceId = SequenceId("test-sequence-round-robin"),
    questionId = QuestionId("test-question-round-robin"),
    sequenceSize = 20,
    newProposalsRatio = 1.0,
    newProposalsVoteThreshold = 100,
    testedProposalsEngagementThreshold = Some(0.8),
    testedProposalsScoreThreshold = Some(0.0),
    testedProposalsControversyThreshold = Some(0.0),
    intraIdeaEnabled = false,
    intraIdeaMinCount = 0,
    interIdeaCompetitionEnabled = false,
    selectionAlgorithmName = SelectionAlgorithmName.RoundRobin
  )

  val banditProposalIds: Seq[ProposalId] =
    (1 to banditSequenceConfiguration.sequenceSize).map(i => ProposalId(s"proposal$i"))
  val roundRobinProposalIds: Seq[ProposalId] =
    (1 to roundRobinSequenceConfiguration.sequenceSize).map(i => ProposalId(s"proposal$i"))

  def fakeProposal(
    id: ProposalId,
    votes: Map[VoteKey, Int],
    sequencePool: SequencePool,
    idea: Option[IdeaId] = None,
    createdAt: ZonedDateTime = DateHelper.now(),
    segment: Option[String] = None
  ): IndexedProposal = {
    IndexedProposal(
      id = id,
      userId = UserId(s"fake-$id"),
      content = "fake",
      slug = "fake",
      status = ProposalStatus.Accepted,
      createdAt = createdAt,
      updatedAt = None,
      votes = votes.map {
        case (k, amount) =>
          IndexedVote(
            key = k,
            count = amount,
            countVerified = amount,
            countSequence = amount,
            countSegment = 0,
            qualifications = Seq.empty
          )
      }.toSeq,
      votesCount = votes.values.sum,
      votesVerifiedCount = votes.values.sum,
      votesSequenceCount = votes.values.sum,
      votesSegmentCount = votes.values.sum,
      toEnrich = false,
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = None,
      trending = None,
      labels = Seq.empty,
      author = IndexedAuthor(
        firstName = None,
        displayName = None,
        organisationName = None,
        organisationSlug = None,
        postalCode = None,
        age = None,
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      question = Some(
        IndexedProposalQuestion(
          questionId = QuestionId("test-question"),
          slug = "test-question",
          title = "test question",
          question = "test question ?",
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          startDate = None,
          endDate = None,
          isOpen = true
        )
      ),
      tags = Seq.empty,
      selectedStakeTag = None,
      ideaId = idea,
      operationId = None,
      sequencePool = sequencePool,
      sequenceSegmentPool = sequencePool,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
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
    IndexedProposal(
      id = id,
      userId = UserId(s"fake-$id"),
      content = "fake",
      slug = "fake",
      status = ProposalStatus.Accepted,
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
      votesCount = votes.values.map(_._1).sum,
      votesVerifiedCount = votes.values.map(_._1).sum,
      votesSequenceCount = votes.values.map(_._1).sum,
      votesSegmentCount = votes.values.map(_._1).sum,
      toEnrich = false,
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = None,
      trending = None,
      labels = Seq.empty,
      author = IndexedAuthor(
        firstName = None,
        displayName = None,
        organisationName = None,
        organisationSlug = None,
        postalCode = None,
        age = None,
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      question = Some(
        IndexedProposalQuestion(
          questionId = QuestionId("test-question"),
          slug = "test-question",
          title = "test question",
          question = "test question ?",
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          startDate = None,
          endDate = None,
          isOpen = true
        )
      ),
      tags = Seq.empty,
      selectedStakeTag = None,
      ideaId = idea,
      operationId = None,
      sequencePool = sequencePool,
      sequenceSegmentPool = sequencePool,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = segment
    )
  }

  Feature("bandit: proposal selection algorithm with new proposals") {
    Scenario("no duplicates with enough proposals only new proposals") {

      val proposals: Seq[IndexedProposal] =
        banditProposalIds.map(id => fakeProposal(id, Map.empty, SequencePool.Tested))

      val selectedProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration,
          includedProposals = proposals,
          newProposals = Seq.empty,
          testedProposals = Seq.empty,
          votedProposals = Seq.empty,
          userSegment = None
        )

      selectedProposals.size should be(banditSequenceConfiguration.sequenceSize)
      selectedProposals.toSet.size should be(banditSequenceConfiguration.sequenceSize)
    }

    Scenario("no duplicates with enough proposals and include list only new proposals") {

      val included = Seq(
        fakeProposal(ProposalId("Included 1"), Map.empty, SequencePool.Tested),
        fakeProposal(ProposalId("Included 2"), Map.empty, SequencePool.Tested),
        fakeProposal(ProposalId("Included 3"), Map.empty, SequencePool.Tested)
      )

      val proposals: Seq[IndexedProposal] =
        banditProposalIds.map(
          id => fakeProposal(id, Map.empty, SequencePool.Tested, Some(IdeaId(MakeRandom.nextString(5))))
        )

      val sequenceProposals = banditSelectionAlgorithm.selectProposalsForSequence(
        sequenceConfiguration = banditSequenceConfiguration,
        includedProposals = included,
        newProposals = Seq.empty,
        testedProposals = proposals,
        votedProposals = Seq.empty,
        userSegment = None
      )

      sequenceProposals.size should be(banditSequenceConfiguration.sequenceSize)
      sequenceProposals.toSet.size should be(banditSequenceConfiguration.sequenceSize)
      sequenceProposals.map(_.id).contains(ProposalId("Included 1")) should be(true)
      sequenceProposals.map(_.id).contains(ProposalId("Included 2")) should be(true)
      sequenceProposals.map(_.id).contains(ProposalId("Included 3")) should be(true)

    }

    Scenario("duplicates with enough proposals only new proposals") {

      val duplicates: Map[ProposalId, IdeaId] =
        Map(ProposalId("proposal1") -> IdeaId("TestIdea"), ProposalId("proposal2") -> IdeaId("TestIdea"))

      val testedProposals: Seq[IndexedProposal] =
        banditProposalIds.map(id => fakeProposal(id, Map.empty, SequencePool.Tested, duplicates.get(id)))

      val sequenceProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration,
          includedProposals = Seq.empty,
          newProposals = Seq.empty,
          testedProposals = testedProposals,
          votedProposals = Seq.empty,
          userSegment = None
        )

      sequenceProposals.size should be(banditSequenceConfiguration.sequenceSize - 1)
      sequenceProposals.toSet.size should be(banditSequenceConfiguration.sequenceSize - 1)
      if (sequenceProposals.map(_.id).contains(ProposalId("proposal1"))) {
        sequenceProposals.map(_.id).contains(ProposalId("proposal2")) should be(false)
      } else {
        sequenceProposals.map(_.id).contains(ProposalId("proposal2")) should be(true)
      }
    }

    Scenario("duplicates from include list with enough proposals only new proposals") {

      val duplicates: Map[ProposalId, IdeaId] =
        Map(ProposalId("proposal1") -> IdeaId("TestIdea"), ProposalId("included1") -> IdeaId("TestIdea"))

      val proposals: Seq[IndexedProposal] =
        banditProposalIds.map(id => fakeProposal(id, Map.empty, SequencePool.Tested, duplicates.get(id)))

      val included =
        Seq(
          fakeProposal(ProposalId("included1"), Map.empty, SequencePool.Tested, duplicates.get(ProposalId("included1")))
        )

      val sequenceProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration,
          includedProposals = included,
          newProposals = Seq.empty,
          testedProposals = proposals,
          votedProposals = Seq.empty,
          userSegment = None
        )

      sequenceProposals.size should be(banditSequenceConfiguration.sequenceSize)
      sequenceProposals.size should be(banditSequenceConfiguration.sequenceSize)
      sequenceProposals.map(_.id).contains(ProposalId("included1")) should be(true)
      sequenceProposals.map(_.id).contains(ProposalId("proposal1")) should be(false)
    }
  }

  Feature("bandit: proposal selection algorithm with tested proposals") {

    Scenario("no duplicates with enough proposals only tested proposals") {

      val testedProposals: Seq[IndexedProposal] =
        banditProposalIds.map(id => fakeProposal(id, Map(VoteKey.Agree -> 200), SequencePool.Tested))

      val selectedProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration,
          includedProposals = Seq.empty,
          newProposals = Seq.empty,
          testedProposals = testedProposals,
          votedProposals = Seq.empty,
          userSegment = None
        )

      selectedProposals.size should be(banditSequenceConfiguration.sequenceSize)
      selectedProposals.toSet.size should be(banditSequenceConfiguration.sequenceSize)
    }

    Scenario("no duplicates with only already voted tested proposals") {

      val testedProposals: Seq[IndexedProposal] =
        banditProposalIds.map(id => fakeProposal(id, Map.empty, SequencePool.Tested))

      val selectedProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration,
          includedProposals = Seq.empty,
          newProposals = Seq.empty,
          testedProposals = testedProposals,
          votedProposals = banditProposalIds,
          userSegment = None
        )

      selectedProposals.size should be(0)
    }

    Scenario("no duplicates with only already voted tested proposals without bandit") {

      val testedProposals: Seq[IndexedProposal] =
        banditProposalIds.map(id => fakeProposal(id, Map.empty, SequencePool.Tested))

      val selectedProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration.copy(intraIdeaEnabled = false),
          includedProposals = Seq.empty,
          newProposals = Seq.empty,
          testedProposals = testedProposals,
          votedProposals = banditProposalIds,
          userSegment = None
        )

      selectedProposals.size should be(0)
    }

    Scenario("no duplicates with only already voted new proposals") {

      val newProposals: Seq[IndexedProposal] =
        banditProposalIds.map(id => fakeProposal(id, Map.empty, SequencePool.Tested))

      val selectedProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration,
          includedProposals = Seq.empty,
          newProposals = newProposals,
          testedProposals = Seq.empty,
          votedProposals = banditProposalIds,
          userSegment = None
        )

      selectedProposals.size should be(0)
    }

    Scenario("no duplicates with only already voted new proposals without bandit") {

      val newProposals: Seq[IndexedProposal] =
        banditProposalIds.map(id => fakeProposal(id, Map.empty, SequencePool.Tested))

      val selectedProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration.copy(intraIdeaEnabled = false),
          includedProposals = Seq.empty,
          newProposals = newProposals,
          testedProposals = Seq.empty,
          votedProposals = banditProposalIds,
          userSegment = None
        )

      selectedProposals.size should be(0)
    }

    Scenario("no duplicates with enough proposals and include list only tested proposals") {

      val proposals: Seq[IndexedProposal] =
        banditProposalIds.map(id => fakeProposal(id, Map(VoteKey.Agree -> 200), SequencePool.Tested))

      val included = Seq(
        fakeProposal(ProposalId("Included 1"), Map.empty, SequencePool.Tested),
        fakeProposal(ProposalId("Included 2"), Map.empty, SequencePool.Tested),
        fakeProposal(ProposalId("Included 3"), Map.empty, SequencePool.Tested)
      )

      val sequenceProposals = banditSelectionAlgorithm.selectProposalsForSequence(
        sequenceConfiguration = banditSequenceConfiguration,
        includedProposals = included,
        newProposals = Seq.empty,
        testedProposals = proposals,
        votedProposals = Seq.empty,
        userSegment = None
      )

      sequenceProposals.size should be(banditSequenceConfiguration.sequenceSize)
      sequenceProposals.toSet.size should be(banditSequenceConfiguration.sequenceSize)
      sequenceProposals.map(_.id).contains(ProposalId("Included 1")) should be(true)
      sequenceProposals.map(_.id).contains(ProposalId("Included 2")) should be(true)
      sequenceProposals.map(_.id).contains(ProposalId("Included 3")) should be(true)

    }

    Scenario("duplicates with enough proposals only tested proposals") {

      val duplicates =
        Map(ProposalId("proposal1") -> IdeaId("TestId"), ProposalId("proposal2") -> IdeaId("TestId"))

      val proposals: Seq[IndexedProposal] =
        banditProposalIds.map(
          id => fakeProposal(id, Map(VoteKey.Agree -> 200), SequencePool.Tested, duplicates.get(id))
        )

      val sequenceProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration,
          includedProposals = Seq.empty,
          newProposals = Seq.empty,
          testedProposals = proposals,
          votedProposals = Seq.empty,
          userSegment = None
        )

      sequenceProposals.size should be(banditSequenceConfiguration.sequenceSize - 1)
      sequenceProposals.toSet.size should be(banditSequenceConfiguration.sequenceSize - 1)
      if (sequenceProposals.map(_.id).contains(ProposalId("proposal1"))) {
        sequenceProposals.map(_.id).contains(ProposalId("proposal2")) should be(false)
      } else {
        sequenceProposals.map(_.id).contains(ProposalId("proposal2")) should be(true)
      }
    }

    Scenario("duplicates from include list with enough proposals only tested proposals") {

      val duplicates =
        Map(ProposalId("proposal1") -> IdeaId("TestId"), ProposalId("included1") -> IdeaId("TestId"))

      val proposals: Seq[IndexedProposal] =
        banditProposalIds.map(
          id => fakeProposal(id, Map(VoteKey.Agree -> 200), SequencePool.Tested, duplicates.get(id))
        )

      val included =
        Seq(
          fakeProposal(ProposalId("included1"), Map.empty, SequencePool.Tested, duplicates.get(ProposalId("included1")))
        )

      val sequenceProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration,
          includedProposals = included,
          newProposals = Seq.empty,
          testedProposals = proposals,
          votedProposals = Seq.empty,
          userSegment = None
        )

      sequenceProposals.size should be(banditSequenceConfiguration.sequenceSize)
      sequenceProposals.size should be(banditSequenceConfiguration.sequenceSize)
      sequenceProposals.map(_.id).contains(ProposalId("included1")) should be(true)
      sequenceProposals.map(_.id).contains(ProposalId("proposal1")) should be(false)
    }

  }

  Feature("bandit: proposal selection algorithm with mixed new and tested proposals") {

    Scenario("no duplicates with enough proposals") {

      val newProposals: Seq[IndexedProposal] =
        banditProposalIds
          .take(banditProposalIds.size)
          .map(id => fakeProposal(id, Map(VoteKey.Agree -> 50), SequencePool.New))

      val testedProposals: Seq[IndexedProposal] =
        banditProposalIds
          .takeRight(banditProposalIds.size)
          .map(id => fakeProposal(id, Map(VoteKey.Agree -> 200), SequencePool.Tested))

      val selectedProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration,
          includedProposals = Seq.empty,
          newProposals = newProposals,
          testedProposals = testedProposals,
          votedProposals = Seq.empty,
          userSegment = None
        )

      selectedProposals.size should be(banditSequenceConfiguration.sequenceSize)
      selectedProposals.toSet.size should be(banditSequenceConfiguration.sequenceSize)
    }

    Scenario("no duplicates with enough proposals and include list") {

      val newProposals: Seq[IndexedProposal] =
        banditProposalIds
          .take(banditProposalIds.size)
          .map(id => fakeProposal(id, Map(VoteKey.Agree -> 50), SequencePool.New))

      val testedProposals: Seq[IndexedProposal] =
        banditProposalIds
          .takeRight(banditProposalIds.size)
          .map(id => fakeProposal(id, Map(VoteKey.Agree -> 200), SequencePool.Tested))

      val included = Seq(
        fakeProposal(ProposalId("Included 1"), Map.empty, SequencePool.Tested),
        fakeProposal(ProposalId("Included 2"), Map.empty, SequencePool.Tested),
        fakeProposal(ProposalId("Included 3"), Map.empty, SequencePool.Tested)
      )

      val sequenceProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration,
          includedProposals = included,
          newProposals = newProposals,
          testedProposals = testedProposals,
          votedProposals = Seq.empty,
          userSegment = None
        )

      sequenceProposals.size should be(banditSequenceConfiguration.sequenceSize)
      sequenceProposals.toSet.size should be(banditSequenceConfiguration.sequenceSize)
      sequenceProposals.map(_.id).contains(ProposalId("Included 1")) should be(true)
      sequenceProposals.map(_.id).contains(ProposalId("Included 2")) should be(true)
      sequenceProposals.map(_.id).contains(ProposalId("Included 3")) should be(true)

    }

    Scenario("duplicates with enough proposals") {

      val duplicates =
        Map(ProposalId("proposal1") -> IdeaId("TestIdea"), ProposalId("proposal2") -> IdeaId("TestIdea"))

      val newProposals: Seq[IndexedProposal] =
        banditProposalIds
          .take(banditProposalIds.size)
          .map(id => fakeProposal(id, Map(VoteKey.Agree -> 50), SequencePool.New, duplicates.get(id)))

      val testedProposals: Seq[IndexedProposal] =
        banditProposalIds
          .takeRight(banditProposalIds.size)
          .map(id => fakeProposal(id, Map(VoteKey.Agree -> 200), SequencePool.Tested, duplicates.get(id)))

      val sequenceProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration,
          includedProposals = Seq.empty,
          newProposals = newProposals,
          testedProposals = testedProposals,
          votedProposals = Seq.empty,
          userSegment = None
        )

      sequenceProposals.size should be(banditSequenceConfiguration.sequenceSize - 1)
      sequenceProposals.toSet.size should be(banditSequenceConfiguration.sequenceSize - 1)
      if (sequenceProposals.map(_.id).contains(ProposalId("proposal1"))) {
        sequenceProposals.map(_.id).contains(ProposalId("proposal2")) should be(false)
      } else {
        sequenceProposals.map(_.id).contains(ProposalId("proposal2")) should be(true)
      }
    }

    Scenario("duplicates from include list with enough proposals") {

      val duplicates =
        Map(ProposalId("proposal1") -> IdeaId("TestIdea"), ProposalId("included1") -> IdeaId("TestIdea"))

      val included =
        Seq(
          fakeProposal(ProposalId("included1"), Map.empty, SequencePool.Tested, duplicates.get(ProposalId("included1")))
        )

      val newProposals: Seq[IndexedProposal] =
        banditProposalIds
          .take(banditProposalIds.size)
          .map(id => fakeProposal(id, Map(VoteKey.Agree -> 50), SequencePool.New, duplicates.get(id)))

      val testedProposals: Seq[IndexedProposal] =
        banditProposalIds
          .takeRight(banditProposalIds.size)
          .map(id => fakeProposal(id, Map(VoteKey.Agree -> 200), SequencePool.Tested, duplicates.get(id)))

      val sequenceProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration,
          includedProposals = included,
          newProposals = newProposals,
          testedProposals = testedProposals,
          votedProposals = Seq.empty,
          userSegment = None
        )

      sequenceProposals.size should be(banditSequenceConfiguration.sequenceSize)
      sequenceProposals.size should be(banditSequenceConfiguration.sequenceSize)
      sequenceProposals.map(_.id).contains(ProposalId("included1")) should be(true)
      sequenceProposals.map(_.id).contains(ProposalId("proposal1")) should be(false)
    }

    Scenario("check first in first out behavior") {

      val newProposalIds: Seq[ProposalId] =
        (1 to banditSequenceConfiguration.sequenceSize).map(i => ProposalId(s"newProposal$i"))
      val testedProposalIds: Seq[ProposalId] =
        (1 to banditSequenceConfiguration.sequenceSize).map(i => ProposalId(s"testedProposal$i"))

      val newProposals: Seq[IndexedProposal] =
        newProposalIds.zipWithIndex.map {
          case (id, i) =>
            fakeProposal(
              id,
              Map(VoteKey.Agree -> 0),
              SequencePool.New,
              createdAt = ZonedDateTime.parse("2017-12-07T16:00:00Z").plus(i, ChronoUnit.MINUTES)
            )
        }

      val newProposalsRandom = MakeRandom.shuffleSeq(newProposals)

      val testedProposals: Seq[IndexedProposal] =
        testedProposalIds.map(id => fakeProposal(id, Map(VoteKey.Agree -> 200), SequencePool.Tested))

      val sequenceProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration,
          includedProposals = Seq.empty,
          newProposals = newProposalsRandom,
          testedProposals = testedProposals,
          votedProposals = Seq.empty,
          userSegment = None
        )

      sequenceProposals.size should be(banditSequenceConfiguration.sequenceSize)
      sequenceProposals.map(_.id).contains(ProposalId("newProposal1")) should be(true)
      sequenceProposals.map(_.id).contains(ProposalId("newProposal2")) should be(true)
      sequenceProposals.map(_.id).contains(ProposalId("newProposal3")) should be(true)
      sequenceProposals.map(_.id).contains(ProposalId("newProposal4")) should be(true)
      sequenceProposals.map(_.id).contains(ProposalId("newProposal5")) should be(true)
      sequenceProposals.map(_.id).contains(ProposalId("newProposal6")) should be(true)
      sequenceProposals.map(_.id).contains(ProposalId("newProposal7")) should be(false)
      sequenceProposals.map(_.id).contains(ProposalId("newProposal8")) should be(false)
      sequenceProposals.map(_.id).contains(ProposalId("newProposal9")) should be(false)
      sequenceProposals.map(_.id).contains(ProposalId("newProposal10")) should be(false)
      sequenceProposals.map(_.id).contains(ProposalId("newProposal11")) should be(false)
      sequenceProposals.map(_.id).contains(ProposalId("newProposal12")) should be(false)
    }

    Scenario("most engaging first") {

      val newProposalIds: Seq[ProposalId] =
        (1 to banditSequenceConfiguration.sequenceSize / 2).map(i => ProposalId(s"newProposal$i"))
      val testedProposalIds: Seq[ProposalId] =
        (1 to banditSequenceConfiguration.sequenceSize / 2).map(i => ProposalId(s"testedProposal$i"))

      val newProposals: Seq[IndexedProposal] =
        newProposalIds.map(id => fakeProposal(id, Map(VoteKey.Agree -> 0), SequencePool.New))

      val testedProposals: Seq[IndexedProposal] =
        testedProposalIds.zipWithIndex.map {
          case (id, i) =>
            fakeProposal(
              id,
              Map(VoteKey.Agree -> (800 + i * 10), VoteKey.Neutral -> (200 - i * 20)),
              SequencePool.Tested
            )
        }

      val testedProposalsRandom = MakeRandom.shuffleSeq(testedProposals)

      val sequenceProposals =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = banditSequenceConfiguration,
          includedProposals = Seq.empty,
          newProposals = newProposals,
          testedProposals = testedProposalsRandom,
          votedProposals = Seq.empty,
          userSegment = None
        )

      sequenceProposals.size should be(12)
      sequenceProposals.head.id should be(ProposalId("testedProposal6"))
    }
  }

  Feature("bandit: allocate votes using soft min") {
    Scenario("power of 2 vote counts") {
      val testedProposals: Seq[IndexedProposal] = (1 to 10).map { i =>
        fakeProposal(
          ProposalId(s"testedProposal$i"),
          Map(VoteKey.Agree -> Math.pow(2, i).toInt),
          SequencePool.Tested,
          None,
          DateHelper.now()
        )
      }

      val counts = new mutable.HashMap[ProposalId, Int]().withDefault(_ => 0)

      val samples = 10000
      for (_ <- 1 to samples) {
        banditSelectionAlgorithm
          .chooseProposals(proposals = testedProposals, count = 1, algorithm = SoftMinRandom)
          .foreach(p => counts(p.id) += 1)
      }

      val proportions: mutable.Map[ProposalId, Double] = counts.map {
        case (i, p) => (i, p.toDouble / samples)
      }

      proportions.size should be(3)
      val confidenceInterval: Double = 0.016
      proportions(testedProposals.head.id) should equal(0.88 +- confidenceInterval)
      proportions(testedProposals(1).id) should equal(0.12 +- confidenceInterval)
      proportions(testedProposals(2).id) should equal(0.01 +- confidenceInterval)
    }
  }

  Feature("bandit: allocate vote with bandit algorithm within idea") {
    Scenario("check proposal scorer") {
      val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
        VoteKey.Agree -> (50 -> Map(QualificationKey.LikeIt -> 20, QualificationKey.PlatitudeAgree -> 10)),
        VoteKey.Disagree -> (20 -> Map(QualificationKey.NoWay -> 10, QualificationKey.PlatitudeDisagree -> 5)),
        VoteKey.Neutral -> (30 -> Map(QualificationKey.DoNotCare -> 10))
      )

      val testProposal = fakeProposalQualif(ProposalId("tested"), votes, SequencePool.Tested)
      val counts = ScoreCounts.fromSequenceVotes(testProposal.votes)
      val testProposalScore = counts.topScore()

      val trials = 1000
      val samples =
        (1 to trials).map(_ => counts.sampleTopScore())

      testProposal.votes.map(_.countVerified).sum should be(100)
      samples.max should be > testProposalScore + 0.1
      samples.min should be < testProposalScore - 0.1
      samples.sum / trials should be(testProposalScore +- 0.05)
    }

    Scenario("check proposal scorer with pathological proposal") {
      val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
        VoteKey.Agree -> (0 -> Map.empty),
        VoteKey.Disagree -> (0 -> Map.empty),
        VoteKey.Neutral -> (0 -> Map.empty)
      )

      val testProposal: IndexedProposal =
        fakeProposalQualif(ProposalId("tested"), votes, SequencePool.Tested)

      val scores = ScoreCounts.fromSequenceVotes(testProposal.votes)

      val testProposalScore: Double = scores.topScore()
      val testProposalScoreSample: Double = scores.sampleTopScore()

      testProposalScore should be > -10.0
      testProposalScoreSample should be > -10.0
    }

    Scenario("check bandit chooser for similars") {
      val testedProposals: Seq[IndexedProposal] = (1 to 20).map { i =>
        val a = MakeRandom.nextInt(100) + 1
        val d = MakeRandom.nextInt(100) + 1
        val n = MakeRandom.nextInt(100) + 1
        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            a ->
              Map(
                QualificationKey.LikeIt -> MakeRandom.nextInt(a),
                QualificationKey.PlatitudeAgree -> MakeRandom.nextInt(a)
              )
          ),
          VoteKey.Disagree -> (
            d ->
              Map(
                QualificationKey.NoWay -> MakeRandom.nextInt(d),
                QualificationKey.PlatitudeDisagree -> MakeRandom.nextInt(d)
              )
          ),
          VoteKey.Neutral -> (n -> Map(QualificationKey.DoNotCare -> MakeRandom.nextInt(n)))
        )
        fakeProposalQualif(ProposalId(s"tested$i"), votes, SequencePool.Tested)
      }

      val sortedProposals: Seq[ProposalId] = testedProposals
        .map(p => Scored(p, ScoreCounts.fromSequenceVotes(p.votes).sampleTopScore()))
        .sortWith(_.score > _.score)
        .map(sp => sp.item.id)

      val chosenCounts: Seq[ProposalId] =
        (1 to 10000)
          .map(
            _ =>
              banditSelectionAlgorithm.chooseProposalBandit(banditSequenceConfiguration, testedProposals, None).id -> 1
          )
          .groupBy {
            case (proposalId, _) => proposalId
          }
          .map {
            case (proposalId, scores) =>
              proposalId -> scores.foldLeft(0) {
                case (current, (_, next)) => current + next
              }
          }
          .toSeq
          .sortWith {
            case ((_, order1), (_, order2)) => order1 > order2
          }
          .map {
            case (proposal, _) => proposal
          }

      chosenCounts.slice(0, 3).contains(sortedProposals.head) should be(true)
      chosenCounts.slice(0, 10).contains(sortedProposals(1)) should be(true)
      chosenCounts.slice(0, 10).contains(sortedProposals(2)) should be(true)
      chosenCounts.slice(0, 10).contains(sortedProposals(19)) should be(false)
    }

    Scenario("check tested proposal chooser") {
      val testedProposals: Map[IdeaId, Seq[IndexedProposal]] = (1 to 100).map { i =>
        val a = MakeRandom.nextInt(100) + 100
        val d = MakeRandom.nextInt(100) + 100
        val n = MakeRandom.nextInt(10) + 1
        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            a ->
              Map(
                QualificationKey.LikeIt -> MakeRandom.nextInt(a),
                QualificationKey.PlatitudeAgree -> MakeRandom.nextInt(a / 10)
              )
          ),
          VoteKey.Disagree -> (
            d ->
              Map(
                QualificationKey.NoWay -> MakeRandom.nextInt(d),
                QualificationKey.PlatitudeDisagree -> MakeRandom.nextInt(d / 10)
              )
          ),
          VoteKey.Neutral -> (n -> Map(QualificationKey.DoNotCare -> MakeRandom.nextInt(n)))
        )
        fakeProposalQualif(
          ProposalId(s"tested$i"),
          votes,
          SequencePool.Tested,
          Some(IdeaId("Idea%s".format((i - 1) / 5)))
        )
      }.groupBy(p => p.ideaId.getOrElse(IdeaId(p.id.value)))

      val chosen: Seq[IndexedProposal] =
        banditSelectionAlgorithm.chooseTestedProposals(banditSequenceConfiguration, testedProposals, 10, None)
      chosen.length should be(10)
    }

    Scenario("check tested proposal chooser without bandit") {
      val noBanditConfiguration = SequenceConfiguration(
        sequenceId = SequenceId("test-sequence"),
        questionId = QuestionId("test-question"),
        newProposalsRatio = 0.5,
        newProposalsVoteThreshold = 100,
        testedProposalsEngagementThreshold = Some(0.8),
        testedProposalsScoreThreshold = Some(1.2),
        testedProposalsControversyThreshold = Some(0.1),
        intraIdeaEnabled = false,
        intraIdeaMinCount = 3,
        intraIdeaProposalsRatio = 1.0 / 3.0
      )

      val testedProposals: Map[IdeaId, Seq[IndexedProposal]] = (1 to 100).map { i =>
        val a = MakeRandom.nextInt(100) + 100
        val d = MakeRandom.nextInt(100) + 100
        val n = MakeRandom.nextInt(10) + 1
        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            a ->
              Map(
                QualificationKey.LikeIt -> MakeRandom.nextInt(a),
                QualificationKey.PlatitudeAgree -> MakeRandom.nextInt(a / 10)
              )
          ),
          VoteKey.Disagree -> (
            d ->
              Map(
                QualificationKey.NoWay -> MakeRandom.nextInt(d),
                QualificationKey.PlatitudeDisagree -> MakeRandom.nextInt(d / 10)
              )
          ),
          VoteKey.Neutral -> (n -> Map(QualificationKey.DoNotCare -> MakeRandom.nextInt(n)))
        )
        fakeProposalQualif(
          ProposalId(s"tested$i"),
          votes,
          SequencePool.Tested,
          Some(IdeaId("Idea%s".format((i - 1) / 5)))
        )
      }.groupBy(p => p.ideaId.getOrElse(IdeaId(p.id.value)))

      val chosen: Seq[IndexedProposal] =
        banditSelectionAlgorithm.chooseTestedProposals(noBanditConfiguration, testedProposals, 10, None)
      chosen.length should be(10)
    }

    Scenario("check tested proposal chooser with strict config") {
      val noBanditRatioConfiguration = SequenceConfiguration(
        sequenceId = SequenceId("test-sequence"),
        questionId = QuestionId("test-question"),
        newProposalsRatio = 0.5,
        newProposalsVoteThreshold = 100,
        testedProposalsEngagementThreshold = Some(0.8),
        testedProposalsScoreThreshold = Some(0.0),
        testedProposalsControversyThreshold = Some(0.0),
        intraIdeaEnabled = true,
        intraIdeaMinCount = 3,
        intraIdeaProposalsRatio = 0.0
      )

      val testedProposals: Map[IdeaId, Seq[IndexedProposal]] = (1 to 100).map { i =>
        val a = MakeRandom.nextInt(100) + 100
        val d = MakeRandom.nextInt(100) + 100
        val n = MakeRandom.nextInt(10) + 1
        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            a ->
              Map(
                QualificationKey.LikeIt -> MakeRandom.nextInt(a),
                QualificationKey.PlatitudeAgree -> MakeRandom.nextInt(a / 10)
              )
          ),
          VoteKey.Disagree -> (
            d ->
              Map(
                QualificationKey.NoWay -> MakeRandom.nextInt(d),
                QualificationKey.PlatitudeDisagree -> MakeRandom.nextInt(d / 10)
              )
          ),
          VoteKey.Neutral -> (n -> Map(QualificationKey.DoNotCare -> MakeRandom.nextInt(n)))
        )
        fakeProposalQualif(
          ProposalId(s"tested$i"),
          votes,
          SequencePool.Tested,
          Some(IdeaId("Idea%s".format((i - 1) / 5)))
        )
      }.groupBy(p => p.ideaId.getOrElse(IdeaId(p.id.value)))

      val chosen: Seq[IndexedProposal] =
        banditSelectionAlgorithm.chooseTestedProposals(noBanditRatioConfiguration, testedProposals, 10, None)
      chosen.length should be(10)
    }
  }

  Feature("bandit: idea competition") {
    Scenario("check champion selection") {
      val testedProposals: Seq[IndexedProposal] = (1 to 20).map { i =>
        val a = 600
        val d = 200
        val n = 200
        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            a ->
              Map(QualificationKey.LikeIt -> 16 * i)
          ),
          VoteKey.Disagree -> (
            d ->
              Map(QualificationKey.NoWay -> 16 * (21 - i))
          ),
          VoteKey.Neutral -> (n -> Map(QualificationKey.DoNotCare -> 0))
        )
        fakeProposalQualif(ProposalId(s"testedProposal$i"), votes, SequencePool.Tested)
      }

      val sequenceConfiguration =
        SequenceConfiguration(sequenceId = SequenceId("test-sequence"), questionId = QuestionId("test-question"))
      val champion = banditSelectionAlgorithm.chooseChampion(sequenceConfiguration, testedProposals, None)

      champion.id.value should be("testedProposal20")
    }

    Scenario("check idea selection") {
      val ideasWithChampion: Map[IdeaId, IndexedProposal] = (1 to 20).map { i =>
        val a = 600
        val d = 200
        val n = 200
        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            a ->
              Map(QualificationKey.LikeIt -> 16 * i)
          ),
          VoteKey.Disagree -> (
            d ->
              Map(QualificationKey.NoWay -> 16 * (21 - i))
          ),
          VoteKey.Neutral -> (n -> Map(QualificationKey.DoNotCare -> 0))
        )
        val ideaId = IdeaId("Idea%s".format(i))
        (ideaId, fakeProposalQualif(ProposalId(s"testedProposal$i"), votes, SequencePool.Tested, Some(ideaId)))
      }.toMap

      val sequenceConfiguration = SequenceConfiguration(
        sequenceId = SequenceId("test-sequence"),
        questionId = QuestionId("test-question"),
        interIdeaCompetitionTargetCount = 5
      )
      val ideas = banditSelectionAlgorithm.selectIdeasWithChampions(sequenceConfiguration, ideasWithChampion, None)

      ideas.length should be(5)

      val counts = new mutable.HashMap[IdeaId, Int]().withDefault(_ => 0)

      val samples = 1000
      for (_ <- 1 to samples) {
        banditSelectionAlgorithm
          .selectIdeasWithChampions(sequenceConfiguration, ideasWithChampion, None)
          .foreach(counts(_) += 1)
      }

      val proportions: mutable.Map[IdeaId, Double] = counts.map {
        case (i, p) => (i, p.toDouble / samples)
      }

      val confidenceInterval: Double = 0.03
      proportions(IdeaId("Idea20")) should equal(1.0 +- confidenceInterval)
      proportions(IdeaId("Idea15")) should equal(0.13 +- confidenceInterval)
    }

    Scenario("check controversial idea selection") {
      val ideasWithChampion: Map[IdeaId, IndexedProposal] = (1 to 20).map { i =>
        val a = 600
        val d = 200
        val n = 200
        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            a ->
              Map(QualificationKey.LikeIt -> 16 * i)
          ),
          VoteKey.Disagree -> (
            d ->
              Map(QualificationKey.NoWay -> 16 * (21 - i))
          ),
          VoteKey.Neutral -> (n -> Map(QualificationKey.DoNotCare -> 0))
        )
        val ideaId = IdeaId("Idea%s".format(i))
        (ideaId, fakeProposalQualif(ProposalId(s"testedProposal$i"), votes, SequencePool.Tested, Some(ideaId)))
      }.toMap

      val sequenceConfiguration = SequenceConfiguration(
        sequenceId = SequenceId("test-sequence"),
        questionId = QuestionId("test-question"),
        interIdeaCompetitionControversialCount = 5
      )
      val ideas =
        banditSelectionAlgorithm.selectControversialIdeasWithChampions(sequenceConfiguration, ideasWithChampion, None)

      ideas.length should be(5)

      val counts = new mutable.HashMap[IdeaId, Int]().withDefault(_ => 0)

      val samples = 1000
      for (_ <- 1 to samples) {
        banditSelectionAlgorithm
          .selectControversialIdeasWithChampions(sequenceConfiguration, ideasWithChampion, None)
          .foreach(counts(_) += 1)
      }

      val proportions: mutable.Map[IdeaId, Double] = counts.map {
        case (i, p) => (i, p.toDouble / samples)
      }

      val confidenceInterval: Double = 0.03
      proportions(IdeaId("Idea10")) should equal(1.0 +- confidenceInterval)
      proportions(IdeaId("Idea7")) should equal(0.06 +- confidenceInterval)
    }

    Scenario("check tested idea selection with idea competition") {
      val ideaCompetitionConfiguration = SequenceConfiguration(
        sequenceId = SequenceId("test-sequence"),
        questionId = QuestionId("test-question"),
        newProposalsRatio = 0.5,
        newProposalsVoteThreshold = 10,
        testedProposalsEngagementThreshold = Some(0.0),
        testedProposalsScoreThreshold = Some(0.0),
        testedProposalsControversyThreshold = Some(0.0),
        intraIdeaEnabled = true,
        intraIdeaMinCount = 1,
        intraIdeaProposalsRatio = 0.0,
        interIdeaCompetitionEnabled = true,
        interIdeaCompetitionTargetCount = 20,
        interIdeaCompetitionControversialRatio = 0.0,
        interIdeaCompetitionControversialCount = 2
      )

      val testedProposals: Map[IdeaId, Seq[IndexedProposal]] = (1 to 100).map { i =>
        val agreeCount = 600
        val disagreeCount = 200
        val neutralCount = 200

        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            agreeCount ->
              Map(QualificationKey.LikeIt -> 3 * i)
          ),
          VoteKey.Disagree -> (
            disagreeCount ->
              Map(QualificationKey.NoWay -> 3 * (101 - i))
          ),
          VoteKey.Neutral -> (neutralCount -> Map(QualificationKey.DoNotCare -> 0))
        )
        fakeProposalQualif(ProposalId(s"testedProposal$i"), votes, SequencePool.Tested, None)
      }.groupBy(proposal => proposal.ideaId.getOrElse(IdeaId(proposal.id.value)))

      val chosen: Seq[IndexedProposal] =
        banditSelectionAlgorithm.chooseTestedProposals(ideaCompetitionConfiguration, testedProposals, 10, None)
      chosen.length should be(10)

      val samples = 1000
      val counts: Map[ProposalId, Int] = (1 to samples).flatMap { _ =>
        banditSelectionAlgorithm
          .chooseTestedProposals(ideaCompetitionConfiguration, testedProposals, 10, None)
      }.groupBy(_.id).map {
        case (proposalId, count) => proposalId -> count.size
      }

      val proportions: Map[ProposalId, Double] = counts.map {
        case (i, p) => (i, p.toDouble / samples)
      }

      val confidenceInterval: Double = 0.01
      proportions(ProposalId("testedProposal100")) should equal(0.48 +- confidenceInterval)
      proportions(ProposalId("testedProposal51")) should equal(0.17 +- confidenceInterval)
      proportions(ProposalId("testedProposal50")) should equal(0.15 +- confidenceInterval)
    }

    Scenario("check tested idea selection with idea competition with boost") {
      val ideaCompetitionConfiguration = SequenceConfiguration(
        sequenceId = SequenceId("test-sequence"),
        questionId = QuestionId("test-question"),
        newProposalsRatio = 0.5,
        newProposalsVoteThreshold = 10,
        testedProposalsEngagementThreshold = Some(0.0),
        testedProposalsScoreThreshold = Some(0.0),
        testedProposalsControversyThreshold = Some(0.0),
        intraIdeaEnabled = true,
        intraIdeaMinCount = 1,
        intraIdeaProposalsRatio = 0.0,
        interIdeaCompetitionEnabled = true,
        interIdeaCompetitionTargetCount = 20,
        interIdeaCompetitionControversialRatio = 0.0,
        interIdeaCompetitionControversialCount = 2,
        nonSequenceVotesWeight = 0.5
      )

      val testedProposals: Map[IdeaId, Seq[IndexedProposal]] = (1 to 100).map { i =>
        val agreeCount = 600
        val disagreeCount = 200
        val neutralCount = 200

        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            agreeCount ->
              Map(QualificationKey.LikeIt -> 3 * i)
          ),
          VoteKey.Disagree -> (
            disagreeCount ->
              Map(QualificationKey.NoWay -> 3 * (101 - i))
          ),
          VoteKey.Neutral -> (neutralCount -> Map(QualificationKey.DoNotCare -> 0))
        )
        val boost = if (i == 51) 10 else 1
        fakeProposalQualif(ProposalId(s"testedProposal$i"), votes, SequencePool.Tested, None, boost = boost)
      }.groupBy(proposal => proposal.ideaId.getOrElse(IdeaId(proposal.id.value)))

      val chosen: Seq[IndexedProposal] =
        banditSelectionAlgorithm.chooseTestedProposals(ideaCompetitionConfiguration, testedProposals, 10, None)
      chosen.length should be(10)

      val samples = 1000
      val counts: Map[ProposalId, Int] = (1 to samples).flatMap { _ =>
        banditSelectionAlgorithm
          .chooseTestedProposals(ideaCompetitionConfiguration, testedProposals, 10, None)
      }.groupBy(_.id).map {
        case (proposalId, count) => proposalId -> count.size
      }

      val proportions: Map[ProposalId, Double] = counts.map {
        case (i, p) => (i, p.toDouble / samples)
      }

      val confidenceInterval: Double = 0.01
      proportions(ProposalId("testedProposal100")) should equal(0.45 +- confidenceInterval)
      proportions(ProposalId("testedProposal51")) should equal(0.26 +- confidenceInterval)
      proportions(ProposalId("testedProposal50")) should equal(0.17 +- confidenceInterval)
    }
  }

  Feature("bandit: proposal sampling") {
    Scenario("check proposal sampling") {
      val sequenceConfiguration = SequenceConfiguration(
        sequenceId = SequenceId("test-sequence"),
        questionId = QuestionId("test-question"),
        newProposalsRatio = 0.5,
        newProposalsVoteThreshold = 100,
        testedProposalsEngagementThreshold = Some(0.8),
        testedProposalsScoreThreshold = Some(0.0),
        testedProposalsControversyThreshold = Some(0.0),
        intraIdeaEnabled = true,
        intraIdeaMinCount = 3,
        intraIdeaProposalsRatio = 1.0 / 3.0,
        interIdeaCompetitionEnabled = false,
        sequenceSize = 10
      )

      val testedProposals: Seq[IndexedProposal] = (1 to 1000).map { i =>
        val a = MakeRandom.nextInt(100) + 100
        val d = MakeRandom.nextInt(100) + 100
        val n = MakeRandom.nextInt(10) + 1
        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            a ->
              Map(
                QualificationKey.LikeIt -> MakeRandom.nextInt(a),
                QualificationKey.PlatitudeAgree -> MakeRandom.nextInt(a / 10)
              )
          ),
          VoteKey.Disagree -> (
            d ->
              Map(
                QualificationKey.NoWay -> MakeRandom.nextInt(d),
                QualificationKey.PlatitudeDisagree -> MakeRandom.nextInt(d / 10)
              )
          ),
          VoteKey.Neutral -> (n -> Map(QualificationKey.DoNotCare -> MakeRandom.nextInt(n)))
        )
        fakeProposalQualif(
          ProposalId(s"tested$i"),
          votes,
          SequencePool.Tested,
          Some(IdeaId("Idea%s".format((i - 1) / 5)))
        )
      }

      val chosen =
        banditSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = sequenceConfiguration,
          includedProposals = Seq.empty,
          newProposals = Seq.empty,
          testedProposals = testedProposals,
          votedProposals = Seq.empty,
          userSegment = None
        )
      chosen.length should be(10)
    }
  }

  Feature("round-robin: proposal sampling") {
    Scenario("no proposals") {
      val chosen =
        roundRobinSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = roundRobinSequenceConfiguration,
          includedProposals = Seq.empty,
          newProposals = Seq.empty,
          testedProposals = Seq.empty,
          votedProposals = Seq.empty,
          userSegment = None
        )
      chosen.length should be(0)
    }

    Scenario("with included proposals") {
      val proposals: Seq[IndexedProposal] =
        roundRobinProposalIds.map(id => fakeProposal(id, Map.empty, SequencePool.Tested))

      val selectedProposals =
        roundRobinSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = roundRobinSequenceConfiguration,
          includedProposals = proposals,
          newProposals = Seq.empty,
          testedProposals = Seq.empty,
          votedProposals = Seq.empty,
          userSegment = None
        )

      selectedProposals.size should be(roundRobinSequenceConfiguration.sequenceSize)
      selectedProposals.toSet.size should be(roundRobinSequenceConfiguration.sequenceSize)
    }

    Scenario("with voted proposals") {
      val testedProposals: Seq[IndexedProposal] =
        roundRobinProposalIds.map(id => fakeProposal(id, Map.empty, SequencePool.Tested)) ++
          Seq(fakeProposal(ProposalId("other-proposal"), Map.empty, SequencePool.Tested))

      val selectedProposals =
        roundRobinSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = roundRobinSequenceConfiguration,
          includedProposals = Seq.empty,
          newProposals = Seq.empty,
          testedProposals = testedProposals,
          votedProposals = roundRobinProposalIds,
          userSegment = None
        )

      selectedProposals.size should be(1)
      selectedProposals.head.id should be(ProposalId("other-proposal"))
    }

    Scenario("no duplicates with only already voted tested proposals") {

      val testedProposals: Seq[IndexedProposal] =
        roundRobinProposalIds.map(id => fakeProposal(id, Map.empty, SequencePool.Tested))

      val selectedProposals =
        roundRobinSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = roundRobinSequenceConfiguration,
          includedProposals = Seq.empty,
          newProposals = Seq.empty,
          testedProposals = testedProposals,
          votedProposals = roundRobinProposalIds,
          userSegment = None
        )

      selectedProposals.size should be(0)
    }

    Scenario("valid proposal sampling") {

      val testedProposals: Seq[IndexedProposal] =
        (1 to 1000).map { i =>
          fakeProposal(
            ProposalId(s"testedProposal$i"),
            Map(VoteKey.Agree -> i),
            SequencePool.Tested,
            None,
            DateHelper.now()
          )
        }
      val selectedProposals =
        roundRobinSelectionAlgorithm.selectProposalsForSequence(
          sequenceConfiguration = roundRobinSequenceConfiguration,
          includedProposals = Seq.empty,
          newProposals = Seq.empty,
          testedProposals = testedProposals,
          votedProposals = roundRobinProposalIds,
          userSegment = None
        )

      selectedProposals.size should be(roundRobinSequenceConfiguration.sequenceSize)
      selectedProposals.toSet should be(testedProposals.take(roundRobinSequenceConfiguration.sequenceSize).toSet)
    }
  }
}
