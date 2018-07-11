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

import org.apache.commons.math3.random.MersenneTwister
import org.make.api.MakeTest
import org.make.api.proposal._
import org.make.core.idea.IdeaId
import org.make.core.proposal._
import org.make.core.sequence.SequenceId
import org.make.core.user.UserId
import org.make.core.{proposal, DateHelper, RequestContext}

import scala.collection.mutable
import scala.util.Random

class Switch {
  private var current = false

  def getAndSwitch(): Boolean = {
    val result = current
    current = !current
    result
  }
}

class SelectionAlgorithmTest extends MakeTest with DefaultSelectionAlgorithmComponent {

  val sequenceConfiguration = SequenceConfiguration(
    sequenceId = SequenceId("test-sequence"),
    newProposalsRatio = 0.5,
    newProposalsVoteThreshold = 100,
    testedProposalsEngagementThreshold = 0.8,
    testedProposalsScoreThreshold = 0.0,
    testedProposalsControversyThreshold = 0.0,
    banditEnabled = true,
    banditMinCount = 3,
    banditProposalsRatio = 1.0 / 3.0,
    ideaCompetitionEnabled = false
  )

  val defaultSize = 12
  val proposalIds: Seq[ProposalId] = (1 to defaultSize).map(i => ProposalId(s"proposal$i"))

  def fakeProposal(id: ProposalId,
                   votes: Map[VoteKey, Int],
                   idea: Option[IdeaId] = None,
                   createdAt: ZonedDateTime = DateHelper.now()): Proposal = {
    proposal.Proposal(
      proposalId = id,
      author = UserId(s"fake-$id"),
      content = "fake",
      slug = "fake",
      status = ProposalStatus.Accepted,
      createdAt = Some(createdAt),
      updatedAt = None,
      votes = votes.map {
        case (key, amount) => Vote(key = key, count = amount, qualifications = Seq.empty)
      }.toSeq,
      labels = Seq.empty,
      theme = None,
      refusalReason = None,
      tags = Seq.empty,
      idea = idea,
      events = Nil,
      creationContext = RequestContext.empty,
      language = Some("fr"),
      country = Some("FR")
    )
  }

  def fakeProposalQualif(id: ProposalId,
                         votes: Map[VoteKey, (Int, Map[QualificationKey, Int])],
                         idea: Option[IdeaId] = None,
                         createdAt: ZonedDateTime = DateHelper.now()): Proposal = {
    proposal.Proposal(
      proposalId = id,
      author = UserId(s"fake-$id"),
      content = "fake",
      slug = "fake",
      status = ProposalStatus.Accepted,
      createdAt = Some(createdAt),
      updatedAt = None,
      votes = votes.map {
        case (key, (amount, qualifs)) =>
          Vote(key = key, count = amount, qualifications = qualifs.map {
            case (qualifKey, count) => Qualification(key = qualifKey, count = count)
          }.toSeq)
      }.toSeq,
      labels = Seq.empty,
      theme = None,
      refusalReason = None,
      tags = Seq.empty,
      idea = idea,
      events = Nil,
      creationContext = RequestContext.empty,
      language = Some("fr"),
      country = Some("FR")
    )
  }

  feature("proposal selection algorithm with new proposals") {
    scenario("no duplicates with enough proposals only new proposals") {

      val proposals: Seq[Proposal] =
        proposalIds.map(id => fakeProposal(id, Map.empty))

      val selectedProposals =
        selectionAlgorithm.selectProposalsForSequence(
          defaultSize,
          sequenceConfiguration,
          proposals,
          Seq.empty,
          Seq.empty
        )

      selectedProposals.size should be(defaultSize)
      selectedProposals.toSet.size should be(defaultSize)
    }

    scenario("no duplicates with enough proposals and include list only new proposals") {

      val proposals: Seq[Proposal] =
        proposalIds.map(id => fakeProposal(id, Map.empty))

      val sequenceProposals = selectionAlgorithm.selectProposalsForSequence(
        targetLength = defaultSize,
        sequenceConfiguration = sequenceConfiguration,
        proposals = proposals,
        votedProposals = Seq.empty,
        includeList = Seq(ProposalId("Included 1"), ProposalId("Included 2"), ProposalId("Included 3"))
      )

      sequenceProposals.size should be(defaultSize)
      sequenceProposals.toSet.size should be(defaultSize)
      sequenceProposals.contains(ProposalId("Included 1")) should be(true)
      sequenceProposals.contains(ProposalId("Included 2")) should be(true)
      sequenceProposals.contains(ProposalId("Included 3")) should be(true)

    }

    scenario("duplicates with enough proposals only new proposals") {

      val duplicates: Map[ProposalId, IdeaId] =
        Map(ProposalId("proposal1") -> IdeaId("TestIdea"), ProposalId("proposal2") -> IdeaId("TestIdea"))

      val proposals: Seq[Proposal] =
        proposalIds.map(id => fakeProposal(id, Map.empty, duplicates.get(id)))

      val sequenceProposals =
        selectionAlgorithm.selectProposalsForSequence(
          defaultSize,
          sequenceConfiguration,
          proposals,
          Seq.empty,
          Seq.empty
        )

      sequenceProposals.size should be(defaultSize - 1)
      sequenceProposals.toSet.size should be(defaultSize - 1)
      if (sequenceProposals.contains(ProposalId("proposal1"))) {
        sequenceProposals.contains(ProposalId("proposal2")) should be(false)
      } else {
        sequenceProposals.contains(ProposalId("proposal2")) should be(true)
      }
    }

    scenario("duplicates from include list with enough proposals only new proposals") {

      val duplicates: Map[ProposalId, IdeaId] =
        Map(ProposalId("proposal1") -> IdeaId("TestIdea"), ProposalId("included1") -> IdeaId("TestIdea"))

      val proposals: Seq[Proposal] =
        (proposalIds ++ Seq(ProposalId("included1"))).map(id => fakeProposal(id, Map.empty, duplicates.get(id)))

      val sequenceProposals =
        selectionAlgorithm.selectProposalsForSequence(
          defaultSize,
          sequenceConfiguration,
          proposals,
          Seq.empty,
          Seq(ProposalId("included1"))
        )

      sequenceProposals.size should be(defaultSize)
      sequenceProposals.size should be(defaultSize)
      sequenceProposals.contains(ProposalId("included1")) should be(true)
      sequenceProposals.contains(ProposalId("proposal1")) should be(false)
    }
  }

