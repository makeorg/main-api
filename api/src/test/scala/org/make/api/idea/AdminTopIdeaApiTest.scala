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

package org.make.api.idea
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import org.make.api.MakeApiTestBase
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.core.idea._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}

import scala.concurrent.Future
import org.make.core.technical.Pagination.Start

class AdminTopIdeaApiTest
    extends MakeApiTestBase
    with DefaultAdminTopIdeaApiComponent
    with TopIdeaServiceComponent
    with QuestionServiceComponent
    with IdeaServiceComponent {

  override val topIdeaService: TopIdeaService = mock[TopIdeaService]
  override val questionService: QuestionService = mock[QuestionService]
  override val ideaService: IdeaService = mock[IdeaService]

  val routes: Route = sealRoute(adminTopIdeaApi.routes)

  Feature("create top idea") {

    Scenario("access refused for user or moderator") {
      Post("/admin/top-ideas") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Post("/admin/top-ideas").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Post("/admin/top-ideas").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }
    }

    Scenario("question doesn't exists") {
      when(questionService.getCachedQuestion(QuestionId("not-found"))).thenReturn(Future.successful(None))
      when(ideaService.fetchOne(IdeaId("idea-id")))
        .thenReturn(
          Future.successful(Some(Idea(ideaId = IdeaId("idea-id"), name = "idea", createdAt = None, updatedAt = None)))
        )

      val entity =
        """{
          | "ideaId": "idea-id",
          | "questionId": "not-found",
          | "name": "name",
          | "label": "label",
          | "scores": {
          |   "totalProposalsRatio": 0,
          |   "agreementRatio": 0,
          |   "likeItRatio": 0
          | },
          | "weight": 42
          |}""".stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/top-ideas")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(ContentTypes.`application/json`, entity) ~>
          routes ~>
          check {
            status should be(StatusCodes.BadRequest)
          }
      }
    }

    Scenario("idea doesn't exists") {
      when(questionService.getCachedQuestion(QuestionId("question-id")))
        .thenReturn(
          Future.successful(
            Some(
              Question(
                questionId = QuestionId("question-id"),
                slug = "question",
                countries = NonEmptyList.of(Country("FR")),
                language = Language("fr"),
                question = "question ?",
                shortTitle = None,
                operationId = None
              )
            )
          )
        )

      when(ideaService.fetchOne(IdeaId("not-found"))).thenReturn(Future.successful(None))

      val entity =
        """{
          | "ideaId": "not-found",
          | "questionId": "question-id",
          | "name": "name",
          | "label": "label",
          | "scores": {
          |   "totalProposalsRatio": 0,
          |   "agreementRatio": 0,
          |   "likeItRatio": 0
          | },
          | "weight": 42
          |}""".stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/top-ideas")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(ContentTypes.`application/json`, entity) ~>
          routes ~>
          check {
            status should be(StatusCodes.BadRequest)
          }
      }
    }

    Scenario("access granted for admin") {

      when(questionService.getCachedQuestion(QuestionId("question-id")))
        .thenReturn(
          Future.successful(
            Some(
              Question(
                questionId = QuestionId("question-id"),
                slug = "question",
                countries = NonEmptyList.of(Country("FR")),
                language = Language("fr"),
                question = "question ?",
                shortTitle = None,
                operationId = None
              )
            )
          )
        )

      when(ideaService.fetchOne(IdeaId("idea-id")))
        .thenReturn(
          Future.successful(Some(Idea(ideaId = IdeaId("idea-id"), name = "idea", createdAt = None, updatedAt = None)))
        )

      when(
        topIdeaService.create(
          IdeaId("idea-id"),
          QuestionId("question-id"),
          name = "name",
          label = "label",
          TopIdeaScores(0, 0, 0),
          42
        )
      ).thenReturn(
        Future
          .successful(
            TopIdea(
              TopIdeaId("top-ideas-id"),
              IdeaId("idea-id"),
              QuestionId("question-id"),
              name = "name",
              label = "label",
              TopIdeaScores(0, 0, 0),
              42
            )
          )
      )

      val entity =
        """{
          | "ideaId": "idea-id",
          | "questionId": "question-id",
          | "name": "name",
          | "label": "label",
          | "scores": {
          |   "totalProposalsRatio": 0,
          |   "agreementRatio": 0,
          |   "likeItRatio": 0
          | },
          | "weight": 42
          |}""".stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/top-ideas")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(ContentTypes.`application/json`, entity) ~>
          routes ~>
          check {
            status should be(StatusCodes.Created)
          }
      }

    }

  }

  Feature("update top idea") {

    Scenario("access refused for user or moderator") {
      Put("/admin/top-ideas/top-idea-id") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Put("/admin/top-ideas/top-idea-id").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Put("/admin/top-ideas/top-idea-id").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }
    }

    Scenario("question doesn't exists") {
      when(topIdeaService.getById(TopIdeaId("top-idea-id"))).thenReturn(
        Future.successful(
          Some(
            TopIdea(
              TopIdeaId("top-idea-id"),
              IdeaId("idea-id"),
              QuestionId("question-id"),
              name = "update name",
              label = "label",
              TopIdeaScores(0, 0, 0),
              42
            )
          )
        )
      )
      when(questionService.getCachedQuestion(QuestionId("not-found"))).thenReturn(Future.successful(None))
      when(ideaService.fetchOne(IdeaId("idea-id")))
        .thenReturn(
          Future.successful(Some(Idea(ideaId = IdeaId("idea-id"), name = "idea", createdAt = None, updatedAt = None)))
        )

      val entity =
        """{
          | "ideaId": "idea-id",
          | "questionId": "not-found",
          | "name": "name",
          | "label": "label",
          | "scores": {
          |   "totalProposalsRatio": 0,
          |   "agreementRatio": 0,
          |   "likeItRatio": 0
          | },
          | "weight": 42
          |}""".stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/top-ideas")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(ContentTypes.`application/json`, entity) ~>
          routes ~>
          check {
            status should be(StatusCodes.BadRequest)
          }
      }
    }

    Scenario("idea doesn't exists") {
      when(topIdeaService.getById(TopIdeaId("top-idea-id"))).thenReturn(
        Future.successful(
          Some(
            TopIdea(
              TopIdeaId("top-idea-id"),
              IdeaId("idea-id"),
              QuestionId("question-id"),
              name = "update name",
              label = "label",
              TopIdeaScores(0, 0, 0),
              42
            )
          )
        )
      )

      when(questionService.getCachedQuestion(QuestionId("question-id")))
        .thenReturn(
          Future.successful(
            Some(
              Question(
                questionId = QuestionId("question-id"),
                slug = "question",
                countries = NonEmptyList.of(Country("FR")),
                language = Language("fr"),
                question = "question ?",
                shortTitle = None,
                operationId = None
              )
            )
          )
        )

      when(ideaService.fetchOne(IdeaId("not-found"))).thenReturn(Future.successful(None))

      val entity =
        """{
          | "ideaId": "not-found",
          | "questionId": "question-id",
          | "name": "name",
          | "label": "label",
          | "scores": {
          |   "totalProposalsRatio": 0,
          |   "agreementRatio": 0,
          |   "likeItRatio": 0
          | },
          | "weight": 42
          |}""".stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/top-ideas")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(ContentTypes.`application/json`, entity) ~>
          routes ~>
          check {
            status should be(StatusCodes.BadRequest)
          }
      }
    }

    Scenario("access granted for admin") {

      when(topIdeaService.getById(TopIdeaId("top-idea-id"))).thenReturn(
        Future.successful(
          Some(
            TopIdea(
              TopIdeaId("top-idea-id"),
              IdeaId("idea-id"),
              QuestionId("question-id"),
              name = "update name",
              label = "label",
              TopIdeaScores(0, 0, 0),
              42
            )
          )
        )
      )

      when(
        topIdeaService
          .update(
            TopIdea(
              TopIdeaId("top-idea-id"),
              IdeaId("idea-id"),
              QuestionId("question-id"),
              name = "update name",
              label = "label",
              TopIdeaScores(0, 0, 0),
              42
            )
          )
      ).thenReturn(
        Future.successful(
          TopIdea(
            TopIdeaId("top-idea-id"),
            IdeaId("idea-id"),
            QuestionId("question-id"),
            name = "update name",
            label = "label",
            TopIdeaScores(0, 0, 0),
            42
          )
        )
      )

      val entity =
        """{
          | "ideaId": "idea-id",
          | "questionId": "question-id",
          | "name": "update name",
          | "label": "label",
          | "scores": {
          |   "totalProposalsRatio": 0,
          |   "agreementRatio": 0,
          |   "likeItRatio": 0
          | },
          | "weight": 42
          |}""".stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/top-ideas/top-idea-id")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(ContentTypes.`application/json`, entity) ~>
          routes ~>
          check {
            status should be(StatusCodes.OK)
          }
      }

    }

    Scenario("No top idea found") {
      when(
        topIdeaService
          .getById(TopIdeaId("not-found"))
      ).thenReturn(Future.successful(None))

      val entity =
        """{
          | "ideaId": "idea-id",
          | "questionId": "question-id",
          | "name": "update name",
          | "label": "label",
          | "scores": {
          |   "totalProposalsRatio": 0,
          |   "agreementRatio": 0,
          |   "likeItRatio": 0
          | },
          | "weight": 42
          |}""".stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/top-ideas/not-found")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(ContentTypes.`application/json`, entity) ~>
          routes ~>
          check {
            status should be(StatusCodes.NotFound)
          }
      }
    }

  }

  Feature("get top idea") {

    Scenario("access refused for user or moderator") {
      Get("/admin/top-ideas/top-idea-id") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Get("/admin/top-ideas/top-idea-id").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Get("/admin/top-ideas/top-idea-id").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }
    }

    Scenario("access granted for admin") {

      when(topIdeaService.getById(TopIdeaId("top-idea-id")))
        .thenReturn(
          Future.successful(
            Some(
              TopIdea(
                TopIdeaId("top-idea-id"),
                IdeaId("idea-id"),
                QuestionId("question-id"),
                name = "update name",
                label = "label",
                TopIdeaScores(0, 0, 0),
                42
              )
            )
          )
        )

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/top-ideas/top-idea-id")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~>
          routes ~>
          check {
            status should be(StatusCodes.OK)
          }
      }

    }

  }

  Feature("search top idea") {

    Scenario("access refused for user or moderator") {
      Get("/admin/top-ideas") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Get("/admin/top-ideas").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Get("/admin/top-ideas").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }
    }

    Scenario("access granted for admin") {

      when(
        topIdeaService
          .count(None, None, None)
      ).thenReturn(Future.successful(0))

      when(
        topIdeaService
          .search(
            start = Start.zero,
            end = None,
            sort = None,
            order = None,
            ideaId = None,
            questionIds = None,
            name = None
          )
      ).thenReturn(Future.successful(Seq.empty))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/top-ideas")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~>
          routes ~>
          check {
            status should be(StatusCodes.OK)
          }
      }

    }

  }

}
