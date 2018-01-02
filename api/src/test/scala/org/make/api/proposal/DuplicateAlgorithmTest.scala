package org.make.api.proposal

import org.make.api.MakeTest
import org.make.core.RequestContext
import org.make.core.proposal._
import org.make.core.reference.IdeaId
import org.make.core.user.UserId

class DuplicateAlgorithmTest extends MakeTest {

  feature("take a single proposal from each cluster") {
    scenario("when proposals list is empty") {
      val chosenProposals = DuplicateAlgorithm.getUniqueIdeas(proposals = Seq.empty)
      chosenProposals should be(Seq.empty)
    }

    scenario("when proposal list is not empty") {
      val proposals = Seq(
        Proposal(
          proposalId = ProposalId("1"),
          author = UserId("0"),
          content = "This is a proposal",
          createdAt = None,
          updatedAt = None,
          slug = "this-is-a-proposal",
          creationContext = RequestContext.empty,
          labels = Seq(),
          theme = None,
          status = ProposalStatus.Pending,
          tags = Seq(),
          votes = Seq.empty,
          events = List.empty,
          similarProposals = Seq.empty,
          idea = Some(IdeaId("Idea 1"))
        ),
        Proposal(
          proposalId = ProposalId("2"),
          author = UserId("0"),
          content = "This is a proposal",
          createdAt = None,
          updatedAt = None,
          slug = "this-is-a-proposal",
          creationContext = RequestContext.empty,
          labels = Seq(),
          theme = None,
          status = ProposalStatus.Pending,
          tags = Seq(),
          votes = Seq.empty,
          events = List.empty,
          similarProposals = Seq.empty,
          idea = Some(IdeaId("Idea 1"))
        ),
        Proposal(
          proposalId = ProposalId("3"),
          author = UserId("0"),
          content = "This is a proposal",
          createdAt = None,
          updatedAt = None,
          slug = "this-is-a-proposal",
          creationContext = RequestContext.empty,
          labels = Seq(),
          theme = None,
          status = ProposalStatus.Pending,
          tags = Seq(),
          votes = Seq.empty,
          events = List.empty,
          similarProposals = Seq.empty,
          idea = Some(IdeaId("Idea 2"))
        )
      )
      val chosenProposals = DuplicateAlgorithm.getUniqueIdeas(proposals = proposals)
      chosenProposals.map(_.value).mkString(",") should be("3,1")
    }
  }

  scenario("when ideas are null") {
    val proposals = Seq(
      Proposal(
        proposalId = ProposalId("1"),
        author = UserId("0"),
        content = "This is a proposal",
        createdAt = None,
        updatedAt = None,
        slug = "this-is-a-proposal",
        creationContext = RequestContext.empty,
        labels = Seq(),
        theme = None,
        status = ProposalStatus.Pending,
        tags = Seq(),
        votes = Seq.empty,
        events = List.empty,
        similarProposals = Seq.empty,
        idea = None
      ),
      Proposal(
        proposalId = ProposalId("2"),
        author = UserId("0"),
        content = "This is a proposal",
        createdAt = None,
        updatedAt = None,
        slug = "this-is-a-proposal",
        creationContext = RequestContext.empty,
        labels = Seq(),
        theme = None,
        status = ProposalStatus.Pending,
        tags = Seq(),
        votes = Seq.empty,
        events = List.empty,
        similarProposals = Seq.empty,
        idea = None
      ),
      Proposal(
        proposalId = ProposalId("3"),
        author = UserId("0"),
        content = "This is a proposal",
        createdAt = None,
        updatedAt = None,
        slug = "this-is-a-proposal",
        creationContext = RequestContext.empty,
        labels = Seq(),
        theme = None,
        status = ProposalStatus.Pending,
        tags = Seq(),
        votes = Seq.empty,
        events = List.empty,
        similarProposals = Seq.empty,
        idea = None
      )
    )
    val chosenProposals = DuplicateAlgorithm.getUniqueIdeas(proposals = proposals)
    chosenProposals should be(Seq.empty)
  }

  feature("get duplicates for a given proposal") {
    scenario("given a target and a list of candidates") {
      val proposals = Seq(
        Proposal(
          proposalId = ProposalId("1"),
          author = UserId("0"),
          content = "Bonjour,",
          createdAt = None,
          updatedAt = None,
          slug = "this-is-a-proposal",
          creationContext = RequestContext.empty,
          labels = Seq(),
          theme = None,
          status = ProposalStatus.Pending,
          tags = Seq(),
          votes = Seq.empty,
          events = List.empty,
          similarProposals = Seq(ProposalId("2"))
        ),
        Proposal(
          proposalId = ProposalId("2"),
          author = UserId("0"),
          content = "This is a proposal",
          createdAt = None,
          updatedAt = None,
          slug = "this-is-a-proposal",
          creationContext = RequestContext.empty,
          labels = Seq(),
          theme = None,
          status = ProposalStatus.Pending,
          tags = Seq(),
          votes = Seq.empty,
          events = List.empty,
          similarProposals = Seq.empty
        ),
        Proposal(
          proposalId = ProposalId("3"),
          author = UserId("0"),
          content = "This is a proposal",
          createdAt = None,
          updatedAt = None,
          slug = "this-is-a-proposal",
          creationContext = RequestContext.empty,
          labels = Seq(),
          theme = None,
          status = ProposalStatus.Pending,
          tags = Seq(),
          votes = Seq.empty,
          events = List.empty,
          similarProposals = Seq.empty
        )
      )
    }
  }

}