  feature("proposal selection algorithm with tested proposals") {

    scenario("no duplicates with enough proposals only tested proposals") {

      val proposals: Seq[Proposal] =
        proposalIds.map(id => fakeProposal(id, Map(VoteKey.Agree -> 200)))

      val selectedProposals =
        selectionAlgorithm.selectProposalsForSequence(
          defaultSize,
          sequenceConfiguration,
          proposals,
          Seq.empty,
          Seq.empty
        )

      selectedProposals.size should be(defaultSize)
      selectedProposals.toSet.size should be(defaultSize)
    }

    scenario("no duplicates with enough proposals and include list only tested proposals") {

      val proposals: Seq[Proposal] =
        proposalIds.map(id => fakeProposal(id, Map(VoteKey.Agree -> 200)))

      val sequenceProposals = selectionAlgorithm.selectProposalsForSequence(
        targetLength = defaultSize,
        sequenceConfiguration = sequenceConfiguration,
        proposals = proposals,
        votedProposals = Seq.empty,
        includeList = Seq(ProposalId("Included 1"), ProposalId("Included 2"), ProposalId("Included 3"))
      )

      sequenceProposals.size should be(defaultSize)
      sequenceProposals.toSet.size should be(defaultSize)
      sequenceProposals.contains(ProposalId("Included 1")) should be(true)
      sequenceProposals.contains(ProposalId("Included 2")) should be(true)
      sequenceProposals.contains(ProposalId("Included 3")) should be(true)

    }

    scenario("duplicates with enough proposals only tested proposals") {

      val duplicates =
        Map(ProposalId("proposal1") -> IdeaId("TestId"), ProposalId("proposal2") -> IdeaId("TestId"))

      val proposals: Seq[Proposal] =
        proposalIds.map(id => fakeProposal(id, Map(VoteKey.Agree -> 200), duplicates.get(id)))

      val sequenceProposals =
        selectionAlgorithm.selectProposalsForSequence(
          defaultSize,
          sequenceConfiguration,
          proposals,
          Seq.empty,
          Seq.empty
        )

      sequenceProposals.size should be(defaultSize - 1)
      sequenceProposals.toSet.size should be(defaultSize - 1)
      if (sequenceProposals.contains(ProposalId("proposal1"))) {
        sequenceProposals.contains(ProposalId("proposal2")) should be(false)
      } else {
        sequenceProposals.contains(ProposalId("proposal2")) should be(true)
      }
    }

    scenario("duplicates from include list with enough proposals only tested proposals") {

      val duplicates =
        Map(ProposalId("proposal1") -> IdeaId("TestId"), ProposalId("included1") -> IdeaId("TestId"))

      val proposals: Seq[Proposal] =
        (proposalIds ++ Seq(ProposalId("included1")))
          .map(id => fakeProposal(id, Map(VoteKey.Agree -> 200), duplicates.get(id)))

      val sequenceProposals =
        selectionAlgorithm.selectProposalsForSequence(
          defaultSize,
          sequenceConfiguration,
          proposals,
          Seq.empty,
          Seq(ProposalId("included1"))
        )

      sequenceProposals.size should be(defaultSize)
      sequenceProposals.size should be(defaultSize)
      sequenceProposals.contains(ProposalId("included1")) should be(true)
      sequenceProposals.contains(ProposalId("proposal1")) should be(false)
    }

  }

