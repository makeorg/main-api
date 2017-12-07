package org.make.api.sequence

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import com.typesafe.scalalogging.StrictLogging
import org.make.api.MakeTest
import org.make.api.proposal.SelectionAlgorithm
import org.make.api.sequence.SelectionAlgorithmTest.fakeProposal
import org.make.core.proposal._
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.user.UserId
import org.make.core.{proposal, DateHelper, RequestContext}

import scala.concurrent.Future
import scala.util.Random

class SelectionAlgorithmTest extends MakeTest with StrictLogging {

  val defaultThreshold = 100
  val defaultSize = 12
  val proposalIds: Seq[ProposalId] = (1 to defaultSize).map(i => ProposalId(s"proposal$i"))

  feature("proposal selection algorithm with new proposals") {
    scenario("no duplicates with enough proposals only new proposals") {

      val proposals: Seq[Proposal] =
        proposalIds.map(id => fakeProposal(id, Map.empty, Seq.empty))

      val selectedProposals =
        SelectionAlgorithm.newProposalsForSequence(defaultSize, proposals, Seq.empty, defaultThreshold, Seq.empty)

      selectedProposals.size should be(defaultSize)
      selectedProposals.toSet.size should be(defaultSize)
    }

    scenario("no duplicates with enough proposals and include list only new proposals") {

      val proposals: Seq[Proposal] =
        proposalIds.map(id => fakeProposal(id, Map.empty, Seq.empty))

      val sequenceProposals = SelectionAlgorithm.newProposalsForSequence(
        targetLength = defaultSize,
        proposals = proposals,
        votedProposals = Seq.empty,
        newProposalVoteCount = defaultThreshold,
        includeList = Seq(ProposalId("Included 1"), ProposalId("Included 2"), ProposalId("Included 3"))
      )

      sequenceProposals.size should be(defaultSize)
      sequenceProposals.toSet.size should be(defaultSize)
      sequenceProposals.contains(ProposalId("Included 1")) should be(true)
      sequenceProposals.contains(ProposalId("Included 2")) should be(true)
      sequenceProposals.contains(ProposalId("Included 3")) should be(true)

    }

    scenario("duplicates with enough proposals only new proposals") {

      val duplicates = Map(
        ProposalId("proposal1") -> Seq(ProposalId("proposal2")),
        ProposalId("proposal2") -> Seq(ProposalId("proposal1"))
      )

      val proposals: Seq[Proposal] =
        proposalIds.map(id => fakeProposal(id, Map.empty, duplicates.getOrElse(id, Seq.empty)))

      val sequenceProposals =
        SelectionAlgorithm.newProposalsForSequence(defaultSize, proposals, Seq.empty, defaultThreshold, Seq.empty)

      sequenceProposals.size should be(defaultSize - 1)
      sequenceProposals.toSet.size should be(defaultSize - 1)
      if (sequenceProposals.contains(ProposalId("proposal1"))) {
        sequenceProposals.contains(ProposalId("proposal2")) should be(false)
      } else {
        sequenceProposals.contains(ProposalId("proposal2")) should be(true)
      }
    }

    scenario("duplicates from include list with enough proposals only new proposals") {

      val duplicates = Map(
        ProposalId("proposal1") -> Seq(ProposalId("included1")),
        ProposalId("included1") -> Seq(ProposalId("proposal1"))
      )

      val proposals: Seq[Proposal] =
        proposalIds.map(id => fakeProposal(id, Map.empty, duplicates.getOrElse(id, Seq.empty)))

      val sequenceProposals = SelectionAlgorithm.newProposalsForSequence(
        defaultSize,
        proposals,
        Seq.empty,
        defaultThreshold,
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
        proposalIds.map(id => fakeProposal(id, Map(VoteKey.Agree -> 200), Seq.empty))

      val selectedProposals =
        SelectionAlgorithm.newProposalsForSequence(defaultSize, proposals, Seq.empty, defaultThreshold, Seq.empty)

      selectedProposals.size should be(defaultSize)
      selectedProposals.toSet.size should be(defaultSize)
    }

    scenario("no duplicates with enough proposals and include list only tested proposals") {

      val proposals: Seq[Proposal] =
        proposalIds.map(id => fakeProposal(id, Map(VoteKey.Agree -> 200), Seq.empty))

      val sequenceProposals = SelectionAlgorithm.newProposalsForSequence(
        targetLength = defaultSize,
        proposals = proposals,
        votedProposals = Seq.empty,
        newProposalVoteCount = defaultThreshold,
        includeList = Seq(ProposalId("Included 1"), ProposalId("Included 2"), ProposalId("Included 3"))
      )

      sequenceProposals.size should be(defaultSize)
      sequenceProposals.toSet.size should be(defaultSize)
      sequenceProposals.contains(ProposalId("Included 1")) should be(true)
      sequenceProposals.contains(ProposalId("Included 2")) should be(true)
      sequenceProposals.contains(ProposalId("Included 3")) should be(true)

    }

    scenario("duplicates with enough proposals only tested proposals") {

      val duplicates = Map(
        ProposalId("proposal1") -> Seq(ProposalId("proposal2")),
        ProposalId("proposal2") -> Seq(ProposalId("proposal1"))
      )

      val proposals: Seq[Proposal] =
        proposalIds.map(id => fakeProposal(id, Map(VoteKey.Agree -> 200), duplicates.getOrElse(id, Seq.empty)))

      val sequenceProposals =
        SelectionAlgorithm.newProposalsForSequence(defaultSize, proposals, Seq.empty, defaultThreshold, Seq.empty)

      sequenceProposals.size should be(defaultSize - 1)
      sequenceProposals.toSet.size should be(defaultSize - 1)
      if (sequenceProposals.contains(ProposalId("proposal1"))) {
        sequenceProposals.contains(ProposalId("proposal2")) should be(false)
      } else {
        sequenceProposals.contains(ProposalId("proposal2")) should be(true)
      }
    }

    scenario("duplicates from include list with enough proposals only tested proposals") {

      val duplicates = Map(
        ProposalId("proposal1") -> Seq(ProposalId("included1")),
        ProposalId("included1") -> Seq(ProposalId("proposal1"))
      )

      val proposals: Seq[Proposal] =
        proposalIds.map(id => fakeProposal(id, Map(VoteKey.Agree -> 200), duplicates.getOrElse(id, Seq.empty)))

      val sequenceProposals = SelectionAlgorithm.newProposalsForSequence(
        defaultSize,
        proposals,
        Seq.empty,
        defaultThreshold,
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
          id => fakeProposal(id, Map(VoteKey.Agree -> (if (switch.getAndSwitch()) { 200 } else { 50 })), Seq.empty)
        )

      val selectedProposals =
        SelectionAlgorithm.newProposalsForSequence(defaultSize, proposals, Seq.empty, defaultThreshold, Seq.empty)

      selectedProposals.size should be(defaultSize)
      selectedProposals.toSet.size should be(defaultSize)
    }

    scenario("no duplicates with enough proposals and include list") {

      val switch = new Switch()

      val proposals: Seq[Proposal] =
        proposalIds.map(
          id => fakeProposal(id, Map(VoteKey.Agree -> (if (switch.getAndSwitch()) { 200 } else { 50 })), Seq.empty)
        )

      val sequenceProposals = SelectionAlgorithm.newProposalsForSequence(
        targetLength = defaultSize,
        proposals = proposals,
        votedProposals = Seq.empty,
        newProposalVoteCount = defaultThreshold,
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

      val duplicates = Map(
        ProposalId("proposal1") -> Seq(ProposalId("proposal2")),
        ProposalId("proposal2") -> Seq(ProposalId("proposal1"))
      )

      val proposals: Seq[Proposal] =
        proposalIds.map(
          id =>
            fakeProposal(
              id,
              Map(VoteKey.Agree -> (if (switch.getAndSwitch()) { 200 } else { 50 })),
              duplicates.getOrElse(id, Seq.empty)
          )
        )

      val sequenceProposals =
        SelectionAlgorithm.newProposalsForSequence(defaultSize, proposals, Seq.empty, defaultThreshold, Seq.empty)

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

      val duplicates = Map(
        ProposalId("proposal1") -> Seq(ProposalId("included1")),
        ProposalId("included1") -> Seq(ProposalId("proposal1"))
      )

      val proposals: Seq[Proposal] =
        proposalIds.map(
          id =>
            fakeProposal(
              id,
              Map(VoteKey.Agree -> (if (switch.getAndSwitch()) { 200 } else { 50 })),
              duplicates.getOrElse(id, Seq.empty)
          )
        )

      val sequenceProposals = SelectionAlgorithm.newProposalsForSequence(
        defaultSize,
        proposals,
        Seq.empty,
        defaultThreshold,
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
              Seq.empty,
              ZonedDateTime.parse("2017-12-07T16:00:00Z").plus(i, ChronoUnit.MINUTES)
            )
        }

      val newProposalsRandom = Random.shuffle(newProposals)

      val testedProposals: Seq[Proposal] =
        testedProposalIds.map(id => fakeProposal(id, Map(VoteKey.Agree -> 200), Seq.empty))

      val proposals: Seq[Proposal] = newProposalsRandom ++ testedProposals

      val sequenceProposals =
        SelectionAlgorithm.newProposalsForSequence(defaultSize, proposals, Seq.empty, defaultThreshold, Seq.empty)

      logger.debug(sequenceProposals.map(_.value).sorted.mkString(", "))

      sequenceProposals.size should be(defaultSize)
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

  }

}

class Switch {
  private var current = false

  def getAndSwitch(): Boolean = {
    val result = current
    current = !current
    result
  }
}

object SelectionAlgorithmTest extends StrictLogging {

  final case class GetSearchSpaceBuilder(proposals: Seq[IndexedProposal], proposalsByBatch: Int) {
    def searchSpace(excluded: Seq[ProposalId]): Future[Seq[IndexedProposal]] = {
      Future.successful(proposals.filter(p => !excluded.contains(p.id)).take(proposalsByBatch))
    }
  }

  def fakeProposal(id: ProposalId,
                   votes: Map[VoteKey, Int],
                   duplicates: Seq[ProposalId],
                   createdAt: ZonedDateTime = DateHelper.now()): Proposal = {
    proposal.Proposal(
      proposalId = id,
      author = UserId("fake"),
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
      similarProposals = duplicates,
      events = Nil,
      creationContext = RequestContext.empty
    )
  }
}
