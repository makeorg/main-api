package org.make.api.user

import java.time.LocalDate

import akka.actor.ActorRef
import akka.testkit.TestKit
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ShardingActorTest
import org.make.core.user.{GetUser, RegisterCommand, UserId, _}
import org.scalatest.GivenWhenThen

class UserActorTest extends ShardingActorTest with GivenWhenThen with StrictLogging {

  var coordinator: ActorRef =
    system.actorOf(UserCoordinator.props, UserCoordinator.name)

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "Register a user" should {
    val userId = UserId("1234")
    "Initialize the state if it was empty" in {

      pending

      Given("an empty state")
      coordinator ! GetUser(userId)
      expectMsg(None)

      And("a newly registered User")
      coordinator ! RegisterCommand(
        userId = userId,
        email = "robb.stark@make.org",
        dateOfBirth = LocalDate.parse("1970-01-01"),
        firstName = "Robb",
        lastName = "Stark"
      )


      Then("have the user state after registration")

      coordinator ! GetUser(userId)



      And("recover its state after having been kill")
      coordinator ! KillUserShard(userId)

      Thread.sleep(100)

      coordinator ! GetUser(userId)

    }
  }
}