  feature("proposal selection algorithm with mixed new and tested proposals") {

    scenario("no duplicates with enough proposals") {

      val switch = new Switch()

      val proposals: Seq[Proposal] =
        proposalIds.map(
          id =>
            fakeProposal(id, Map(VoteKey.Agree -> (if (switch.getAndSwitch()) {
                                                     200
                                                   } else {
                                                     50
                                                   })))
        )

      val selectedProposals =
        selectionAlgorithm.selectProposalsForSequence(
          defaultSize,
          sequenceConfiguration,
          proposals,
          Seq.empty,
          Seq.empty
        )

      selectedProposals.size should be(defaultSize)
      selectedProposals.toSet.size should be(defaultSize)
    }

    scenario("no duplicates with enough proposals and include list") {

      val switch = new Switch()

      val proposals: Seq[Proposal] =
        proposalIds.map(
          id =>
            fakeProposal(id, Map(VoteKey.Agree -> (if (switch.getAndSwitch()) {
                                                     200
                                                   } else {
                                                     50
                                                   })))
        )

      val sequenceProposals = selectionAlgorithm.selectProposalsForSequence(
        targetLength = defaultSize,
        sequenceConfiguration = sequenceConfiguration,
        proposals = proposals,
        votedProposals = Seq.empty,
        includeList = Seq(ProposalId("Included 1"), ProposalId("Included 2"), ProposalId("Included 3"))
      )

      sequenceProposals.size should be(defaultSize)
      sequenceProposals.toSet.size should be(defaultSize)
      sequenceProposals.contains(ProposalId("Included 1")) should be(true)
      sequenceProposals.contains(ProposalId("Included 2")) should be(true)
      sequenceProposals.contains(ProposalId("Included 3")) should be(true)

    }

    scenario("duplicates with enough proposals") {

      val switch = new Switch()

      val duplicates =
        Map(ProposalId("proposal1") -> IdeaId("TestIdea"), ProposalId("proposal2") -> IdeaId("TestIdea"))

      val proposals: Seq[Proposal] =
        proposalIds.map(
          id =>
            fakeProposal(id, Map(VoteKey.Agree -> (if (switch.getAndSwitch()) {
                                                     200
                                                   } else {
                                                     50
                                                   })), duplicates.get(id))
        )

      val sequenceProposals =
        selectionAlgorithm.selectProposalsForSequence(
          defaultSize,
          sequenceConfiguration,
          proposals,
          Seq.empty,
          Seq.empty
        )

      sequenceProposals.size should be(defaultSize - 1)
      sequenceProposals.toSet.size should be(defaultSize - 1)
      if (sequenceProposals.contains(ProposalId("proposal1"))) {
        sequenceProposals.contains(ProposalId("proposal2")) should be(false)
      } else {
        sequenceProposals.contains(ProposalId("proposal2")) should be(true)
      }
    }

    scenario("duplicates from include list with enough proposals") {

      val switch = new Switch()

      val duplicates =
        Map(ProposalId("proposal1") -> IdeaId("TestIdea"), ProposalId("included1") -> IdeaId("TestIdea"))

      val proposals: Seq[Proposal] =
        (proposalIds ++ Seq(ProposalId("included1"))).map(
          id =>
            fakeProposal(id, Map(VoteKey.Agree -> (if (switch.getAndSwitch()) {
                                                     200
                                                   } else {
                                                     50
                                                   })), duplicates.get(id))
        )

      val sequenceProposals =
        selectionAlgorithm.selectProposalsForSequence(
          defaultSize,
          sequenceConfiguration,
          proposals,
          Seq.empty,
          Seq(ProposalId("included1"))
        )

      sequenceProposals.size should be(defaultSize)
      sequenceProposals.size should be(defaultSize)
      sequenceProposals.contains(ProposalId("included1")) should be(true)
      sequenceProposals.contains(ProposalId("proposal1")) should be(false)
    }

    scenario("check first in first out behavior") {

      val newProposalIds: Seq[ProposalId] = (1 to defaultSize).map(i    => ProposalId(s"newProposal$i"))
      val testedProposalIds: Seq[ProposalId] = (1 to defaultSize).map(i => ProposalId(s"testedProposal$i"))

      val newProposals: Seq[Proposal] =
        newProposalIds.zipWithIndex.map {
          case (id, i) =>
            fakeProposal(
              id,
              Map(VoteKey.Agree -> 0),
              None,
              ZonedDateTime.parse("2017-12-07T16:00:00Z").plus(i, ChronoUnit.MINUTES)
            )
        }

      val newProposalsRandom = Random.shuffle(newProposals)

      val testedProposals: Seq[Proposal] =
        testedProposalIds.map(id => fakeProposal(id, Map(VoteKey.Agree -> 200)))

      val proposals: Seq[Proposal] = newProposalsRandom ++ testedProposals

      val sequenceProposals =
        selectionAlgorithm.selectProposalsForSequence(
          defaultSize,
          sequenceConfiguration,
          proposals,
          Seq.empty,
          Seq.empty
        )

      sequenceProposals.size should be(defaultSize)
      sequenceProposals.contains(ProposalId("newProposal1")) should be(true)
      sequenceProposals.contains(ProposalId("newProposal2")) should be(true)
      sequenceProposals.contains(ProposalId("newProposal3")) should be(true)
      sequenceProposals.contains(ProposalId("newProposal4")) should be(true)
      sequenceProposals.contains(ProposalId("newProposal5")) should be(true)
      sequenceProposals.contains(ProposalId("newProposal6")) should be(true)
      sequenceProposals.contains(ProposalId("newProposal7")) should be(false)
      sequenceProposals.contains(ProposalId("newProposal8")) should be(false)
      sequenceProposals.contains(ProposalId("newProposal9")) should be(false)
      sequenceProposals.contains(ProposalId("newProposal10")) should be(false)
      sequenceProposals.contains(ProposalId("newProposal11")) should be(false)
      sequenceProposals.contains(ProposalId("newProposal12")) should be(false)
    }

    scenario("check filtering based on engagement rate") {

      val newProposalIds: Seq[ProposalId] = (1 to defaultSize).map(i    => ProposalId(s"newProposal$i"))
      val testedProposalIds: Seq[ProposalId] = (1 to defaultSize).map(i => ProposalId(s"testedProposal$i"))

      val newProposals: Seq[Proposal] =
        newProposalIds.map(id => fakeProposal(id, Map(VoteKey.Agree -> 0)))

      val testedProposals: Seq[Proposal] =
        testedProposalIds.zipWithIndex.map {
          case (id, i) =>
            fakeProposal(id, Map(VoteKey.Agree -> (200 - i * 10), VoteKey.Neutral -> i * 10))
        }

      val testedProposalsRandom = Random.shuffle(testedProposals)

      val sequenceProposals =
        selectionAlgorithm.selectProposalsForSequence(
          defaultSize,
          sequenceConfiguration,
          newProposals ++ testedProposalsRandom,
          Seq.empty,
          Seq.empty
        )

      sequenceProposals.size should be(12)
      sequenceProposals.contains(ProposalId("testedProposal1")) should be(true)
      sequenceProposals.contains(ProposalId("testedProposal2")) should be(true)
      sequenceProposals.contains(ProposalId("testedProposal3")) should be(true)
      sequenceProposals.contains(ProposalId("testedProposal4")) should be(true)
      sequenceProposals.contains(ProposalId("testedProposal5")) should be(true)
      sequenceProposals.contains(ProposalId("testedProposal6")) should be(true)
      sequenceProposals.contains(ProposalId("testedProposal7")) should be(false)
      sequenceProposals.contains(ProposalId("testedProposal8")) should be(false)
      sequenceProposals.contains(ProposalId("testedProposal9")) should be(false)
      sequenceProposals.contains(ProposalId("testedProposal10")) should be(false)
      sequenceProposals.contains(ProposalId("testedProposal11")) should be(false)
      sequenceProposals.contains(ProposalId("testedProposal12")) should be(false)
    }

    scenario("most engaging first") {

      val newProposalIds: Seq[ProposalId] = (1 to defaultSize / 2).map(i    => ProposalId(s"newProposal$i"))
      val testedProposalIds: Seq[ProposalId] = (1 to defaultSize / 2).map(i => ProposalId(s"testedProposal$i"))

      val newProposals: Seq[Proposal] =
        newProposalIds.map(id => fakeProposal(id, Map(VoteKey.Agree -> 0)))

      val testedProposals: Seq[Proposal] =
        testedProposalIds.zipWithIndex.map {
          case (id, i) =>
            fakeProposal(id, Map(VoteKey.Agree -> (800 + i * 10), VoteKey.Neutral -> (200 - i * 20)))
        }

      val testedProposalsRandom = Random.shuffle(testedProposals)

      val sequenceProposals =
        selectionAlgorithm.selectProposalsForSequence(
          defaultSize,
          sequenceConfiguration,
          newProposals ++ testedProposalsRandom,
          Seq.empty,
          Seq.empty
        )

      sequenceProposals.size should be(12)
      sequenceProposals.head should be(ProposalId("testedProposal6"))
    }
  }

