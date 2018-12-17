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

package org.make.api.tag

import java.util.Date

import akka.http.scaladsl.model.headers.{Accept, Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.core.RequestContext
import org.make.core.auth.UserRights
import org.make.core.operation.OperationId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => matches}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class ModerationTagApiTest
    extends MakeApiTestBase
    with DefaultModerationTagApiComponent
    with MockitoSugar
    with TagServiceComponent
    with QuestionServiceComponent {

  override val tagService: TagService = mock[TagService]

  override val questionService: QuestionService = mock[QuestionService]

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
        Some(AuthInfo(UserRights(UserId("my-citizen-user-id"), Seq(RoleCitizen)), None, Some("citizen"), None))
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(
      Future.successful(
        Some(AuthInfo(UserRights(UserId("my-moderator-user-id"), Seq(RoleModerator)), None, Some("moderator"), None))
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future
        .successful(Some(AuthInfo(UserRights(UserId("my-admin-user-id"), Seq(RoleAdmin)), None, Some("admin"), None)))
    )

  when(questionService.getQuestion(any[QuestionId])).thenReturn(
    Future.successful(
      Some(
        Question(
          QuestionId("1234-1234-1234-1234"),
          slug = "my-question",
          Country("FR"),
          Language("fr"),
          "?",
          Some(OperationId("1234-1234-1234-1234")),
          None
        )
      )
    )
  )

  val validTagText: String = "tag"
  val helloWorldTagText: String = "hello world"
  val helloWorldTagSlug: String = "hello-world"
  val fakeTagText: String = "fake-tag"

  def newTag(label: String, tagId: TagId = idGenerator.nextTagId()): Tag = Tag(
    tagId = tagId,
    label = label,
    display = TagDisplay.Inherit,
    weight = 0f,
    tagTypeId = TagTypeId("11111111-1111-1111-1111-11111111111"),
    operationId = Some(OperationId("operation-id")),
    themeId = None,
    country = Country("FR"),
    language = Language("fr"),
    questionId = Some(QuestionId("1234-1234-1234-1234"))
  )

  val validTag: Tag = newTag(validTagText, TagId("valid-tag"))
  val newValidTag: Tag = newTag(s"new$validTagText", TagId("valid-tag"))
  val helloWorldTagId: String = "hello-world"
  val helloWorldTag: Tag = newTag(helloWorldTagText, TagId(helloWorldTagId))
  val fakeTag: Tag = newTag(fakeTagText)
  val tag1: Tag = newTag("tag1", TagId("tag1"))
  val tag2: Tag = newTag("tag2", TagId("tag2"))

  when(
    tagService.createTag(ArgumentMatchers.eq(validTagText), any[TagTypeId], any[Question], any[TagDisplay], any[Float])
  ).thenReturn(Future.successful(validTag))
  when(
    tagService
      .createTag(ArgumentMatchers.eq(s"new$validTagText"), any[TagTypeId], any[Question], any[TagDisplay], any[Float])
  ).thenReturn(Future.successful(newValidTag))
  when(tagService.findByLabel(ArgumentMatchers.eq(validTagText), any[Boolean]))
    .thenReturn(Future.successful(Seq(validTag)))
  when(tagService.findByLabel(ArgumentMatchers.eq(s"$validTagText UPDATED"), any[Boolean]))
    .thenReturn(Future.successful(Seq.empty))
  when(tagService.findByLabel(ArgumentMatchers.eq(s"new$validTagText"), any[Boolean]))
    .thenReturn(Future.successful(Seq.empty))
  when(tagService.getTag(ArgumentMatchers.eq(TagId(fakeTagText))))
    .thenReturn(Future.successful(None))
  when(tagService.getTag(ArgumentMatchers.eq(TagId("valid-tag"))))
    .thenReturn(Future.successful(Some(validTag)))
  when(tagService.getTag(ArgumentMatchers.eq(TagId(helloWorldTagId))))
    .thenReturn(Future.successful(Some(helloWorldTag)))
  when(tagService.findAll())
    .thenReturn(Future.successful(Seq(tag1, tag2)))
  when(
    tagService.find(
      any[Int],
      any[Option[Int]],
      ArgumentMatchers.eq(Some("label")),
      ArgumentMatchers.eq(Some("ASC")),
      any[Boolean],
      any[TagFilter]
    )
  ).thenReturn(Future.successful(Seq(tag1, tag2)))

  when(
    tagService.updateTag(
      ArgumentMatchers.eq(TagId(helloWorldTagId)),
      ArgumentMatchers.eq(s"$validTagText UPDATED"),
      any[TagDisplay],
      any[TagTypeId],
      any[Float],
      any[Question],
      any[RequestContext]
    )
  ).thenReturn(
    Future.successful(
      Some(newTag(s"$validTagText UPDATED", TagId(helloWorldTagId)).copy(tagTypeId = TagTypeId("1234-1234-1234-1234")))
    )
  )

  when(
    tagService.updateTag(
      ArgumentMatchers.eq(TagId(fakeTagText)),
      ArgumentMatchers.eq(s"$validTagText UPDATED"),
      any[TagDisplay],
      any[TagTypeId],
      any[Float],
      any[Question],
      any[RequestContext]
    )
  ).thenReturn(Future.successful(None))

  when(tagService.count(any[TagFilter])).thenReturn(Future.successful(2))

  val routes: Route = sealRoute(moderationTagApi.routes)

  feature("create a tag") {
    scenario("unauthenticated") {
      Given("an un authenticated user")
      When("the user wants to create a tag")
      Then("he should get an unauthorized (401) return code")
      Post("/moderation/tags").withEntity(
        HttpEntity(ContentTypes.`application/json`, s"""{"label": "$validTagText"}""")
      ) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated citizen") {
      Given("an authenticated user with the citizen role")
      When("the user wants to create a tag")
      Then("he should get an forbidden (403) return code")

      Post("/moderation/tags")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"label": "$validTagText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated moderator") {
      Given("an authenticated user with the moderator role")
      When("the user wants to create a tag")
      Then("the tag should be saved if valid")
      val tagRequest =
        s"""{
           |"label": "new$validTagText",
           |"tagTypeId": "1234-1234-1234-1234",
           |"questionId": "1234-1234-1234-1234",
           |"display": "INHERIT"
       }""".stripMargin

      Post("/moderation/tags")
        .withEntity(HttpEntity(ContentTypes.`application/json`, tagRequest))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }

      Given("an authenticated user with the moderator role")
      When("the user wants to create a tag with duplicate label into context")
      Then("the status should be BadRequest")
      val tagRequestDuplicate =
        s"""{
           |"label": "$validTagText",
           |"tagTypeId": "1234-1234-1234-1234",
           |"questionId": "1234-1234-1234-1234",
           |"display": "INHERIT"
       }""".stripMargin

      Post("/moderation/tags")
        .withEntity(HttpEntity(ContentTypes.`application/json`, tagRequestDuplicate))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }

      Given("an authenticated user with the moderator role")
      When("the user wants to create a tag without question")
      Then("the status should be BadRequest")
      val tagRequestEmpty =
        s"""{
           |"label": "$validTagText",
           |"tagTypeId": "1234-1234-1234-1234",
           |"display": "INHERIT"
       }""".stripMargin

      Post("/moderation/tags")
        .withEntity(HttpEntity(ContentTypes.`application/json`, tagRequestEmpty))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  feature("get a tag") {

    scenario("tag not exist") {
      Given("an anonymous user")
      When(s"she wants to get a tag from id '$fakeTagText'")
      Then("she should get a not authorized response")

      Get(s"/moderation/tags/$fakeTagText") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Given("a citizen user")
      When(s"she wants to get a tag from id '$fakeTagText'")
      Then("she should get a forbidden response")

      Get(s"/moderation/tags/$fakeTagText")
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Given(s"a moderator user and a tag id '$fakeTagText' that not exist")
      When(s"she wants to get a tag from id '$fakeTagText'")
      Then("she should get a not found response")

      Get(s"/moderation/tags/$fakeTagText")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("valid tag") {
      Given(s"a registered tag with a label '$helloWorldTagText' and an id '$helloWorldTagSlug'")
      When(s"I get a tag from id '$helloWorldTagId'")
      Then("I should get an ok response")
      And(s"I should get a tag with a label '$helloWorldTagText' and an id '$helloWorldTagSlug")

      Get("/moderation/tags/hello-world")
        .withHeaders(Accept(MediaTypes.`application/json`))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val tag: TagResponse = entityAs[TagResponse]
        tag.label should be(helloWorldTagText)
        tag.id.value should be(helloWorldTagSlug)
      }
    }

    scenario("list tag") {
      Given("an anonymous user")
      Given("when she wants to get the list tag")
      Then("she should get a not authorized response")

      Get("/moderation/tags?_start=0&_end=10&_sort=label&_order=ASC") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

      Given("a citizen user")
      Given("when she wants to get the list tag")
      Then("she should get a forbidden response")

      Get("/moderation/tags?_start=0&_end=10&_sort=label&_order=ASC")
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Given("some registered tags")
      When("I get list tag")
      Then("I get a list of all tags")

      Get("/moderation/tags?_start=0&_end=10&_sort=label&_order=ASC")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        header("x-total-count").map(_.value) should be(Some("2"))
        val tags: Seq[TagResponse] = entityAs[Seq[TagResponse]]
        tags.size should be(2)
        tags(1).id.value should be("tag2")
        tags.head.id.value should be("tag1")
      }
    }
  }

  feature("update a tag") {
    val updateTagRequest =
      s"""{
         |"label": "$validTagText UPDATED",
         |"tagTypeId": "1234-1234-1234-1234",
         |"questionId": "1234-1234-1234-1234",
         |"display": "INHERIT",
         |"weight": "0"
       }""".stripMargin

    scenario("update tag with anonymous user") {
      Given("unauthenticated user")
      When(s"I update tag from id '$fakeTag")
      Then("I get an unauthorized response")
      Put(s"/moderation/tags/$fakeTag") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("update tag with citizen user") {
      Given("an authenticated user with role citizen")
      When(s"I update tag from id '$fakeTag")
      Then("I get a forbidden response")
      Put(s"/moderation/tags/$fakeTag")
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("update non existing tag as moderator") {
      Given("an authenticated user with role moderator")
      When("I update non existing tag")
      Then("I get a not found response")
      Put(s"/moderation/tags/$fakeTagText")
        .withEntity(HttpEntity(ContentTypes.`application/json`, updateTagRequest))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("update tag with an invalid request") {
      Given("a registered tag")
      When("I update tag with an invalid body")
      Then("I get a bad request response")
      Put(s"/moderation/tags/$fakeTagText")
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("update tag") {
      Given("a registered tag 'hello-world'")
      When("I update tag label to 'hello-world UPDATED'")
      Then("I get an OK response")
      Put(s"/moderation/tags/$helloWorldTagId")
        .withEntity(HttpEntity(ContentTypes.`application/json`, updateTagRequest))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val tag: TagResponse = entityAs[TagResponse]
        tag.id.value should be(helloWorldTagId)
        tag.tagTypeId should be(TagTypeId("1234-1234-1234-1234"))
      }
    }

  }
}
