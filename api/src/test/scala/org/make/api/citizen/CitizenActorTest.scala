package org.make.api.citizen

import java.time.LocalDate

import akka.actor.{ActorRef, PoisonPill}
import akka.testkit.TestKit
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ShardingActorTest
import org.make.core.citizen.{Citizen, CitizenId, GetCitizen, RegisterCommand, _}
import org.scalatest.GivenWhenThen

class CitizenActorTest extends ShardingActorTest with GivenWhenThen with StrictLogging {

  var coordinator: ActorRef = _

  val mainCitizenId = CitizenId("1234")

  override protected def afterEach(): Unit = {
    coordinator ! PoisonPill
  }

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  override protected def beforeEach(): Unit = {
    coordinator = system.actorOf(CitizenCoordinator.props, CitizenCoordinator.name)
  }

  "Register a citizen" should {
    "Initialize the state if it was empty" in {

      Given("an empty state")
      coordinator ! GetCitizen(mainCitizenId)
      expectMsg(None)

      And("a newly registered Citizen")
      coordinator ! RegisterCommand(
        citizenId = mainCitizenId,
        email = "robb.stark@make.org",
        dateOfBirth = LocalDate.parse("1970-01-01"),
        firstName = "Robb",
        lastName = "Stark"
      )

      expectMsg(
        Some(
          Citizen(
            citizenId = mainCitizenId,
            email = "robb.stark@make.org",
            dateOfBirth = LocalDate.parse("1970-01-01"),
            firstName = "Robb",
            lastName = "Stark"
          )
        )
      )

      Then("have the citizen state after registration")

      coordinator ! GetCitizen(mainCitizenId)

      expectMsg(
        Some(
          Citizen(
            citizenId = mainCitizenId,
            email = "robb.stark@make.org",
            dateOfBirth = LocalDate.parse("1970-01-01"),
            firstName = "Robb",
            lastName = "Stark"
          )
        )
      )

      And("recover its state after having been kill")
      coordinator ! KillCitizenShard(mainCitizenId)

      Thread.sleep(100)

      coordinator ! GetCitizen(mainCitizenId)

      expectMsg(
        Some(
          Citizen(
            citizenId = mainCitizenId,
            email = "robb.stark@make.org",
            dateOfBirth = LocalDate.parse("1970-01-01"),
            firstName = "Robb",
            lastName = "Stark"
          )
        )
      )
    }
  }
}