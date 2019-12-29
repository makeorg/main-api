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
import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.auth.UserRights
import org.make.core.idea.{IdeaId, TopIdea, TopIdeaId, TopIdeaScores}
import org.make.core.question.QuestionId
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers.{eq => matches}
import org.mockito.Mockito.when
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class AdminTopIdeaApiTest
    extends MakeApiTestBase
    with DefaultAdminTopIdeaApiComponent
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with MakeSettingsComponent
    with TopIdeaServiceComponent {

  override val topIdeaService: TopIdeaService = mock[TopIdeaService]

  val validCitizenAccessToken = "my-valid-citizen-access-token"
  val validModeratorAccessToken = "my-valid-moderator-access-token"
  val validAdminAccessToken = "my-valid-admin-access-token"

  val tokenCreationDate = new Date()
  private val citizenAccessToken =
    AccessToken(validCitizenAccessToken, None, Some("user"), Some(0L), tokenCreationDate)
  private val moderatorAccessToken =
    AccessToken(validModeratorAccessToken, None, Some("user"), Some(0L), tokenCreationDate)
  private val adminAccessToken =
    AccessToken(validAdminAccessToken, None, Some("user"), Some(0L), tokenCreationDate)

  when(oauth2DataHandler.findAccessToken(validCitizenAccessToken))
    .thenReturn(Future.successful(Some(citizenAccessToken)))
  when(oauth2DataHandler.findAccessToken(validModeratorAccessToken))
    .thenReturn(Future.successful(Some(moderatorAccessToken)))
  when(oauth2DataHandler.findAccessToken(validAdminAccessToken))
    .thenReturn(Future.successful(Some(adminAccessToken)))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(citizenAccessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(
              userId = UserId("my-citizen-user-id"),
              roles = Seq(RoleCitizen),
              availableQuestions = Seq.empty,
              emailVerified = true
            ),
            None,
            Some("citizen"),
            None
          )
        )
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(
              userId = UserId("my-moderator-user-id"),
              roles = Seq(RoleModerator),
              availableQuestions = Seq.empty,
              emailVerified = true
            ),
            None,
            Some("moderator"),
            None
          )
        )
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future
        .successful(
          Some(
            AuthInfo(
              UserRights(
                userId = UserId("my-admin-user-id"),
                roles = Seq(RoleAdmin),
                availableQuestions = Seq.empty,
                emailVerified = true
              ),
              None,
              Some("admin"),
              None
            )
          )
        )
    )

  val routes: Route = sealRoute(adminTopIdeaApi.routes)

  feature("create top idea") {

    scenario("access refused for user or moderator") {
      Post("/admin/top-ideas") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Post("/admin/top-ideas").withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Post("/admin/top-ideas").withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }
    }

    scenario("access granted for admin") {

      when(
        topIdeaService.create(IdeaId("idea-id"), QuestionId("question-id"), name = "name", TopIdeaScores(0, 0, 0), 42)
      ).thenReturn(
        Future
          .successful(
            TopIdea(
              TopIdeaId("top-ideas-id"),
              IdeaId("idea-id"),
              QuestionId("question-id"),
              name = "name",
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
          | "totalProposalsRatio": 0,
          | "agreementRatio": 0,
          | "likeItRatio": 0,
          | "weight": 42
          |}""".stripMargin

      Post("/admin/top-ideas")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken)))
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

      Put("/admin/top-ideas/top-idea-id").withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Put("/admin/top-ideas/top-idea-id").withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
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
          | "totalProposalsRatio": 0,
          | "agreementRatio": 0,
          | "likeItRatio": 0,
          | "weight": 42
          |}""".stripMargin

      Put("/admin/top-ideas/top-idea-id")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken)))
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
          | "totalProposalsRatio": 0,
          | "agreementRatio": 0,
          | "likeItRatio": 0,
          | "weight": 42
          |}""".stripMargin

      Put("/admin/top-ideas/not-found")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken)))
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

      Get("/admin/top-ideas/top-idea-id").withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Get("/admin/top-ideas/top-idea-id").withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~>
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
                TopIdeaScores(0, 0, 0),
                42
              )
            )
          )
        )

      Get("/admin/top-ideas/top-idea-id")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~>
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

      Get("/admin/top-ideas").withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Get("/admin/top-ideas").withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~>
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
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.OK)
        }

    }

  }

}
