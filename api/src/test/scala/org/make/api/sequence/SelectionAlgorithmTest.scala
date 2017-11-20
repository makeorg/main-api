package org.make.api.sequence

import com.typesafe.scalalogging.StrictLogging
import org.make.api.MakeTest
import org.make.api.proposal.SelectionAlgorithm
import org.make.api.sequence.SelectionAlgorithmTest.{fakeProposal, GetSearchSpaceBuilder}
import org.make.core.DateHelper
import org.make.core.proposal.{ProposalId, ProposalStatus, VoteKey}
import org.make.core.proposal.indexed.{Author, IndexedProposal, IndexedVote}
import org.make.core.user.UserId
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._
import scala.concurrent.Future

class SelectionAlgorithmTest extends MakeTest with ScalaFutures {

  feature("proposal selection algorithm") {
    scenario("no duplicates with enough proposals") {

      val pickedProposals = 12
      val proposalIds: Seq[ProposalId] = (1 to pickedProposals).map(i => ProposalId(s"proposal$i"))

      val getSearchSpace: (Seq[ProposalId]) => Future[Seq[IndexedProposal]] =
        GetSearchSpaceBuilder(proposalIds.map(id => fakeProposal(id, Map.empty)), pickedProposals).searchSpace

      val futureProposals = SelectionAlgorithm.getProposalsForSequence(
        pickedProposals,
        1,
        getSearchSpace,
        (_)   => Future.successful(Seq.empty),
        (ids) => Future.successful(ids.map(id => fakeProposal(id, Map.empty))),
        Seq.empty
      )

      whenReady(futureProposals, Timeout(2.seconds)) { proposals =>
        proposals.size should be(pickedProposals)
        proposals.map(_.id).toSet.size should be(pickedProposals)
      }
    }

    scenario("no duplicates with enough proposals and include list") {

      val pickedProposals = 12
      val proposalIds: Seq[ProposalId] = (1 to pickedProposals).map(i => ProposalId(s"proposal$i"))

      val getSearchSpace: (Seq[ProposalId]) => Future[Seq[IndexedProposal]] =
        GetSearchSpaceBuilder(proposalIds.map(id => fakeProposal(id, Map.empty)), pickedProposals).searchSpace

      val futureProposals = SelectionAlgorithm.getProposalsForSequence(
        pickedProposals,
        1,
        getSearchSpace,
        (_)   => Future.successful(Seq.empty),
        (ids) => Future.successful(ids.map(id => fakeProposal(id, Map.empty))),
        Seq(ProposalId("Included 1"), ProposalId("Included 2"), ProposalId("Included 3"))
      )

      whenReady(futureProposals, Timeout(2.seconds)) { proposals =>
        proposals.size should be(pickedProposals)
        val proposalIds = proposals.map(_.id).toSet
        proposalIds.size should be(pickedProposals)
        proposalIds.contains(ProposalId("Included 1")) should be(true)
        proposalIds.contains(ProposalId("Included 2")) should be(true)
        proposalIds.contains(ProposalId("Included 3")) should be(true)
      }

    }

    scenario("duplicates with enough proposals") {

      val pickedProposals = 12
      val proposalIds: Seq[ProposalId] = (1 to (pickedProposals + 1)).map(i => ProposalId(s"proposal$i"))

      val getSearchSpace: (Seq[ProposalId]) => Future[Seq[IndexedProposal]] =
        GetSearchSpaceBuilder(proposalIds.map(id => fakeProposal(id, Map.empty)), pickedProposals).searchSpace

      val futureProposals = SelectionAlgorithm.getProposalsForSequence(pickedProposals, 1, getSearchSpace, {
        case ProposalId("proposal1") => Future.successful(Seq(ProposalId("proposal2")))
        case ProposalId("proposal2") => Future.successful(Seq(ProposalId("proposal1")))
        case ProposalId(_)           => Future.successful(Seq.empty)
      }, (ids) => Future.successful(ids.map(id => fakeProposal(id, Map.empty))), Seq.empty)

      whenReady(futureProposals, Timeout(2.seconds)) { proposals =>
        proposals.size should be(pickedProposals)
        val proposalIds = proposals.map(_.id).toSet
        proposalIds.size should be(pickedProposals)
        if (proposalIds.contains(ProposalId("proposal1"))) {
          proposalIds.contains(ProposalId("proposal2")) should be(false)
        } else {
          proposalIds.contains(ProposalId("proposal2")) should be(true)
        }
      }
    }

    scenario("duplicates from include list with not enough proposals") {

      val pickedProposals = 12
      val proposalIds: Seq[ProposalId] = (1 until pickedProposals).map(i => ProposalId(s"proposal$i"))

      val getSearchSpace: (Seq[ProposalId]) => Future[Seq[IndexedProposal]] =
        GetSearchSpaceBuilder(proposalIds.map(id => fakeProposal(id, Map.empty)), pickedProposals).searchSpace

      val futureProposals = SelectionAlgorithm.getProposalsForSequence(pickedProposals, 1, getSearchSpace, {
        case ProposalId("included1") => Future.successful(Seq(ProposalId("proposal1")))
        case ProposalId(_)           => Future.successful(Seq.empty)
      }, (ids) => Future.successful(ids.map(id => fakeProposal(id, Map.empty))), Seq(ProposalId("included1")))

      whenReady(futureProposals, Timeout(2.seconds)) { proposals =>
        proposals.size should be(pickedProposals - 1)
        val proposalIds = proposals.map(_.id).toSet
        proposalIds.size should be(pickedProposals - 1)
        proposalIds.contains(ProposalId("included1")) should be(true)
        proposalIds.contains(ProposalId("proposal1")) should be(false)
      }
    }

  }

}

object SelectionAlgorithmTest extends StrictLogging {

  final case class GetSearchSpaceBuilder(proposals: Seq[IndexedProposal], proposalsByBatch: Int) {
    def searchSpace(excluded: Seq[ProposalId]): Future[Seq[IndexedProposal]] = {
      Future.successful(proposals.filter(p => !excluded.contains(p.id)).take(proposalsByBatch))
    }
  }

  def fakeProposal(id: ProposalId, votes: Map[VoteKey, Int]): IndexedProposal = {
    IndexedProposal(
      id = id,
      userId = UserId("fake"),
      content = "fake",
      slug = "fake",
      status = ProposalStatus.Accepted,
      createdAt = DateHelper.now(),
      updatedAt = None,
      votes = votes.map {
        case (key, amount) => IndexedVote(key = key, count = amount, qualifications = Seq.empty)
      }.toSeq,
      context = None,
      trending = None,
      labels = Seq.empty,
      author = Author(None, None, None),
      country = "FR",
      language = "fr",
      themeId = None,
      tags = Seq.empty
    )
  }
}
