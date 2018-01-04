package org.make.api.proposal

import org.make.api.MakeTest
import org.make.core.RequestContext
import org.make.core.proposal._
import org.make.core.reference.IdeaId
import org.make.core.user.UserId
import org.make.semantic.text.document.{Document, FeatureDocument}
import org.make.semantic.text.feature.wordvec.WordVecFeature
import org.make.semantic.text.feature.{TokenFT, WordVecFT}
import org.make.semantic.text.model.duplicate.SimilarDocResult

class DuplicateAlgorithmTest extends MakeTest {

  feature("take a single proposal from each cluster") {
    scenario("when proposals list is empty") {
      Given("A empty list of proposals")
      When("An user wants to get a list of unique ideas")
      Then("the returned list should be empty")
      val chosenProposals = DuplicateAlgorithm.getUniqueDuplicateIdeas(duplicates = Seq.empty)
      chosenProposals should be(Seq.empty)
    }

    scenario("when proposal list is not empty") {
      Given("A list of proposals")
      When("An user wants to get a list of unique ideas")
      Then("the returned list should contains only one proposal per idea")
      val duplicates = Seq(
        DuplicateResult(
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
          1.0
        ),
        DuplicateResult(
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
          0.9
        ),
        DuplicateResult(
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
          ),
          0.8
        )
      )
      val chosenProposals = DuplicateAlgorithm.getUniqueDuplicateIdeas(duplicates = duplicates)
      chosenProposals.map(_.proposal.proposalId) should be(Seq(ProposalId("3"), ProposalId("1")))
    }
  }

  scenario("when ideas are null") {
    Given("A list of proposals with no idea assigned")
    When("An user wants to get a list of unique ideas")
    Then("the returned list should be empy")
    val duplicates = Seq(
      DuplicateResult(
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
        1.0
      ),
      DuplicateResult(
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
        0.9
      ),
      DuplicateResult(
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
        ),
        0.8
      )
    )
    val chosenProposals = DuplicateAlgorithm.getUniqueDuplicateIdeas(duplicates = duplicates)
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
          content = "Bonjour",
          createdAt = None,
          updatedAt = None,
          slug = "Bonjour",
          creationContext = RequestContext.empty,
          labels = Seq.empty,
          theme = None,
          status = ProposalStatus.Accepted,
          tags = Seq.empty,
          votes = Seq.empty,
          events = List.empty,
          idea = Some(IdeaId("Idea 1"))
        ),
        Proposal(
          proposalId = ProposalId("2"),
          author = UserId("0"),
          content = "Il faut tester",
          createdAt = None,
          updatedAt = None,
          slug = "this-is-a-proposal",
          creationContext = RequestContext.empty,
          labels = Seq(),
          theme = None,
          status = ProposalStatus.Accepted,
          tags = Seq.empty,
          votes = Seq.empty,
          events = List.empty,
          similarProposals = Seq.empty,
          idea = Some(IdeaId("Idea 2"))
        ),
        Proposal(
          proposalId = ProposalId("3"),
          author = UserId("0"),
          content = "Il faut toujours tester",
          createdAt = None,
          updatedAt = None,
          slug = "this-is-a-proposal",
          creationContext = RequestContext.empty,
          labels = Seq.empty,
          theme = None,
          status = ProposalStatus.Accepted,
          tags = Seq.empty,
          votes = Seq.empty,
          events = List.empty,
          similarProposals = Seq.empty,
          idea = Some(IdeaId("Idea 2"))
        )
      )

      val newProposal = Proposal(
        proposalId = ProposalId("4"),
        author = UserId("0"),
        content = "Il faut encore tester pour les femmes",
        createdAt = None,
        updatedAt = None,
        slug = "this-is-a-test",
        creationContext = RequestContext.empty,
        labels = Seq(),
        theme = None,
        status = ProposalStatus.Pending,
        tags = Seq(),
        votes = Seq.empty,
        events = List.empty,
        similarProposals = Seq.empty
      )

      val document = Document(newProposal.content, "fr")
      val features = FeatureDocument(document, DuplicateAlgorithm.featureExtractor.extract(document))

      features.getFeature(TokenFT).length should be(2)
      features.getFeature(WordVecFT)(0).asInstanceOf[WordVecFeature].vector.length should be(300)

      val suggested: Seq[SimilarDocResult[Proposal]] =
        DuplicateAlgorithm.getPredictedDuplicateResults(newProposal, proposals, 10)

      logger.debug(suggested.toString())

      suggested.length should be(3)
      suggested.head.score should be(0.93)
    }
  }

}
