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

package org.make.api.tagtype

import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.core.auth.UserRights
import org.make.core.tag.{TagType, TagTypeDisplay, TagTypeId}
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{eq => matches}
import org.mockito.Mockito.when
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class ModerationTagTypeApiTest
    extends MakeApiTestBase
    with DefaultModerationTagTypeApiComponent
    with TagTypeServiceComponent {

  override val tagTypeService: TagTypeService = mock[TagTypeService]

  val validCitizenAccessToken = "my-valid-citizen-access-token"
  val validModeratorAccessToken = "my-valid-moderator-access-token"
  val validAdminAccessToken = "my-valid-admin-access-token"

  val tokenCreationDate = new Date()
  private val citizenAccessToken =
    AccessToken(validCitizenAccessToken, None, Some("user"), Some(1234567890L), tokenCreationDate)
  private val moderatorAccessToken =
    AccessToken(validModeratorAccessToken, None, Some("user"), Some(1234567890L), tokenCreationDate)
  private val adminAccessToken =
    AccessToken(validAdminAccessToken, None, Some("user"), Some(1234567890L), tokenCreationDate)

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

  val routes: Route = sealRoute(moderationTagTypeApi.routes)

  feature("create a tagType") {
    val validTagType = TagType(TagTypeId("valid-tag-type"), "valid TagType", TagTypeDisplay.Hidden)

    when(
      tagTypeService
        .createTagType(
          ArgumentMatchers.eq(validTagType.label),
          ArgumentMatchers.eq(validTagType.display),
          ArgumentMatchers.eq(validTagType.weight)
        )
    ).thenReturn(Future.successful(validTagType))

    scenario("unauthorize unauthenticated") {
      Post("/moderation/tag-types").withEntity(
        HttpEntity(ContentTypes.`application/json`, """{"label": "valid TagType", "display":"HIDDEN"}""")
      ) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbid authenticated citizen") {
      Post("/moderation/tag-types")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"label": "valid TagType", "display":"HIDDEN"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated moderator") {
      Post("/moderation/tag-types")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"label": "valid TagType", "display":"HIDDEN", "weight": 0}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }
  }

  feature("read a tagType") {
    val helloWorldTagType = TagType(TagTypeId("hello-tag-type"), "hello tag type", TagTypeDisplay.Displayed)

    when(tagTypeService.getTagType(ArgumentMatchers.eq(helloWorldTagType.tagTypeId)))
      .thenReturn(Future.successful(Some(helloWorldTagType)))
    when(tagTypeService.getTagType(ArgumentMatchers.eq(TagTypeId("fake-tag-type"))))
      .thenReturn(Future.successful(None))

    scenario("unauthorize unauthenticated") {
      Get("/moderation/tag-types/hello-tag-type") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbid authenticated citizen") {
      Get("/moderation/tag-types/hello-tag-type")
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated moderator on existing tag type") {
      Get("/moderation/tag-types/hello-tag-type")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val tagType: TagTypeResponse = entityAs[TagTypeResponse]
        tagType.id should be(helloWorldTagType.tagTypeId)
        tagType.label should be(helloWorldTagType.label)
        tagType.display should be(helloWorldTagType.display)
      }
    }

    scenario("not found and allow authenticated moderator on a non existing tag type") {
      Get("/moderation/tag-types/fake-tag-type")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }

  feature("update a tagType") {
    val helloWorldTagType = TagType(TagTypeId("hello-tag-type"), "hello tag type", TagTypeDisplay.Displayed)
    val newHelloWorldTagType = TagType(TagTypeId("hello-tag-type"), "new label", TagTypeDisplay.Hidden)

    when(tagTypeService.getTagType(ArgumentMatchers.eq(helloWorldTagType.tagTypeId)))
      .thenReturn(Future.successful(Some(helloWorldTagType)))
    when(
      tagTypeService.updateTagType(
        ArgumentMatchers.eq(TagTypeId("fake-tag-type")),
        ArgumentMatchers.any[String],
        ArgumentMatchers.any[TagTypeDisplay],
        ArgumentMatchers.any[Int]
      )
    ).thenReturn(Future.successful(None))
    when(
      tagTypeService.updateTagType(
        ArgumentMatchers.eq(helloWorldTagType.tagTypeId),
        ArgumentMatchers.eq("new label"),
        ArgumentMatchers.eq(TagTypeDisplay.Hidden),
        ArgumentMatchers.any[Int]
      )
    ).thenReturn(Future.successful(Some(newHelloWorldTagType)))

    scenario("unauthorize unauthenticated") {
      Put("/moderation/tag-types/hello-tag-type").withEntity(
        HttpEntity(ContentTypes.`application/json`, """{"label": "new label", "display":"HIDDEN"}""")
      ) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbid authenticated citizen") {
      Put("/moderation/tag-types/hello-tag-type")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"label": "new label", "display":"HIDDEN"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated moderator on existing tag type") {
      Put("/moderation/tag-types/hello-tag-type")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"label": "new label", "display":"HIDDEN", "weight": 0}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val tagType: TagTypeResponse = entityAs[TagTypeResponse]
        tagType.id should be(newHelloWorldTagType.tagTypeId)
        tagType.label should be(newHelloWorldTagType.label)
        tagType.display should be(newHelloWorldTagType.display)
      }
    }

    scenario("not found and allow authenticated moderator on a non existing tag type") {
      Put("/moderation/tag-types/fake-tag-type")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"label": "new label", "display":"HIDDEN", "weight": 0}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }
}
