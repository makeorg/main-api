package org.make.api.proposal

import org.make.api.MakeTest
import org.make.core.RequestContext
import org.make.core.proposal._
import org.make.core.reference.IdeaId
import org.make.core.user.UserId

class DuplicateAlgorithmTest extends MakeTest {

  feature("take a single proposal from each cluster") {
    scenario("when proposals list is empty") {
      Given("A empty list of proposals")
      When("An user wants to get a list of unique ideas")
      Then("the returned list should be empty")
      val chosenProposals = DuplicateAlgorithm.getUniqueIdeas(proposals = Seq.empty)
      chosenProposals should be(Seq.empty)
    }

    scenario("when proposal list is not empty") {
      Given("A list of proposals")
      When("An user wants to get a list of unique ideas")
      Then("the returned list should contains only one proposal per idea")
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
    Given("A list of proposals with no idea assigned")
    When("An user wants to get a list of unique ideas")
    Then("the returned list should be empy")
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

    /* Todo: this test is not implemented */
    scenario("given a target and a list of candidates") {
      Given("A list of proposals")
      When("An user wants to get a list of suggested proposals")
      Then("the returned list shoud be a sorted list of matching proposals")

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
