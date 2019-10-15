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

import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.auth.UserRights
import org.make.core.idea.IdeaId
import org.make.core.question.QuestionId
import org.make.core.tag.TagId
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.mockito.Mockito.when
import scalaoauth2.provider.{AccessToken, AuthInfo}
import org.mockito.ArgumentMatchers.{eq => matches}

import scala.concurrent.Future

class AdminIdeaMappingApiTest
    extends MakeApiTestBase
    with DefaultAdminIdeaMappingApiComponent
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with MakeSettingsComponent
    with IdeaMappingServiceComponent {

  override val ideaMappingService: IdeaMappingService = mock[IdeaMappingService]

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

  val routes: Route = sealRoute(adminIdeaMappingApi.routes)

  feature("create idea mapping") {

    scenario("access refused for user or moderator") {
      Post("/admin/idea-mappings") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Post("/admin/idea-mappings").withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Post("/admin/idea-mappings").withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }
    }

    scenario("access granted for admin") {

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

      Post("/admin/idea-mappings")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken)))
        .withEntity(ContentTypes.`application/json`, entity) ~>
        routes ~>
        check {
          status should be(StatusCodes.Created)
        }

    }

  }

  feature("update idea mapping") {

    scenario("access refused for user or moderator") {
      Put("/admin/idea-mappings/456") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Put("/admin/idea-mappings/456").withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Put("/admin/idea-mappings/456").withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }
    }

    scenario("access granted for admin") {

      when(
        ideaMappingService
          .changeIdea(
            adminId = UserId("my-admin-user-id"),
            IdeaMappingId = IdeaMappingId("456"),
            newIdea = IdeaId("idea-id"),
            migrateProposals = false
          )
      ).thenReturn(
        Future.successful(
          Some(IdeaMapping(IdeaMappingId("idea-mapping-id"), QuestionId("question-id"), None, None, IdeaId("idea-id")))
        )
      )

      val entity =
        """
          |{
          |  "ideaId": "idea-id",
          |  "migrateProposals": false
          |}
        """.stripMargin

      Put("/admin/idea-mappings/456")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken)))
        .withEntity(ContentTypes.`application/json`, entity) ~>
        routes ~>
        check {
          status should be(StatusCodes.OK)
        }

    }

    scenario("No mapping found") {
      when(
        ideaMappingService
          .changeIdea(
            adminId = UserId("my-admin-user-id"),
            IdeaMappingId = IdeaMappingId("456"),
            newIdea = IdeaId("idea-id"),
            migrateProposals = false
          )
      ).thenReturn(Future.successful(None))

      val entity =
        """
          |{
          |  "ideaId": "idea-id",
          |  "migrateProposals": false
          |}
        """.stripMargin

      Put("/admin/idea-mappings/456")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken)))
        .withEntity(ContentTypes.`application/json`, entity) ~>
        routes ~>
        check {
          status should be(StatusCodes.NotFound)
        }
    }

  }

  feature("get single idea mapping") {

    scenario("access refused for user or moderator") {
      Get("/admin/idea-mappings/123") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Get("/admin/idea-mappings/123").withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Get("/admin/idea-mappings/123").withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }
    }

    scenario("access granted for admin") {

      when(ideaMappingService.getById(IdeaMappingId("123")))
        .thenReturn(
          Future.successful(
            Some(IdeaMapping(IdeaMappingId("123"), QuestionId("question-id"), None, None, IdeaId("idea-id")))
          )
        )

      Get("/admin/idea-mappings/123")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.OK)
        }

    }

  }

  feature("search idea mapping") {

    scenario("access refused for user or moderator") {
      Get("/admin/idea-mappings") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Get("/admin/idea-mappings").withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }

      Get("/admin/idea-mappings").withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.Forbidden)
        }
    }

    scenario("access granted for admin") {

      when(
        ideaMappingService
          .count(Some(QuestionId("question-id")), Some(Left(None)), Some(Right(TagId("solution-tag"))), None)
      ).thenReturn(Future.successful(0))

      when(
        ideaMappingService
          .search(
            start = 0,
            end = None,
            sort = None,
            order = None,
            questionId = Some(QuestionId("question-id")),
            stakeTagId = Some(Left(None)),
            solutionTypeTagId = Some(Right(TagId("solution-tag"))),
            ideaId = None
          )
      ).thenReturn(Future.successful(Seq.empty))

      Get("/admin/idea-mappings?stakeTagId=None&solutionTypeTagId=solution-tag&questionId=question-id")
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~>
        routes ~>
        check {
          status should be(StatusCodes.OK)
        }

    }

  }

}
