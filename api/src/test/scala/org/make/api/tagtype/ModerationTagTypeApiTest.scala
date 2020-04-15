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

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.core.tag.{TagType, TagTypeDisplay, TagTypeId}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when

import scala.concurrent.Future

class ModerationTagTypeApiTest
    extends MakeApiTestBase
    with DefaultModerationTagTypeApiComponent
    with TagTypeServiceComponent {

  override val tagTypeService: TagTypeService = mock[TagTypeService]

  val routes: Route = sealRoute(moderationTagTypeApi.routes)

  feature("create a tagType") {
    val validTagType =
      TagType(TagTypeId("valid-tag-type"), "valid TagType", TagTypeDisplay.Hidden, requiredForEnrichment = false)

    when(
      tagTypeService
        .createTagType(
          ArgumentMatchers.eq(validTagType.label),
          ArgumentMatchers.eq(validTagType.display),
          ArgumentMatchers.eq(validTagType.weight),
          ArgumentMatchers.eq(false)
        )
    ).thenReturn(Future.successful(validTagType))

    scenario("unauthorize unauthenticated") {
      Post("/moderation/tag-types").withEntity(
        HttpEntity(
          ContentTypes.`application/json`,
          """{"label": "valid TagType", "display":"HIDDEN", "requiredForEnrichment": false}"""
        )
      ) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbid authenticated citizen") {
      Post("/moderation/tag-types")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{"label": "valid TagType", "display":"HIDDEN", "requiredForEnrichment": false}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated moderator") {
      Post("/moderation/tag-types")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{"label": "valid TagType", "display":"HIDDEN", "weight": 0, "requiredForEnrichment": false}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }
  }

  feature("read a tagType") {
    val helloWorldTagType =
      TagType(TagTypeId("hello-tag-type"), "hello tag type", TagTypeDisplay.Displayed, requiredForEnrichment = false)

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
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated moderator on existing tag type") {
      Get("/moderation/tag-types/hello-tag-type")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val tagType: TagTypeResponse = entityAs[TagTypeResponse]
        tagType.id should be(helloWorldTagType.tagTypeId)
        tagType.label should be(helloWorldTagType.label)
        tagType.display should be(helloWorldTagType.display)
      }
    }

    scenario("not found and allow authenticated moderator on a non existing tag type") {
      Get("/moderation/tag-types/fake-tag-type")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }

  feature("update a tagType") {
    val helloWorldTagType =
      TagType(TagTypeId("hello-tag-type"), "hello tag type", TagTypeDisplay.Displayed, requiredForEnrichment = false)
    val newHelloWorldTagType =
      TagType(TagTypeId("hello-tag-type"), "new label", TagTypeDisplay.Hidden, requiredForEnrichment = true)

    when(tagTypeService.getTagType(ArgumentMatchers.eq(helloWorldTagType.tagTypeId)))
      .thenReturn(Future.successful(Some(helloWorldTagType)))
    when(
      tagTypeService.updateTagType(
        ArgumentMatchers.eq(TagTypeId("fake-tag-type")),
        ArgumentMatchers.any[String],
        ArgumentMatchers.any[TagTypeDisplay],
        ArgumentMatchers.any[Int],
        ArgumentMatchers.any[Boolean]
      )
    ).thenReturn(Future.successful(None))
    when(
      tagTypeService.updateTagType(
        ArgumentMatchers.eq(helloWorldTagType.tagTypeId),
        ArgumentMatchers.eq("new label"),
        ArgumentMatchers.eq(TagTypeDisplay.Hidden),
        ArgumentMatchers.any[Int],
        ArgumentMatchers.eq(true)
      )
    ).thenReturn(Future.successful(Some(newHelloWorldTagType)))

    scenario("unauthorize unauthenticated") {
      Put("/moderation/tag-types/hello-tag-type").withEntity(
        HttpEntity(
          ContentTypes.`application/json`,
          """{"label": "new label", "display":"HIDDEN", "requiredForEnrichment": true}"""
        )
      ) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbid authenticated citizen") {
      Put("/moderation/tag-types/hello-tag-type")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{"label": "new label", "display":"HIDDEN", "requiredForEnrichment": true}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated moderator on existing tag type") {
      Put("/moderation/tag-types/hello-tag-type")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{"label": "new label", "display":"HIDDEN", "weight": 0, "requiredForEnrichment": true}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
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
          HttpEntity(
            ContentTypes.`application/json`,
            """{"label": "new label", "display":"HIDDEN", "weight": 0, "requiredForEnrichment": true}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }
}