  feature("allocate votes inversely proportional to current vote count") {
    scenario("sequential vote counts") {
      val testedProposals: Seq[Proposal] = (1 to 10).map { i =>
        fakeProposal(ProposalId(s"testedProposal$i"), Map(VoteKey.Agree -> 100 * i), None, DateHelper.now())
      }

      val counts = new mutable.HashMap[ProposalId, Int]() { override def default(key: ProposalId) = 0 }

      InverseWeightedRandom.random = new Random(0)

      val samples = 10000
      for (_ <- 1 to samples) {
        selectionAlgorithm
          .chooseProposals(proposals = testedProposals, count = 1, algorithm = InverseWeightedRandom)
          .foreach(p => counts(p.proposalId) += 1)
      }

      val proportions: mutable.Map[ProposalId, Double] = counts.map {
        case (i, p) => (i, p.toDouble / samples)
      }

      val confidenceInterval: Double = 0.015
      proportions(testedProposals(0).proposalId) should equal(0.34 +- confidenceInterval)
      proportions(testedProposals(1).proposalId) should equal(0.17 +- confidenceInterval)
      proportions(testedProposals(2).proposalId) should equal(0.11 +- confidenceInterval)
      proportions(testedProposals(3).proposalId) should equal(0.09 +- confidenceInterval)
      proportions(testedProposals(4).proposalId) should equal(0.07 +- confidenceInterval)
      proportions(testedProposals(5).proposalId) should equal(0.06 +- confidenceInterval)
      proportions(testedProposals(6).proposalId) should equal(0.05 +- confidenceInterval)
      proportions(testedProposals(7).proposalId) should equal(0.04 +- confidenceInterval)
      proportions(testedProposals(8).proposalId) should equal(0.04 +- confidenceInterval)
      proportions(testedProposals(9).proposalId) should equal(0.03 +- confidenceInterval)
    }
  }

