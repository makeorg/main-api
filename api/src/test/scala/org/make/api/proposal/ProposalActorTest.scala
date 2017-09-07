package org.make.api.proposal

import java.time.ZonedDateTime

import akka.actor.ActorRef
import akka.testkit.TestKit
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ShardingActorTest
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal._
import org.make.core.reference.{LabelId, TagId, ThemeId}
import org.make.core.user.Role.RoleCitizen
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationFailedError}
import org.scalatest.GivenWhenThen

class ProposalActorTest extends ShardingActorTest with GivenWhenThen with StrictLogging {

  val coordinator: ActorRef =
    system.actorOf(ProposalCoordinator.props, ProposalCoordinator.name)

  val mainUserId: UserId = UserId("1234")
  val mainCreatedAt: Option[ZonedDateTime] = Some(DateHelper.now().minusSeconds(10))
  val mainUpdatedAt: Option[ZonedDateTime] = Some(DateHelper.now())

  val user: User = User(
    userId = mainUserId,
    email = "john.snow@the-night-watch.com",
    firstName = None,
    lastName = None,
    lastIp = None,
    hashedPassword = None,
    verified = true,
    enabled = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    resetToken = None,
    verificationTokenExpiresAt = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleCitizen),
    profile = None
  )

  private def proposal(proposalId: ProposalId) = Proposal(
    proposalId = proposalId,
    author = mainUserId,
    content = "This is a proposal",
    createdAt = mainCreatedAt,
    updatedAt = None,
    slug = "this-is-a-proposal",
    creationContext = RequestContext.empty,
    labels = Seq(),
    theme = None,
    status = ProposalStatus.Pending,
    tags = Seq(),
    events = List(
      ProposalAction(
        date = mainCreatedAt.get,
        user = mainUserId,
        actionType = "propose",
        arguments = Map("content" -> "This is a proposal")
      )
    )
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
        requestContext = RequestContext.empty,
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
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal"
      )

      expectMsg(proposalId)

      When("updating this Proposal")
      coordinator ! UpdateProposalCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
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
        requestContext = RequestContext.empty,
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

  feature("accept a proposal") {
    scenario("accepting a non existing proposal") {
      Given("no proposal corresponding to id 'nothing-there'")
      When("I try to accept the proposal")
      coordinator ! AcceptProposalCommand(
        proposalId = ProposalId("nothing-there"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        theme = Some(ThemeId("my theme")),
        labels = Seq(),
        tags = Seq(TagId("some tag id")),
        similarProposals = Seq()
      )

      Then("I should receive 'None' since nothing is found")
      val error = expectMsgType[ValidationFailedError]
      error.errors.head.field should be("unknown")
      error.errors.head.message should be(Some("Proposal nothing-there doesn't exist"))

    }

    scenario("accept an existing proposal changing the text") {
      Given("a freshly created proposal")
      val originalContent = "This is a proposal that will be validated"
      coordinator ! ProposeCommand(
        ProposalId("to-be-moderated-changing-text"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      When("I validate the proposal")
      coordinator ! AcceptProposalCommand(
        proposalId = ProposalId("to-be-moderated-changing-text"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = Some("This content must be changed"),
        theme = Some(ThemeId("my theme")),
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        similarProposals = Seq()
      )

      Then("I should receive the accepted proposal with modified content")

      val response: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to accept given proposal"))

      response.proposalId should be(ProposalId("to-be-moderated-changing-text"))
      response.events.length should be(2)
      response.content should be("This content must be changed")
      response.status should be(Accepted)
      response.author should be(mainUserId)
      response.createdAt.isDefined should be(true)
      response.updatedAt.isDefined should be(true)
      response.tags should be(Seq(TagId("some tag id")))
      response.labels should be(Seq(LabelId("action")))
      response.theme should be(Some(ThemeId("my theme")))

    }

    scenario("accept an existing proposal without changing text") {
      Given("a freshly created proposal")
      val originalContent = "This is a proposal that will be validated"
      coordinator ! ProposeCommand(
        ProposalId("to-be-moderated"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      When("I validate the proposal")
      coordinator ! AcceptProposalCommand(
        proposalId = ProposalId("to-be-moderated"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        theme = Some(ThemeId("my theme")),
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        similarProposals = Seq()
      )

      Then("I should receive the accepted proposal")

      val response: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to accept given proposal"))

      response.proposalId should be(ProposalId("to-be-moderated"))
      response.events.length should be(2)
      response.content should be(originalContent)
      response.status should be(Accepted)
      response.author should be(mainUserId)
      response.createdAt.isDefined should be(true)
      response.updatedAt.isDefined should be(true)
      response.tags should be(Seq(TagId("some tag id")))
      response.labels should be(Seq(LabelId("action")))
      response.theme should be(Some(ThemeId("my theme")))

    }

    scenario("validating a validated proposal shouldn't do anything") {
      Given("a validated proposal")
      val originalContent = "This is a proposal that will be validated"
      coordinator ! ProposeCommand(
        ProposalId("to-be-moderated-2"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = ProposalId("to-be-moderated-2"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        theme = Some(ThemeId("my theme")),
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        similarProposals = Seq()
      )

      val response: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      When("I re-validate the proposal")
      coordinator ! AcceptProposalCommand(
        proposalId = ProposalId("to-be-moderated-2"),
        moderator = UserId("some other user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = Some("something different"),
        theme = Some(ThemeId("my theme 2")),
        labels = Seq(LabelId("action2")),
        tags = Seq(TagId("some tag id 2")),
        similarProposals = Seq()
      )

      Then("I should receive an error")
      val error = expectMsgType[ValidationFailedError]
      error.errors.head.field should be("unknown")
      error.errors.head.message should be(Some("Proposal to-be-moderated-2 is already validated"))

    }

  }

}
