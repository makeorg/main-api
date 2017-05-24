package org.make.api.citizen

import java.time.LocalDate

import akka.actor.ActorRef
import akka.testkit.TestKit
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ShardingActorTest
import org.make.core.citizen.{Citizen, CitizenId, GetCitizen, RegisterCommand, _}
import org.scalatest.GivenWhenThen

class CitizenActorTest extends ShardingActorTest with GivenWhenThen with StrictLogging {

  var coordinator: ActorRef = system.actorOf(CitizenCoordinator.props, CitizenCoordinator.name)

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "Register a citizen" should {
    val citizenId = CitizenId("1234")
    "Initialize the state if it was empty" in {

      Given("an empty state")
      coordinator ! GetCitizen(citizenId)
      expectMsg(None)

      And("a newly registered Citizen")
      coordinator ! RegisterCommand(
        citizenId = citizenId,
        email = "robb.stark@make.org",
        dateOfBirth = LocalDate.parse("1970-01-01"),
        firstName = "Robb",
        lastName = "Stark"
      )

      expectMsg(
        Some(
          Citizen(
            citizenId = citizenId,
            email = "robb.stark@make.org",
            dateOfBirth = LocalDate.parse("1970-01-01"),
            firstName = "Robb",
            lastName = "Stark"
          )
        )
      )

      Then("have the citizen state after registration")

      coordinator ! GetCitizen(citizenId)

      expectMsg(
        Some(
          Citizen(
            citizenId = citizenId,
            email = "robb.stark@make.org",
            dateOfBirth = LocalDate.parse("1970-01-01"),
            firstName = "Robb",
            lastName = "Stark"
          )
        )
      )

      And("recover its state after having been kill")
      coordinator ! KillCitizenShard(citizenId)

      Thread.sleep(100)

      coordinator ! GetCitizen(citizenId)

      expectMsg(
        Some(
          Citizen(
            citizenId = citizenId,
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