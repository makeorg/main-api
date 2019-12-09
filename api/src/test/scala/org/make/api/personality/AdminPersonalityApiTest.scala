/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.personality

import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.user.{PersonalityRegisterData, UserService, UserServiceComponent}
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.core.RequestContext
import org.make.core.auth.UserRights
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.{User, UserId, UserType}
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito.when
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class AdminPersonalityApiTest
    extends MakeApiTestBase
    with DefaultAdminPersonalityApiComponent
    with UserServiceComponent
    with MakeDataHandlerComponent {

  override val userService: UserService = mock[UserService]

  val routes: Route = sealRoute(adminPersonalityApi.routes)

  val validAccessToken = "my-valid-access-token"
  val adminToken = "my-admin-access-token"
  val moderatorToken = "my-moderator-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)
  private val adminAccessToken = AccessToken(adminToken, None, None, Some(1234567890L), tokenCreationDate)
  private val moderatorAccessToken = AccessToken(moderatorToken, None, None, Some(1234567890L), tokenCreationDate)

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))
  when(oauth2DataHandler.findAccessToken(adminToken)).thenReturn(Future.successful(Some(adminAccessToken)))
  when(oauth2DataHandler.findAccessToken(moderatorToken)).thenReturn(Future.successful(Some(moderatorAccessToken)))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(accessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(
              userId = UserId("user-citizen"),
              roles = Seq(RoleCitizen),
              availableQuestions = Seq.empty,
              emailVerified = true
            ),
            None,
            Some("user"),
            None
          )
        )
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(
              UserId("user-admin"),
              roles = Seq(RoleAdmin),
              availableQuestions = Seq.empty,
              emailVerified = true
            ),
            None,
            None,
            None
          )
        )
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(
      Future
        .successful(
          Some(
            AuthInfo(
              UserRights(
                UserId("user-moderator"),
                roles = Seq(RoleModerator),
                availableQuestions = Seq.empty,
                emailVerified = true
              ),
              None,
              None,
              None
            )
          )
        )
    )

  val personality = TestUtils.user(
    id = UserId("personality-id"),
    email = "perso.nality@make.org",
    firstName = Some("perso"),
    lastName = Some("nality"),
    userType = UserType.UserTypePersonality
  )

  feature("post personality") {
    scenario("post personality unauthenticated") {
      Post("/admin/personalities").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("post personality without admin rights") {
      Post("/admin/personalities")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("post personality with admin rights") {

      when(userService.registerPersonality(any[PersonalityRegisterData], any[RequestContext]))
        .thenReturn(Future.successful(personality))

      Post("/admin/personalities")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "email": "perso.nality@make.org",
                                                                  | "firstName": "perso",
                                                                  | "lastName": "nality",
                                                                  | "country": "FR",
                                                                  | "language": "fr"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Created
      }
    }

    scenario("post scenario with wrong request") {
      Post("/admin/personalities")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "firstName": "perso",
                                                                  | "lastName": "nality",
                                                                  | "country": "FR",
                                                                  | "language": "fr"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  feature("put personality") {
    scenario("put personality unauthenticated") {
      Put("/admin/personalities/personality-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("put personality without admin rights") {
      Put("/admin/personalities/personality-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("put personality with admin rights") {

      when(userService.getUser(any[UserId])).thenReturn(Future.successful(Some(personality)))
      when(userService.getUserByEmail(any[String])).thenReturn(Future.successful(None))

      when(
        userService
          .update(any[User], any[RequestContext])
      ).thenReturn(Future.successful(personality))

      Put("/admin/personalities/personality-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "firstName": "some other firstName"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    scenario("put personality with wrong request") {
      Put("/admin/personalities/personality-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                   "email": "some other email"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    scenario("put non existent personality") {
      when(userService.getUser(any[UserId])).thenReturn(Future.successful(None))

      Put("/admin/personalities/not-found")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                   "firstName": "some other firstName"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  feature("get personalities") {
    scenario("get personalities unauthenticated") {
      Get("/admin/personalities") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("get personalities without admin rights") {
      Get("/admin/personalities")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("get personalities with admin rights") {

      when(
        userService.adminFindUsers(
          start = 0,
          end = None,
          sort = None,
          order = None,
          email = None,
          firstName = None,
          lastName = None,
          role = None,
          userType = Some(UserType.UserTypePersonality)
        )
      ).thenReturn(Future.successful(Seq(personality)))
      when(
        userService.adminCountUsers(
          email = None,
          firstName = None,
          lastName = None,
          role = None,
          userType = Some(UserType.UserTypePersonality)
        )
      ).thenReturn(Future.successful(1))

      Get("/admin/personalities")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  feature("get personality") {
    scenario("get personality unauthenticated") {
      Get("/admin/personalities/personality-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("get personalities without admin rights") {
      Get("/admin/personalities/personality-id")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("get personality with admin rights") {

      when(userService.getUser(any[UserId]))
        .thenReturn(Future.successful(Some(personality)))

      Get("/admin/personalities/personality-id")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    scenario("get non existent personality") {

      when(userService.getUser(any[UserId]))
        .thenReturn(Future.successful(None))

      Get("/admin/personalities/not-found")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

}
