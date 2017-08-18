package org.make.api.proposal

import java.time.{ZoneOffset, ZonedDateTime}

import akka.actor.ActorRef
import akka.testkit.TestKit
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ShardingActorTest
import org.make.core.RequestContext
import org.make.core.proposal._
import org.make.core.user.Role.RoleCitizen
import org.make.core.user.{User, UserId}
import org.scalatest.GivenWhenThen

class ProposalActorTest extends ShardingActorTest with GivenWhenThen with StrictLogging {

  val coordinator: ActorRef =
    system.actorOf(ProposalCoordinator.props, ProposalCoordinator.name)

  val mainUserId: UserId = UserId("1234")
  val mainCreatedAt: Option[ZonedDateTime] = Some(ZonedDateTime.now.minusSeconds(10))
  val mainUpdatedAt: Option[ZonedDateTime] = Some(ZonedDateTime.now)

  val user: User = User(
    userId = mainUserId,
    email = "john.snow@the-night-watch.com",
    firstName = None,
    lastName = None,
    lastIp = None,
    None,
    true,
    true,
    ZonedDateTime.now(ZoneOffset.UTC),
    None,
    None,
    None,
    None,
    Seq(RoleCitizen),
    None
  )

  private def proposal(proposalId: ProposalId) = Proposal(
    proposalId = proposalId,
    author = mainUserId,
    content = "This is a proposal",
    createdAt = mainCreatedAt,
    updatedAt = None,
    slug = "this-is-a-proposal",
    creationContext = RequestContext.empty
  )

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  feature("Propose a proposal") {
    val proposalId: ProposalId = ProposalId("proposeCommand")
    scenario("Initialize the state if it was empty") {
      Given("an empty state")
      coordinator ! GetProposal(proposalId, RequestContext.empty)
      expectMsg(None)

      And("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal"
      )

      expectMsg(proposalId)

      Then("have the proposal state after proposal")

      coordinator ! GetProposal(proposalId, RequestContext.empty)

      expectMsg(Some(proposal(proposalId)))

      And("recover its state after having been kill")
      coordinator ! KillProposalShard(proposalId, RequestContext.empty)

      Thread.sleep(100)

      coordinator ! GetProposal(proposalId, RequestContext.empty)

      expectMsg(Some(proposal(proposalId)))
    }
  }

  feature("Update a proposal") {
    val proposalId: ProposalId = ProposalId("updateCommand")
    scenario("Fail if ProposalId doesn't exists") {
      Given("an empty state")
      coordinator ! GetProposal(proposalId, RequestContext.empty)
      expectMsg(None)

      When("a asking for a fake ProposalId")
      coordinator ! UpdateProposalCommand(
        proposalId = ProposalId("fake"),
        context = RequestContext.empty,
        updatedAt = mainUpdatedAt.get,
        content = "An updated content"
      )

      Then("returns None")
      expectMsg(None)
    }

    scenario("Change the state and create a snapshot if valid") {
      Given("an empty state")
      coordinator ! GetProposal(proposalId, RequestContext.empty)
      expectMsg(None)

      And("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        context = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal"
      )

      expectMsg(proposalId)

      When("updating this Proposal")
      coordinator ! UpdateProposalCommand(
        proposalId = proposalId,
        context = RequestContext.empty,
        updatedAt = mainUpdatedAt.get,
        content = "An updated content"
      )

      val modified = Some(
        proposal(proposalId)
          .copy(content = "An updated content", slug = "an-updated-content", updatedAt = mainUpdatedAt)
      )
      expectMsg(modified)

      Then("getting its updated state after update")
      coordinator ! GetProposal(proposalId, RequestContext.empty)

      expectMsg(modified)

      And("recover its updated state after having been kill")
      coordinator ! KillProposalShard(proposalId, RequestContext.empty)

      Thread.sleep(100)

      coordinator ! GetProposal(proposalId, RequestContext.empty)

      expectMsg(modified)
    }
  }

  feature("View a proposal") {
    val proposalId: ProposalId = ProposalId("viewCommand")
    scenario("Fail if ProposalId doesn't exists") {
      Given("an empty state")
      coordinator ! GetProposal(proposalId, RequestContext.empty)
      expectMsg(None)

      When("a asking for a fake ProposalId")
      coordinator ! ViewProposalCommand(ProposalId("fake"), RequestContext.empty)

      Then("returns None")
      expectMsg(None)
    }

    scenario("Return the state if valid") {
      Given("an empty state")
      coordinator ! GetProposal(proposalId, RequestContext.empty)
      expectMsg(None)

      When("a new Proposal is proposed")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        context = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal"
      )

      expectMsg(proposalId)

      Then("returns the state")
      coordinator ! ViewProposalCommand(proposalId, RequestContext.empty)
      expectMsg(Some(proposal(proposalId)))
    }
  }
}
