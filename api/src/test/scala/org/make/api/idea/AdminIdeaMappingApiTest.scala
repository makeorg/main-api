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
import org.make.core.idea.{IdeaId, IdeaMapping, IdeaMappingId}
import org.make.core.question.QuestionId
import org.make.core.tag.TagId
import org.make.core.user.UserId

import scala.concurrent.Future
import org.make.core.technical.Pagination.Start

class AdminIdeaMappingApiTest
    extends MakeApiTestBase
    with DefaultAdminIdeaMappingApiComponent
    with IdeaMappingServiceComponent {

  override val ideaMappingService: IdeaMappingService = mock[IdeaMappingService]

  val routes: Route = sealRoute(adminIdeaMappingApi.routes)

  Feature("create idea mapping") {

    Scenario("access refused for user or moderator") {
      Post("/admin/idea-mappings") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Post("/admin/idea-mappings").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Post("/admin/idea-mappings").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }
    }

    Scenario("access granted for admin") {

      when(ideaMappingService.create(QuestionId("question-id"), None, None, IdeaId("idea-id")))
        .thenReturn(
          Future.successful(
            IdeaMapping(IdeaMappingId("idea-mapping-id"), QuestionId("question-id"), None, None, IdeaId("idea-id"))
          )
        )

      val entity =
        """
          |{
          |  "questionId": "question-id",
          |  "ideaId": "idea-id"
          |}
        """.stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/idea-mappings")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(ContentTypes.`application/json`, entity) ~>
          routes ~>
          check {
            status should be(StatusCodes.Created)
          }
      }

    }

  }

  Feature("update idea mapping") {

    Scenario("access refused for user or moderator") {
      Put("/admin/idea-mappings/456") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Put("/admin/idea-mappings/456").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Put("/admin/idea-mappings/456").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }
    }

    Scenario("access granted for admin") {

      for (id <- Seq("my-admin-user-id", "my-super-admin-user-id")) {
        when(
          ideaMappingService
            .changeIdea(
              adminId = UserId(id),
              IdeaMappingId = IdeaMappingId("456"),
              newIdea = IdeaId("idea-id"),
              migrateProposals = false
            )
        ).thenReturn(
          Future.successful(
            Some(
              IdeaMapping(IdeaMappingId("idea-mapping-id"), QuestionId("question-id"), None, None, IdeaId("idea-id"))
            )
          )
        )
      }

      val entity =
        """
          |{
          |  "ideaId": "idea-id",
          |  "migrateProposals": false
          |}
        """.stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/idea-mappings/456")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(ContentTypes.`application/json`, entity) ~>
          routes ~>
          check {
            status should be(StatusCodes.OK)
          }
      }

    }

    Scenario("No mapping found") {
      for (id <- Seq("my-admin-user-id", "my-super-admin-user-id")) {
        when(
          ideaMappingService
            .changeIdea(
              adminId = UserId(id),
              IdeaMappingId = IdeaMappingId("456"),
              newIdea = IdeaId("idea-id"),
              migrateProposals = false
            )
        ).thenReturn(Future.successful(None))
      }

      val entity =
        """
          |{
          |  "ideaId": "idea-id",
          |  "migrateProposals": false
          |}
        """.stripMargin

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/idea-mappings/456")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(ContentTypes.`application/json`, entity) ~>
          routes ~>
          check {
            status should be(StatusCodes.NotFound)
          }
      }
    }

  }

  Feature("get single idea mapping") {

    Scenario("access refused for user or moderator") {
      Get("/admin/idea-mappings/123") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Get("/admin/idea-mappings/123").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Get("/admin/idea-mappings/123").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }
    }

    Scenario("access granted for admin") {

      when(ideaMappingService.getById(IdeaMappingId("123")))
        .thenReturn(
          Future.successful(
            Some(IdeaMapping(IdeaMappingId("123"), QuestionId("question-id"), None, None, IdeaId("idea-id")))
          )
        )

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/idea-mappings/123")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~>
          routes ~>
          check {
            status should be(StatusCodes.OK)
          }
      }

    }

  }

  Feature("search idea mapping") {

    Scenario("access refused for user or moderator") {
      Get("/admin/idea-mappings") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Get("/admin/idea-mappings").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Get("/admin/idea-mappings").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }
    }

    Scenario("access granted for admin") {

      when(
        ideaMappingService
          .count(Some(QuestionId("question-id")), Some(Left(None)), Some(Right(TagId("solution-tag"))), None)
      ).thenReturn(Future.successful(0))

      when(
        ideaMappingService
          .search(
            start = Start.zero,
            end = None,
            sort = None,
            order = None,
            questionId = Some(QuestionId("question-id")),
            stakeTagId = Some(Left(None)),
            solutionTypeTagId = Some(Right(TagId("solution-tag"))),
            ideaId = None
          )
      ).thenReturn(Future.successful(Seq.empty))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/idea-mappings?stakeTagId=None&solutionTypeTagId=solution-tag&questionId=question-id")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~>
          routes ~>
          check {
            status should be(StatusCodes.OK)
          }
      }

    }

  }

}
