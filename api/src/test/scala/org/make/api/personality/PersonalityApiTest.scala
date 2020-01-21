package org.make.api.personality

import akka.http.scaladsl.model.StatusCodes
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.api.user.{UserResponse, UserService, UserServiceComponent}
import org.make.core.user.{UserId, UserType}
import org.mockito.Mockito

import scala.concurrent.Future
import scalaoauth2.provider.AccessToken
import java.{util => ju}
import java.time.Instant
import org.mockito.ArgumentMatchers
import scalaoauth2.provider.AuthInfo
import org.make.core.auth.UserRights
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes
import org.make.core.user.User
import org.make.core.RequestContext
import org.make.core.profile.Profile

class PersonalityApiTest extends MakeApiTestBase with DefaultPersonalityApiComponent with UserServiceComponent {

  override val userService: UserService = mock[UserService]

  val routes = personalityApi.routes

  val otherUser = TestUtils.user(UserId("other"))

  val returnedPersonality = TestUtils.user(
    id = UserId("personality-id"),
    email = "personality@make.org",
    firstName = Some("my-personality"),
    profile = Profile.parseProfile(politicalParty = Some("political-party")),
    enabled = true,
    emailVerified = true,
    userType = UserType.UserTypePersonality
  )

  val usersByToken = Map("user" -> otherUser, "personality" -> returnedPersonality)
  val personalities = Map(returnedPersonality.userId -> returnedPersonality)

  Mockito
    .when(oauth2DataHandler.findAccessToken(ArgumentMatchers.any[String]))
    .thenAnswer { invocation =>
      val token = invocation.getArgument[String](0)
      val maybeAccessToken =
        usersByToken.get(token).map(_ => AccessToken(token, None, None, None, ju.Date.from(Instant.now)))
      Future.successful(maybeAccessToken)
    }

  Mockito
    .when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.any[AccessToken]))
    .thenAnswer(
      invocation =>
        Future.successful(
          usersByToken
            .get(invocation.getArgument[AccessToken](0).token)
            .map(
              user =>
                AuthInfo(
                  UserRights(
                    userId = user.userId,
                    roles = user.roles,
                    availableQuestions = user.availableQuestions,
                    emailVerified = user.emailVerified
                  ),
                  None,
                  None,
                  None
                )
            )
        )
    )

  Mockito
    .when(userService.getPersonality(ArgumentMatchers.any[UserId]))
    .thenAnswer(invocation => Future.successful(personalities.get(invocation.getArgument[UserId](0))))

  Mockito
    .when(userService.update(ArgumentMatchers.any[User], ArgumentMatchers.any[RequestContext]))
    .thenAnswer(invocation => Future.successful(invocation.getArgument[User](0)))

  feature("get personality") {
    scenario("get existing personality") {
      Get("/personalities/personality-id") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val personality: UserResponse = entityAs[UserResponse]
        personality.userId should be(UserId("personality-id"))
      }
    }

    scenario("get non existing organisation") {
      Get("/personalities/non-existant") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  feature("get personality profile") {
    scenario("get existing personality") {
      Get("/personalities/personality-id/profile") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val personality = entityAs[PersonalityProfileResponse]
        personality.firstName should contain("my-personality")
        personality.politicalParty should contain("political-party")
      }
    }

    scenario("get non existing organisation") {
      Get("/personalities/non-existant/profile") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  feature("modify profile") {
    val entity = """
                |{
                |  "firstName": "Morteau",
                |  "lastName": "Chipo",
                |  "avatarUrl": "https://la-saucisse-masquee.org/avatar",
                |  "description": "Des saucisses avec des masques",
                |  "optInNewsletter": true,
                |  "website": "https://les-saucisses-masquees.org",
                |  "politicalParty": "Les saucisses masquées"
                |}
                """.stripMargin

    scenario("unauthentificated modification") {
      Put(s"/personalities/${returnedPersonality.userId.value}/profile")
        .withEntity(HttpEntity(ContentTypes.`application/json`, entity)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("wrong user") {
      Put(s"/personalities/${returnedPersonality.userId.value}/profile")
        .withHeaders(Authorization(OAuth2BearerToken("user")))
        .withEntity(HttpEntity(ContentTypes.`application/json`, entity)) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("correct user") {
      Put(s"/personalities/${returnedPersonality.userId.value}/profile")
        .withHeaders(Authorization(OAuth2BearerToken("personality")))
        .withEntity(HttpEntity(ContentTypes.`application/json`, entity)) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val response = entityAs[PersonalityProfileResponse]
        response.firstName should contain("Morteau")
        response.lastName should contain("Chipo")
        response.avatarUrl should contain("https://la-saucisse-masquee.org/avatar")
        response.description should contain("Des saucisses avec des masques")
        response.optInNewsletter should contain(true)
        response.website should contain("https://les-saucisses-masquees.org")
        response.politicalParty should contain("Les saucisses masquées")
      }
    }
  }

}