  feature("allocate votes using soft min") {
    scenario("power of 2 vote counts") {
      val testedProposals: Seq[Proposal] = (1 to 10).map { i =>
        fakeProposal(
          ProposalId(s"testedProposal$i"),
          Map(VoteKey.Agree -> Math.pow(2, i).toInt),
          None,
          DateHelper.now()
        )
      }

      val counts = new mutable.HashMap[ProposalId, Int]() { override def default(key: ProposalId) = 0 }

      SoftMinRandom.random = new Random(0)

      val samples = 10000
      for (_ <- 1 to samples) {
        selectionAlgorithm
          .chooseProposals(proposals = testedProposals, count = 1, algorithm = SoftMinRandom)
          .foreach(p => counts(p.proposalId) += 1)
      }

      val proportions: mutable.Map[ProposalId, Double] = counts.map {
        case (i, p) => (i, p.toDouble / samples)
      }

      proportions.size should be(3)
      val confidenceInterval: Double = 0.015
      proportions(testedProposals(0).proposalId) should equal(0.88 +- confidenceInterval)
      proportions(testedProposals(1).proposalId) should equal(0.11 +- confidenceInterval)
      proportions(testedProposals(2).proposalId) should equal(0.01 +- confidenceInterval)
    }
  }

  feature("allocate vote with bandit algorithm within idea") {
    scenario("check proposal scorer") {
      val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
        VoteKey.Agree -> (50 -> Map(QualificationKey.LikeIt -> 20, QualificationKey.PlatitudeAgree -> 10)),
        VoteKey.Disagree -> (20 -> Map(QualificationKey.NoWay -> 10, QualificationKey.PlatitudeDisagree -> 5)),
        VoteKey.Neutral -> (30 -> Map(QualificationKey.DoNotCare -> 10))
      )

      val testProposal = fakeProposalQualif(ProposalId("tested"), votes)
      val testProposalScore = ProposalScorerHelper.topScore(testProposal)

      ProposalScorerHelper.random = new MersenneTwister(0)
      val trials = 1000
      val samples = (1 to trials).map(i => ProposalScorerHelper.sampleScore(testProposal))

      testProposal.votes.map(_.count).sum should be(100)
      samples.max should be > testProposalScore + 0.1
      samples.min should be < testProposalScore - 0.1
      samples.sum / trials should be(testProposalScore +- 0.01)
    }

    scenario("check proposal scorer with pathological proposal") {
      val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
        VoteKey.Agree -> (0 -> Map.empty),
        VoteKey.Disagree -> (0 -> Map.empty),
        VoteKey.Neutral -> (0 -> Map.empty)
      )
      val testProposal: Proposal = fakeProposalQualif(ProposalId("tested"), votes)

      val testProposalScore: Double = ProposalScorerHelper.topScore(testProposal)
      val testProposalScoreSample: Double = ProposalScorerHelper.sampleScore(testProposal)

