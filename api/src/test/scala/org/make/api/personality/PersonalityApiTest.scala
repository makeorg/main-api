package org.make.api.personality

import akka.http.scaladsl.model.StatusCodes
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.api.user.{UserResponse, UserService, UserServiceComponent}
import org.make.core.user.{UserId, UserType}
import org.mockito.Mockito

import scala.concurrent.Future

class PersonalityApiTest extends MakeApiTestBase with DefaultPersonalityApiComponent with UserServiceComponent {

  override val userService: UserService = mock[UserService]

  val routes = personalityApi.routes

  feature("get personality") {
    scenario("get existing personality") {
      val returnedPersonality = TestUtils.user(
        id = UserId("personality-id"),
        email = "personality@make.org",
        enabled = true,
        emailVerified = true,
        userType = UserType.UserTypePersonality
      )

      Mockito
        .when(userService.getPersonality(UserId("personality-id")))
        .thenReturn(Future.successful(Some(returnedPersonality)))

      Get("/personalities/personality-id") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val organisation: UserResponse = entityAs[UserResponse]
        organisation.userId should be(UserId("personality-id"))
      }
    }

    scenario("get non existing organisation") {
      Mockito
        .when(userService.getPersonality(UserId("non-existant")))
        .thenReturn(Future.successful(None))

      Get("/personalities/non-existant") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

}
