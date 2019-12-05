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
import org.make.api.MakeApiTestBase
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.auth.UserRights
import org.make.core.personality.{Candidate, Personality, PersonalityId}
import org.make.core.question.QuestionId
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito.when
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class AdminQuestionPersonalityApiTest
    extends MakeApiTestBase
    with DefaultAdminQuestionPersonalityApiComponent
    with QuestionPersonalityServiceComponent
    with MakeDataHandlerComponent {

  override val questionPersonalityService: QuestionPersonalityService = mock[QuestionPersonalityService]

  val routes: Route = sealRoute(adminQuestionPersonalityApi.routes)

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

  val personality: Personality = Personality(
    personalityId = PersonalityId("personality-id"),
    userId = UserId("user-id"),
    questionId = QuestionId("question-id"),
    personalityRole = Candidate
  )

  feature("post personality") {
    scenario("post personality unauthenticated") {
      Post("/admin/question-personalities").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("post personality without admin rights") {
      Post("/admin/question-personalities")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("post personality with admin rights") {

      when(questionPersonalityService.createPersonality(any[CreateQuestionPersonalityRequest]))
        .thenReturn(Future.successful(personality))

      Post("/admin/question-personalities")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "userId": "user-id",
                                                                  | "questionId": "question-id",
                                                                  | "personalityRole": "CANDIDATE"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Created
      }
    }

    scenario("post scenario with wrong request") {
      Post("/admin/question-personalities")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "questionId": "question-id"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  feature("put personality") {
    scenario("put personality unauthenticated") {
      Put("/admin/question-personalities/personality-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("put personality without admin rights") {
      Put("/admin/question-personalities/personality-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("put personality with admin rights") {

      when(
        questionPersonalityService
          .updatePersonality(matches(PersonalityId("personality-id")), any[UpdateQuestionPersonalityRequest])
      ).thenReturn(Future.successful(Some(personality)))

      Put("/admin/question-personalities/personality-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "userId": "user-id",
                                                                  | "personalityRole": "CANDIDATE"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    scenario("put personality with wrong request") {
      Put("/admin/question-personalities/personality-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "personalityRole": "CANDIDATE"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    scenario("put non existent personality") {
      when(
        questionPersonalityService
          .updatePersonality(matches(PersonalityId("not-found")), any[UpdateQuestionPersonalityRequest])
      ).thenReturn(Future.successful(None))

      Put("/admin/question-personalities/not-found")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "userId": "user-id",
                                                                  | "personalityRole": "CANDIDATE"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  feature("get personalities") {
    scenario("get personalities unauthenticated") {
      Get("/admin/question-personalities") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("get personalities without admin rights") {
      Get("/admin/question-personalities")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("get personalities with admin rights") {

      when(
        questionPersonalityService.find(
          questionId = None,
          userId = None,
          start = 0,
          end = None,
          sort = None,
          order = None,
          personalityRole = None
        )
      ).thenReturn(Future.successful(Seq(personality)))
      when(questionPersonalityService.count(userId = None, questionId = None, personalityRole = None))
        .thenReturn(Future.successful(1))

      Get("/admin/question-personalities")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  feature("get personality") {
    scenario("get personality unauthenticated") {
      Get("/admin/question-personalities/personality-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("get personalities without admin rights") {
      Get("/admin/question-personalities/personality-id")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("get personality with admin rights") {

      when(questionPersonalityService.getPersonality(matches(PersonalityId("personality-id"))))
        .thenReturn(Future.successful(Some(personality)))

      Get("/admin/question-personalities/personality-id")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    scenario("get non existent personality") {

      when(questionPersonalityService.getPersonality(matches(PersonalityId("not-found"))))
        .thenReturn(Future.successful(None))

      Get("/admin/question-personalities/not-found")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

}
