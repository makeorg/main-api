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
import org.make.api.MakeApiTestBase
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.idea._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.mockito.Mockito.when

import scala.concurrent.Future

class AdminTopIdeaApiTest
    extends MakeApiTestBase
    with DefaultAdminTopIdeaApiComponent
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with MakeSettingsComponent
    with TopIdeaServiceComponent
    with QuestionServiceComponent
    with IdeaServiceComponent {

  override val topIdeaService: TopIdeaService = mock[TopIdeaService]
  override val questionService: QuestionService = mock[QuestionService]
  override val ideaService: IdeaService = mock[IdeaService]

  val routes: Route = sealRoute(adminTopIdeaApi.routes)

  feature("create top idea") {

    scenario("access refused for user or moderator") {
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

    scenario("question doesn't exists") {
      when(questionService.getQuestion(QuestionId("not-found"))).thenReturn(Future.successful(None))
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

      Post("/admin/top-ideas")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(ContentTypes.`application/json`, entity) ~>
        routes ~>
        check {
          status should be(StatusCodes.BadRequest)
        }
    }

    scenario("idea doesn't exists") {
      when(questionService.getQuestion(QuestionId("question-id")))
        .thenReturn(
          Future.successful(
            Some(
              Question(
                questionId = QuestionId("question-id"),
                slug = "question",
                country = Country("FR"),
                language = Language("fr"),
                question = "question ?",
                operationId = None,
                themeId = None
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

      Post("/admin/top-ideas")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(ContentTypes.`application/json`, entity) ~>
        routes ~>
        check {
          status should be(StatusCodes.BadRequest)
        }
    }

    scenario("access granted for admin") {

      when(questionService.getQuestion(QuestionId("question-id")))
        .thenReturn(
          Future.successful(
            Some(
              Question(
                questionId = QuestionId("question-id"),
                slug = "question",
                country = Country("FR"),
                language = Language("fr"),
                question = "question ?",
                operationId = None,
                themeId = None
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

      Post("/admin/top-ideas")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(ContentTypes.`application/json`, entity) ~>
        routes ~>
        check {
          status should be(StatusCodes.Created)
        }

    }

  }

  feature("update top idea") {

    scenario("access refused for user or moderator") {
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

    scenario("question doesn't exists") {
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
      when(questionService.getQuestion(QuestionId("not-found"))).thenReturn(Future.successful(None))
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

      Post("/admin/top-ideas")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(ContentTypes.`application/json`, entity) ~>
        routes ~>
        check {
          status should be(StatusCodes.BadRequest)
        }
    }

    scenario("idea doesn't exists") {
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

      when(questionService.getQuestion(QuestionId("question-id")))
        .thenReturn(
          Future.successful(
            Some(
              Question(
                questionId = QuestionId("question-id"),
                slug = "question",
                country = Country("FR"),
                language = Language("fr"),
                question = "question ?",
                operationId = None,
                themeId = None
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

      Post("/admin/top-ideas")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(ContentTypes.`application/json`, entity) ~>
        routes ~>
        check {
          status should be(StatusCodes.BadRequest)
        }
    }

    scenario("access granted for admin") {

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

      Put("/admin/top-ideas/top-idea-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(ContentTypes.`application/json`, entity) ~>
        routes ~>
        check {
          status should be(StatusCodes.OK)
        }

    }

    scenario("No top idea found") {
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

      Put("/admin/top-ideas/not-found")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(ContentTypes.`application/json`, entity) ~>
        routes ~>
        check {
          status should be(StatusCodes.NotFound)
        }
    }

  }

  feature("get top idea") {

    scenario("access refused for user or moderator") {
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

    scenario("access granted for admin") {

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

      Get("/admin/top-ideas/top-idea-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~>
        routes ~>
        check {
          status should be(StatusCodes.OK)
        }

    }

  }

  feature("search top idea") {

    scenario("access refused for user or moderator") {
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

    scenario("access granted for admin") {

      when(
        topIdeaService
          .count(None, None, None)
      ).thenReturn(Future.successful(0))

      when(
        topIdeaService
          .search(start = 0, end = None, sort = None, order = None, ideaId = None, questionId = None, name = None)
      ).thenReturn(Future.successful(Seq.empty))

      Get("/admin/top-ideas")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~>
        routes ~>
        check {
          status should be(StatusCodes.OK)
        }

    }

  }

}