      testProposalScore should be > 0.0
      testProposalScoreSample should be > 0.0
    }

    scenario("check bandit chooser for similars") {
      val random = new Random(0)
      val testedProposals: Seq[Proposal] = (1 to 20).map { i =>
        val a = random.nextInt(100) + 1
        val d = random.nextInt(100) + 1
        val n = random.nextInt(100) + 1
        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            a ->
              Map(QualificationKey.LikeIt -> random.nextInt(a), QualificationKey.PlatitudeAgree -> random.nextInt(a))
          ),
          VoteKey.Disagree -> (
            d ->
              Map(QualificationKey.NoWay -> random.nextInt(d), QualificationKey.PlatitudeDisagree -> random.nextInt(d))
          ),
          VoteKey.Neutral -> (n -> Map(QualificationKey.DoNotCare -> random.nextInt(n)))
        )
        fakeProposalQualif(ProposalId(s"tested$i"), votes)
      }

      UniformRandom.random = new Random(0)
      ProposalScorerHelper.random = new MersenneTwister(0)

      val sortedProposals: Seq[ProposalId] = testedProposals
        .map(p => selectionAlgorithm.ScoredProposal(p, ProposalScorerHelper.sampleScore(p)))
        .sortWith(_.score > _.score)
        .map(sp => sp.proposal.proposalId)

      val chosenCounts: Seq[ProposalId] =
        (1 to 1000)
          .map(i => selectionAlgorithm.chooseProposalBandit(sequenceConfiguration, testedProposals).proposalId -> 1)
          .groupBy(_._1)
          .mapValues(_.map(_._2).sum)
          .toSeq
          .sortWith(_._2 > _._2)
          .map(_._1)

      chosenCounts.slice(0, 2).contains(sortedProposals(0)) should be(true)
      chosenCounts.slice(0, 5).contains(sortedProposals(1)) should be(true)
      chosenCounts.slice(0, 10).contains(sortedProposals(2)) should be(true)
      chosenCounts.slice(0, 10).contains(sortedProposals(19)) should be(false)
    }

    scenario("check tested proposal chooser") {
      val random = new Random(0)
      val testedProposals: Seq[Proposal] = (1 to 100).map { i =>
        val a = random.nextInt(100) + 100
        val d = random.nextInt(100) + 100
        val n = random.nextInt(10) + 1
        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            a ->
              Map(
                QualificationKey.LikeIt -> random.nextInt(a),
                QualificationKey.PlatitudeAgree -> random.nextInt(a / 10)
              )
          ),
          VoteKey.Disagree -> (
            d ->
              Map(
                QualificationKey.NoWay -> random.nextInt(d),
                QualificationKey.PlatitudeDisagree -> random.nextInt(d / 10)
              )
          ),
          VoteKey.Neutral -> (n -> Map(QualificationKey.DoNotCare -> random.nextInt(n)))
        )
        fakeProposalQualif(ProposalId(s"tested$i"), votes, Some(IdeaId("Idea%s".format((i - 1) / 5))))
      }

      UniformRandom.random = new Random(0)
      ProposalScorerHelper.random = new MersenneTwister(0)

      val chosen: Seq[Proposal] = selectionAlgorithm.chooseTestedProposals(sequenceConfiguration, testedProposals, 10)
      chosen.length should be(10)
    }

    scenario("check tested proposal chooser without bandit") {
      val noBanditConfiguration = SequenceConfiguration(
        sequenceId = SequenceId("test-sequence"),
        newProposalsRatio = 0.5,
        newProposalsVoteThreshold = 100,
        testedProposalsEngagementThreshold = 0.8,
        testedProposalsScoreThreshold = 1.2,
        testedProposalsControversyThreshold = 0.1,
        banditEnabled = false,
        banditMinCount = 3,
        banditProposalsRatio = 1.0 / 3.0
      )

      val random = new Random(0)
      val testedProposals: Seq[Proposal] = (1 to 100).map { i =>
        val a = random.nextInt(100) + 100
        val d = random.nextInt(100) + 100
        val n = random.nextInt(10) + 1
        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            a ->
              Map(
                QualificationKey.LikeIt -> random.nextInt(a),
                QualificationKey.PlatitudeAgree -> random.nextInt(a / 10)
              )
          ),
          VoteKey.Disagree -> (
            d ->
              Map(
                QualificationKey.NoWay -> random.nextInt(d),
                QualificationKey.PlatitudeDisagree -> random.nextInt(d / 10)
              )
          ),
          VoteKey.Neutral -> (n -> Map(QualificationKey.DoNotCare -> random.nextInt(n)))
        )
        fakeProposalQualif(ProposalId(s"tested$i"), votes, Some(IdeaId("Idea%s".format((i - 1) / 5))))
      }

      UniformRandom.random = new Random(0)
      ProposalScorerHelper.random = new MersenneTwister(0)

      val chosen: Seq[Proposal] = selectionAlgorithm.chooseTestedProposals(noBanditConfiguration, testedProposals, 10)
      chosen.length should be(10)
    }

    scenario("check tested proposal chooser with strict config") {
      val noBanditRatioConfiguration = SequenceConfiguration(
        sequenceId = SequenceId("test-sequence"),
        newProposalsRatio = 0.5,
        newProposalsVoteThreshold = 100,
        testedProposalsEngagementThreshold = 0.8,
        testedProposalsScoreThreshold = 0.0,
        testedProposalsControversyThreshold = 0.0,
        banditEnabled = true,
        banditMinCount = 3,
        banditProposalsRatio = 0.0
      )

      val random = new Random(0)
      val testedProposals: Seq[Proposal] = (1 to 100).map { i =>
        val a = random.nextInt(100) + 100
        val d = random.nextInt(100) + 100
        val n = random.nextInt(10) + 1
        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            a ->
              Map(
                QualificationKey.LikeIt -> random.nextInt(a),
                QualificationKey.PlatitudeAgree -> random.nextInt(a / 10)
              )
          ),
          VoteKey.Disagree -> (
            d ->
              Map(
                QualificationKey.NoWay -> random.nextInt(d),
                QualificationKey.PlatitudeDisagree -> random.nextInt(d / 10)
              )
          ),
          VoteKey.Neutral -> (n -> Map(QualificationKey.DoNotCare -> random.nextInt(n)))
        )
        fakeProposalQualif(ProposalId(s"tested$i"), votes, Some(IdeaId("Idea%s".format((i - 1) / 5))))
      }

      UniformRandom.random = new Random(0)
      ProposalScorerHelper.random = new MersenneTwister(0)

      val chosen: Seq[Proposal] =
        selectionAlgorithm.chooseTestedProposals(noBanditRatioConfiguration, testedProposals, 10)
      chosen.length should be(10)
    }
  }

  feature("score + controversy score filter") {

    scenario("check tested proposal chooser with score threshold") {
      val scoreThresholdConfiguration = SequenceConfiguration(
        sequenceId = SequenceId("test-sequence"),
        newProposalsRatio = 0.5,
        newProposalsVoteThreshold = 100,
        testedProposalsEngagementThreshold = 0.8,
        testedProposalsScoreThreshold = 1.15,
        testedProposalsControversyThreshold = 1.0,
        banditEnabled = false,
        banditMinCount = 0,
        banditProposalsRatio = 0.0
      )

      val testedProposals: Seq[Proposal] = (1 to 20).map { i =>
        val a = 800
        val n = 200
        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            a ->
              Map(QualificationKey.LikeIt -> 8 * i, QualificationKey.Doable -> 8 * i)
          ),
          VoteKey.Neutral -> (n -> Map(QualificationKey.DoNotCare -> 0))
        )
        fakeProposalQualif(ProposalId(s"testedProposal$i"), votes)
      }

      UniformRandom.random = new Random(0)
      ProposalScorerHelper.random = new MersenneTwister(0)

      val chosen: Seq[Proposal] =
        selectionAlgorithm.chooseTestedProposals(scoreThresholdConfiguration, testedProposals, 10)
      val chosenIds = chosen.map(_.proposalId)
      chosen.length should be(10)
      chosenIds.contains(ProposalId("testedProposal1")) should be(false)
      chosenIds.contains(ProposalId("testedProposal2")) should be(false)
      chosenIds.contains(ProposalId("testedProposal3")) should be(false)
      chosenIds.contains(ProposalId("testedProposal4")) should be(false)
      chosenIds.contains(ProposalId("testedProposal5")) should be(false)
      chosenIds.contains(ProposalId("testedProposal6")) should be(false)
      chosenIds.contains(ProposalId("testedProposal7")) should be(false)
      chosenIds.contains(ProposalId("testedProposal8")) should be(false)
      chosenIds.contains(ProposalId("testedProposal9")) should be(false)
      chosenIds.contains(ProposalId("testedProposal10")) should be(false)
      chosenIds.contains(ProposalId("testedProposal11")) should be(true)
      chosenIds.contains(ProposalId("testedProposal12")) should be(true)
      chosenIds.contains(ProposalId("testedProposal13")) should be(true)
      chosenIds.contains(ProposalId("testedProposal14")) should be(true)
      chosenIds.contains(ProposalId("testedProposal15")) should be(true)
      chosenIds.contains(ProposalId("testedProposal16")) should be(true)
      chosenIds.contains(ProposalId("testedProposal17")) should be(true)
      chosenIds.contains(ProposalId("testedProposal18")) should be(true)
      chosenIds.contains(ProposalId("testedProposal19")) should be(true)
      chosenIds.contains(ProposalId("testedProposal20")) should be(true)
    }

    scenario("check tested proposal chooser with controversy threshold") {
      val scoreThresholdConfiguration = SequenceConfiguration(
        sequenceId = SequenceId("test-sequence"),
        newProposalsRatio = 0.5,
        newProposalsVoteThreshold = 100,
        testedProposalsEngagementThreshold = 0.8,
        testedProposalsScoreThreshold = 2.0,
        testedProposalsControversyThreshold = .14,
        banditEnabled = false,
        banditMinCount = 0,
        banditProposalsRatio = 0.0
      )

      val testedProposals: Seq[Proposal] = (1 to 20).map { i =>
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
        fakeProposalQualif(ProposalId(s"testedProposal$i"), votes)
      }

      UniformRandom.random = new Random(0)
      ProposalScorerHelper.random = new MersenneTwister(0)

      val chosen: Seq[Proposal] =
        selectionAlgorithm.chooseTestedProposals(scoreThresholdConfiguration, testedProposals, 10)
      val chosenIds = chosen.map(_.proposalId)
      chosen.length should be(10)
      chosenIds.contains(ProposalId("testedProposal1")) should be(false)
      chosenIds.contains(ProposalId("testedProposal2")) should be(false)
      chosenIds.contains(ProposalId("testedProposal3")) should be(false)
      chosenIds.contains(ProposalId("testedProposal4")) should be(false)
      chosenIds.contains(ProposalId("testedProposal5")) should be(false)
      chosenIds.contains(ProposalId("testedProposal6")) should be(true)
      chosenIds.contains(ProposalId("testedProposal7")) should be(true)
      chosenIds.contains(ProposalId("testedProposal8")) should be(true)
      chosenIds.contains(ProposalId("testedProposal9")) should be(true)
      chosenIds.contains(ProposalId("testedProposal10")) should be(true)
      chosenIds.contains(ProposalId("testedProposal11")) should be(true)
      chosenIds.contains(ProposalId("testedProposal12")) should be(true)
      chosenIds.contains(ProposalId("testedProposal13")) should be(true)
      chosenIds.contains(ProposalId("testedProposal14")) should be(true)
      chosenIds.contains(ProposalId("testedProposal15")) should be(true)
      chosenIds.contains(ProposalId("testedProposal16")) should be(false)
      chosenIds.contains(ProposalId("testedProposal17")) should be(false)
      chosenIds.contains(ProposalId("testedProposal18")) should be(false)
      chosenIds.contains(ProposalId("testedProposal19")) should be(false)
      chosenIds.contains(ProposalId("testedProposal20")) should be(false)
    }
  }

  feature("idea competition") {
    scenario("check champion selection") {
      val testedProposals: Seq[Proposal] = (1 to 20).map { i =>
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
        fakeProposalQualif(ProposalId(s"testedProposal$i"), votes)
      }

      val champion = selectionAlgorithm.chooseChampion(testedProposals)

      champion.proposalId.value should be("testedProposal20")
    }

    scenario("check idea selection") {
      val ideasWithChampion: Map[IdeaId, Proposal] = (1 to 20).map { i =>
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
        (ideaId, fakeProposalQualif(ProposalId(s"testedProposal$i"), votes, Some(ideaId)))
      }.toMap

      val ideas = selectionAlgorithm.selectIdeasWithChampions(ideasWithChampion, 5)

      ideas.length should be(5)

      ProposalScorerHelper.random = new MersenneTwister(0)
      val counts = new mutable.HashMap[IdeaId, Int]() {
        override def default(key: IdeaId) = 0
      }

      val samples = 1000
      for (_ <- 1 to samples) {
        selectionAlgorithm
          .selectIdeasWithChampions(ideasWithChampion, 5)
          .foreach(counts(_) += 1)
      }

      val proportions: mutable.Map[IdeaId, Double] = counts.map {
        case (i, p) => (i, p.toDouble / samples)
      }

      val confidenceInterval: Double = 0.03
      proportions(IdeaId("Idea20")) should equal(1.0 +- confidenceInterval)
      proportions(IdeaId("Idea15")) should equal(0.13 +- confidenceInterval)
    }

    scenario("check controversial idea selection") {
      val ideasWithChampion: Map[IdeaId, Proposal] = (1 to 20).map { i =>
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
        (ideaId, fakeProposalQualif(ProposalId(s"testedProposal$i"), votes, Some(ideaId)))
      }.toMap

      val ideas = selectionAlgorithm.selectControversialIdeasWithChampions(ideasWithChampion, 5)

      ideas.length should be(5)

      ProposalScorerHelper.random = new MersenneTwister(0)
      val counts = new mutable.HashMap[IdeaId, Int]() {
        override def default(key: IdeaId) = 0
      }

      val samples = 1000
      for (_ <- 1 to samples) {
        selectionAlgorithm
          .selectControversialIdeasWithChampions(ideasWithChampion, 5)
          .foreach(counts(_) += 1)
      }

      val proportions: mutable.Map[IdeaId, Double] = counts.map {
        case (i, p) => (i, p.toDouble / samples)
      }

      val confidenceInterval: Double = 0.03
      proportions(IdeaId("Idea10")) should equal(1.0 +- confidenceInterval)
      proportions(IdeaId("Idea7")) should equal(0.06 +- confidenceInterval)
    }

    scenario("check tested idea selection with idea competition") {
      val ideaCompetitionConfiguration = SequenceConfiguration(
        sequenceId = SequenceId("test-sequence"),
        newProposalsRatio = 0.5,
        newProposalsVoteThreshold = 10,
        testedProposalsEngagementThreshold = 0.0,
        testedProposalsScoreThreshold = 0.0,
        testedProposalsControversyThreshold = 0.0,
        banditEnabled = true,
        banditMinCount = 1,
        banditProposalsRatio = 0.0,
        ideaCompetitionEnabled = true,
        ideaCompetitionTargetCount = 20,
        ideaCompetitionControversialRatio = 0.0,
        ideaCompetitionControversialCount = 2
      )

      val testedProposals: Seq[Proposal] = (1 to 100).map { i =>
        val a = 600
        val d = 200
        val n = 200
        val votes: Map[VoteKey, (Int, Map[QualificationKey, Int])] = Map(
          VoteKey.Agree -> (
            a ->
              Map(QualificationKey.LikeIt -> 3 * i)
          ),
          VoteKey.Disagree -> (
            d ->
              Map(QualificationKey.NoWay -> 3 * (101 - i))
          ),
          VoteKey.Neutral -> (n -> Map(QualificationKey.DoNotCare -> 0))
        )
        fakeProposalQualif(ProposalId(s"testedProposal$i"), votes)
      }

      UniformRandom.random = new Random(0)
      ProposalScorerHelper.random = new MersenneTwister(0)
      selectionAlgorithm.random = new Random(0)

      val chosen: Seq[Proposal] =
        selectionAlgorithm.chooseTestedProposals(ideaCompetitionConfiguration, testedProposals, 10)
      chosen.length should be(10)

      val counts = new mutable.HashMap[ProposalId, Int]() {
        override def default(key: ProposalId) = 0
      }

      val samples = 1000
      for (_ <- 1 to samples) {
        selectionAlgorithm
          .chooseTestedProposals(ideaCompetitionConfiguration, testedProposals, 10)
          .foreach(p => counts(p.proposalId) += 1)
      }

      val proportions: mutable.Map[ProposalId, Double] = counts.map {
        case (i, p) => (i, p.toDouble / samples)
      }

      val confidenceInterval: Double = 0.03
      proportions(ProposalId("testedProposal100")) should equal(1.0 +- confidenceInterval)
      proportions(ProposalId("testedProposal51")) should equal(0.347 +- confidenceInterval)
    }
  }
}
