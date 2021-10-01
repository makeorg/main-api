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

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.user.{PersonalityRegisterData, UserService, UserServiceComponent}
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.core.RequestContext
import org.make.core.technical.Pagination.Start
import org.make.core.user.{User, UserId, UserType}

import scala.concurrent.Future

class AdminPersonalityApiTest
    extends MakeApiTestBase
    with DefaultAdminPersonalityApiComponent
    with UserServiceComponent {

  override val userService: UserService = mock[UserService]

  val routes: Route = sealRoute(adminPersonalityApi.routes)

  val personality = TestUtils.user(
    id = UserId("personality-id"),
    email = "perso.nality@make.org",
    firstName = Some("perso"),
    lastName = Some("nality"),
    userType = UserType.UserTypePersonality
  )

  Feature("post personality") {
    Scenario("post personality unauthenticated") {
      Post("/admin/personalities").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("post personality without admin rights") {
      Post("/admin/personalities")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("post personality with admin rights") {

      when(userService.registerPersonality(any[PersonalityRegisterData], any[RequestContext]))
        .thenReturn(Future.successful(personality))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/personalities")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "email": "perso.nality@make.org",
                                                                  | "firstName": "perso",
                                                                  | "lastName": "nality",
                                                                  | "country": "FR",
                                                                  | "language": "fr"
                                                                  |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.Created
        }
      }
    }

    Scenario("post scenario with wrong request") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/personalities")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "firstName": "perso",
                                                                  | "lastName": "nality",
                                                                  | "country": "FR",
                                                                  | "language": "fr"
                                                                  |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }
  }

  Feature("put personality") {
    Scenario("put personality unauthenticated") {
      Put("/admin/personalities/personality-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("put personality without admin rights") {
      Put("/admin/personalities/personality-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("put personality with admin rights") {

      when(userService.getUser(any[UserId])).thenReturn(Future.successful(Some(personality)))
      when(userService.getUserByEmail(any[String])).thenReturn(Future.successful(None))

      when(
        userService
          .updatePersonality(any[User], any[Option[UserId]], any[String], any[RequestContext])
      ).thenReturn(Future.successful(personality))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/personalities/personality-id")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "firstName": "some other firstName"
                                                                  |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }

    Scenario("put personality with wrong request") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/personalities/personality-id")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                   "email": "some other email"
                                                                  |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }

    Scenario("put non existent personality") {
      when(userService.getUser(any[UserId])).thenReturn(Future.successful(None))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/personalities/not-found")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                   "firstName": "some other firstName"
                                                                  |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }
  }

  Feature("get personalities") {
    Scenario("get personalities unauthenticated") {
      Get("/admin/personalities") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("get personalities without admin rights") {
      Get("/admin/personalities")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("get personalities with admin rights") {

      when(
        userService.adminFindUsers(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          ids = None,
          email = None,
          firstName = None,
          lastName = None,
          role = None,
          userType = Some(UserType.UserTypePersonality)
        )
      ).thenReturn(Future.successful(Seq(personality)))
      when(
        userService.adminCountUsers(
          ids = None,
          email = None,
          firstName = None,
          lastName = None,
          role = None,
          userType = Some(UserType.UserTypePersonality)
        )
      ).thenReturn(Future.successful(1))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/personalities")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }
  }

  Feature("get personality") {
    Scenario("get personality unauthenticated") {
      Get("/admin/personalities/personality-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("get personalities without admin rights") {
      Get("/admin/personalities/personality-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("get personality with admin rights") {

      when(userService.getUser(any[UserId]))
        .thenReturn(Future.successful(Some(personality)))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/personalities/personality-id")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }

    Scenario("get non existent personality") {

      when(userService.getUser(any[UserId]))
        .thenReturn(Future.successful(None))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/personalities/not-found")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }
  }

}
