package org.make.api.proposal

import java.time.ZonedDateTime

import akka.actor.ActorRef
import akka.testkit.TestKit
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ShardingActorTest
import org.make.core.proposal._
import org.make.core.user.UserId
import org.scalatest.GivenWhenThen

class ProposalActorTest extends ShardingActorTest with GivenWhenThen with StrictLogging {

  val coordinator: ActorRef =
    system.actorOf(ProposalCoordinator.props, ProposalCoordinator.name)

  val mainUserId: UserId = UserId("1234")
  val mainCreatedAt: Option[ZonedDateTime] = Some(ZonedDateTime.now.minusSeconds(10))
  val mainUpdatedAt: Option[ZonedDateTime] = Some(ZonedDateTime.now)

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  feature("Propose a proposal") {
    val proposalId: ProposalId = ProposalId("proposeCommand")
    scenario("Initialize the state if it was empty") {
      Given("an empty state")
      coordinator ! GetProposal(proposalId)
      expectMsg(None)

      And("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        userId = mainUserId,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal"
      )

      expectMsg(
        Some(
          Proposal(
            proposalId = proposalId,
            userId = mainUserId,
            content = "This is a proposal",
            createdAt = mainCreatedAt,
            updatedAt = mainCreatedAt
          )
        )
      )

      Then("have the proposal state after proposal")

      coordinator ! GetProposal(proposalId)

      expectMsg(
        Some(
          Proposal(
            proposalId = proposalId,
            userId = mainUserId,
            content = "This is a proposal",
            createdAt = mainCreatedAt,
            updatedAt = mainCreatedAt
          )
        )
      )

      And("recover its state after having been kill")
      coordinator ! KillProposalShard(proposalId)

      Thread.sleep(100)

      coordinator ! GetProposal(proposalId)

      expectMsg(
        Some(
          Proposal(
            proposalId = proposalId,
            userId = mainUserId,
            content = "This is a proposal",
            createdAt = mainCreatedAt,
            updatedAt = mainCreatedAt
          )
        )
      )
    }
  }

  feature("Update a proposal") {
    val proposalId: ProposalId = ProposalId("updateCommand")
    scenario("Fail if ProposalId doesn't exists") {
      Given("an empty state")
      coordinator ! GetProposal(proposalId)
      expectMsg(None)

      When("a asking for a fake ProposalId")
      coordinator ! UpdateProposalCommand(
        proposalId = ProposalId("fake"),
        updatedAt = mainUpdatedAt.get,
        content = "An updated content"
      )

      Then("returns None")
      expectMsg(None)
    }

    scenario("Change the state and create a snapshot if valid") {
      Given("an empty state")
      coordinator ! GetProposal(proposalId)
      expectMsg(None)

      And("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        userId = mainUserId,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal"
      )

      expectMsg(
        Some(
          Proposal(
            proposalId = proposalId,
            userId = mainUserId,
            content = "This is a proposal",
            createdAt = mainCreatedAt,
            updatedAt = mainCreatedAt
          )
        )
      )

      When("updating this Proposal")
      coordinator ! UpdateProposalCommand(
        proposalId = proposalId,
        updatedAt = mainUpdatedAt.get,
        content = "An updated content"
      )

      expectMsg(
        Some(
          Proposal(
            proposalId = proposalId,
            userId = mainUserId,
            content = "An updated content",
            createdAt = mainCreatedAt,
            updatedAt = mainUpdatedAt
          )
        )
      )

      Then("getting its updated state after update")
      coordinator ! GetProposal(proposalId)

      expectMsg(
        Some(
          Proposal(
            proposalId = proposalId,
            userId = mainUserId,
            content = "An updated content",
            createdAt = mainCreatedAt,
            updatedAt = mainUpdatedAt
          )
        )
      )

      And("recover its updated state after having been kill")
      coordinator ! KillProposalShard(proposalId)

      Thread.sleep(100)

      coordinator ! GetProposal(proposalId)

      expectMsg(
        Some(
          Proposal(
            proposalId = proposalId,
            userId = mainUserId,
            content = "An updated content",
            createdAt = mainCreatedAt,
            updatedAt = mainUpdatedAt
          )
        )
      )
    }
  }

  feature("View a proposal") {
    val proposalId: ProposalId = ProposalId("viewCommand")
    scenario("Fail if ProposalId doesn't exists") {
      Given("an empty state")
      coordinator ! GetProposal(proposalId)
      expectMsg(None)

      When("a asking for a fake ProposalId")
      coordinator ! ViewProposalCommand(ProposalId("fake"))

      Then("returns None")
      expectMsg(None)
    }

    scenario("Return the state if valid") {
      Given("an empty state")
      coordinator ! GetProposal(proposalId)
      expectMsg(None)

      When("a new Proposal is proposed")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        userId = mainUserId,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal"
      )

      expectMsg(
        Some(
          Proposal(
            proposalId = proposalId,
            userId = mainUserId,
            content = "This is a proposal",
            createdAt = mainCreatedAt,
            updatedAt = mainCreatedAt
          )
        )
      )

      Then("returns the state")
      coordinator ! ViewProposalCommand(proposalId)
      expectMsg(
        Some(
          Proposal(
            proposalId = proposalId,
            userId = mainUserId,
            content = "This is a proposal",
            createdAt = mainCreatedAt,
            updatedAt = mainCreatedAt
          )
        )
      )
    }
  }
}
