package org.make.api.proposition

import java.time.ZonedDateTime

import akka.actor.ActorRef
import akka.testkit.TestKit
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ShardingActorTest
import org.make.core.proposition._
import org.make.core.user.UserId
import org.scalatest.GivenWhenThen

class PropositionActorTest extends ShardingActorTest with GivenWhenThen with StrictLogging {

  val coordinator: ActorRef =
    system.actorOf(PropositionCoordinator.props, PropositionCoordinator.name)

  val mainUserId: UserId = UserId("1234")
  val mainCreatedAt: ZonedDateTime = ZonedDateTime.now.minusSeconds(10)
  val mainUpdatedAt: ZonedDateTime = ZonedDateTime.now

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  feature("Propose a proposition") {
    val propositionId: PropositionId = PropositionId("proposeCommand")
    scenario("Initialize the state if it was empty") {
      Given("an empty state")
      coordinator ! GetProposition(propositionId)
      expectMsg(None)

      And("a newly proposed Proposition")
      coordinator ! ProposeCommand(
        propositionId = propositionId,
        userId = mainUserId,
        createdAt = mainCreatedAt,
        content = "Ceci est une proposition"
      )

      expectMsg(
        Some(
          Proposition(
            propositionId = propositionId,
            userId = mainUserId,
            content = "Ceci est une proposition",
            createdAt = mainCreatedAt,
            updatedAt = mainCreatedAt
          )
        )
      )

      Then("have the proposition state after proposal")

      coordinator ! GetProposition(propositionId)

      expectMsg(
        Some(
          Proposition(
            propositionId = propositionId,
            userId = mainUserId,
            content = "Ceci est une proposition",
            createdAt = mainCreatedAt,
            updatedAt = mainCreatedAt
          )
        )
      )

      And("recover its state after having been kill")
      coordinator ! KillPropositionShard(propositionId)

      Thread.sleep(100)

      coordinator ! GetProposition(propositionId)

      expectMsg(
        Some(
          Proposition(
            propositionId = propositionId,
            userId = mainUserId,
            content = "Ceci est une proposition",
            createdAt = mainCreatedAt,
            updatedAt = mainCreatedAt
          )
        )
      )
    }
  }

  feature("Update a proposition") {
    val propositionId: PropositionId = PropositionId("updateCommand")
    scenario("Fail if PropositionId doesn't exists") {
      Given("an empty state")
      coordinator ! GetProposition(propositionId)
      expectMsg(None)

      When("a asking for a fake PropositionId")
      coordinator ! UpdatePropositionCommand(
        propositionId = PropositionId("fake"),
        updatedAt = mainUpdatedAt,
        content = "An updated content"
      )

      Then("returns None")
      expectMsg(None)
    }

    scenario("Change the state and create a snapshot if valid") {
      Given("an empty state")
      coordinator ! GetProposition(propositionId)
      expectMsg(None)

      And("a newly proposed Proposition")
      coordinator ! ProposeCommand(
        propositionId = propositionId,
        userId = mainUserId,
        createdAt = mainCreatedAt,
        content = "Ceci est une proposition"
      )

      expectMsg(
        Some(
          Proposition(
            propositionId = propositionId,
            userId = mainUserId,
            content = "Ceci est une proposition",
            createdAt = mainCreatedAt,
            updatedAt = mainCreatedAt
          )
        )
      )

      When("updating this Proposition")
      coordinator ! UpdatePropositionCommand(
        propositionId = propositionId,
        updatedAt = mainUpdatedAt,
        content = "An updated content"
      )

      expectMsg(
        Some(
          Proposition(
            propositionId = propositionId,
            userId = mainUserId,
            content = "An updated content",
            createdAt = mainCreatedAt,
            updatedAt = mainUpdatedAt
          )
        )
      )

      Then("getting its updated state after update")
      coordinator ! GetProposition(propositionId)

      expectMsg(
        Some(
          Proposition(
            propositionId = propositionId,
            userId = mainUserId,
            content = "An updated content",
            createdAt = mainCreatedAt,
            updatedAt = mainUpdatedAt
          )
        )
      )

      And("recover its updated state after having been kill")
      coordinator ! KillPropositionShard(propositionId)

      Thread.sleep(100)

      coordinator ! GetProposition(propositionId)

      expectMsg(
        Some(
          Proposition(
            propositionId = propositionId,
            userId = mainUserId,
            content = "An updated content",
            createdAt = mainCreatedAt,
            updatedAt = mainUpdatedAt
          )
        )
      )
    }
  }

  feature("View a proposition") {
    val propositionId: PropositionId = PropositionId("viewCommand")
    scenario("Fail if PropositionId doesn't exists") {
      Given("an empty state")
      coordinator ! GetProposition(propositionId)
      expectMsg(None)

      When("a asking for a fake PropositionId")
      coordinator ! ViewPropositionCommand(PropositionId("fake"))

      Then("returns None")
      expectMsg(None)
    }

    scenario("Return the state if valid") {
      Given("an empty state")
      coordinator ! GetProposition(propositionId)
      expectMsg(None)

      When("a new Proposition is proposed")
      coordinator ! ProposeCommand(
        propositionId = propositionId,
        userId = mainUserId,
        createdAt = mainCreatedAt,
        content = "Ceci est une proposition"
      )

      expectMsg(
        Some(
          Proposition(
            propositionId = propositionId,
            userId = mainUserId,
            content = "Ceci est une proposition",
            createdAt = mainCreatedAt,
            updatedAt = mainCreatedAt
          )
        )
      )

      Then("returns the state")
      coordinator ! ViewPropositionCommand(propositionId)
      expectMsg(
        Some(
          Proposition(
            propositionId = propositionId,
            userId = mainUserId,
            content = "Ceci est une proposition",
            createdAt = mainCreatedAt,
            updatedAt = mainCreatedAt
          )
        )
      )
    }
  }
}
