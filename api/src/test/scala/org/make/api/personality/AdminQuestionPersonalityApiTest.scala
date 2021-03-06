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
import org.make.api.MakeApiTestBase
import org.make.core.personality.{Personality, PersonalityId, PersonalityRole, PersonalityRoleId}
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import org.make.core.Order

import scala.concurrent.Future
import org.make.core.technical.Pagination.{End, Start}

class AdminQuestionPersonalityApiTest
    extends MakeApiTestBase
    with DefaultAdminQuestionPersonalityApiComponent
    with PersonalityRoleServiceComponent
    with QuestionPersonalityServiceComponent {

  override val questionPersonalityService: QuestionPersonalityService = mock[QuestionPersonalityService]
  override val personalityRoleService: PersonalityRoleService = mock[PersonalityRoleService]

  val routes: Route = sealRoute(adminQuestionPersonalityApi.routes)

  val personality: Personality = Personality(
    personalityId = PersonalityId("personality-id"),
    userId = UserId("user-id"),
    questionId = QuestionId("question-id"),
    personalityRoleId = PersonalityRoleId("candidate")
  )

  Feature("post personality") {

    when(
      questionPersonalityService
        .find(
          start = any[Start],
          end = any[Option[End]],
          sort = any[Option[String]],
          order = any[Option[Order]],
          userId = any[Option[UserId]],
          questionId = any[Option[QuestionId]],
          personalityRoleId = any[Option[PersonalityRoleId]]
        )
    ).thenReturn(Future.successful(Seq.empty))

    Scenario("post personality unauthenticated") {
      Post("/admin/question-personalities").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("post personality without admin rights") {
      Post("/admin/question-personalities")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("post personality with admin rights") {

      when(questionPersonalityService.createPersonality(any[CreateQuestionPersonalityRequest]))
        .thenReturn(Future.successful(personality))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/question-personalities")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "userId": "user-id",
                                                                  | "questionId": "question-id",
                                                                  | "personalityRoleId": "candidate"
                                                                  |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.Created
        }
      }
    }

    Scenario("post personality with wrong request") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/question-personalities")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "questionId": "question-id"
                                                                  |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }

    Scenario("personality already exists") {

      when(
        questionPersonalityService
          .find(
            start = any[Start],
            end = any[Option[End]],
            sort = any[Option[String]],
            order = any[Option[Order]],
            userId = eqTo(Some(UserId("user-id"))),
            questionId = eqTo(Some(QuestionId("question-id"))),
            personalityRoleId = any[Option[PersonalityRoleId]]
          )
      ).thenReturn(
        Future.successful(
          Seq(
            Personality(
              personalityId = PersonalityId("personality-id"),
              userId = UserId("user-id"),
              questionId = QuestionId("question-id"),
              personalityRoleId = PersonalityRoleId("candidate")
            )
          )
        )
      )

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/question-personalities")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "userId": "user-id",
                                                                  | "questionId": "question-id",
                                                                  | "personalityRoleId": "candidate"
                                                                  |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }
  }

  Feature("put personality") {
    Scenario("put personality unauthenticated") {
      Put("/admin/question-personalities/personality-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("put personality without admin rights") {
      Put("/admin/question-personalities/personality-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("put personality with admin rights") {

      when(
        questionPersonalityService
          .updatePersonality(eqTo(PersonalityId("personality-id")), any[UpdateQuestionPersonalityRequest])
      ).thenReturn(Future.successful(Some(personality)))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/question-personalities/personality-id")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "userId": "user-id",
                                                                  | "personalityRoleId": "candidate"
                                                                  |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }

    Scenario("put personality with wrong request") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/question-personalities/personality-id")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "personalityRoleId": "candidate"
                                                                  |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }

    Scenario("put non existent personality") {
      when(
        questionPersonalityService
          .updatePersonality(eqTo(PersonalityId("not-found")), any[UpdateQuestionPersonalityRequest])
      ).thenReturn(Future.successful(None))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/question-personalities/not-found")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "userId": "user-id",
                                                                  | "personalityRoleId": "candidate"
                                                                  |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }
  }

  Feature("get personalities") {
    Scenario("get personalities unauthenticated") {
      Get("/admin/question-personalities") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("get personalities without admin rights") {
      Get("/admin/question-personalities")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("get personalities with admin rights") {

      when(
        questionPersonalityService.find(
          questionId = None,
          userId = None,
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          personalityRoleId = None
        )
      ).thenReturn(Future.successful(Seq(personality)))
      when(questionPersonalityService.count(userId = None, questionId = None, personalityRoleId = None))
        .thenReturn(Future.successful(1))
      when(
        personalityRoleService.find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          roleIds = Some(Seq(PersonalityRoleId("candidate"))),
          name = None
        )
      ).thenReturn(
        Future
          .successful(Seq(PersonalityRole(personalityRoleId = PersonalityRoleId("candidate"), name = "CANDIDATE")))
      )

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/question-personalities")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }
  }

  Feature("get personality") {
    Scenario("get personality unauthenticated") {
      Get("/admin/question-personalities/personality-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("get personalities without admin rights") {
      Get("/admin/question-personalities/personality-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("get personality with admin rights") {

      when(questionPersonalityService.getPersonality(eqTo(PersonalityId("personality-id"))))
        .thenReturn(Future.successful(Some(personality)))
      when(personalityRoleService.getPersonalityRole(personality.personalityRoleId))
        .thenReturn(
          Future
            .successful(Some(PersonalityRole(personalityRoleId = PersonalityRoleId("candidate"), name = "CANDIDATE")))
        )

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/question-personalities/personality-id")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }

    Scenario("get non existent personality") {

      when(questionPersonalityService.getPersonality(eqTo(PersonalityId("not-found"))))
        .thenReturn(Future.successful(None))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/question-personalities/not-found")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }
  }

}
