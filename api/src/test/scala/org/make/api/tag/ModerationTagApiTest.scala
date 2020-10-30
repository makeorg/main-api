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

import akka.http.scaladsl.model.headers.{Accept, Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import org.make.api.MakeApiTestBase
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.core.{Order, RequestContext}
import org.make.core.operation.OperationId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}

import scala.concurrent.Future
import org.make.core.technical.Pagination.{End, Start}

class ModerationTagApiTest
    extends MakeApiTestBase
    with DefaultModerationTagApiComponent
    with TagServiceComponent
    with QuestionServiceComponent {

  override val tagService: TagService = mock[TagService]

  override val questionService: QuestionService = mock[QuestionService]

  when(questionService.getQuestion(any[QuestionId])).thenReturn(
    Future.successful(
      Some(
        Question(
          questionId = QuestionId("1234-1234-1234-1234"),
          slug = "my-question",
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          question = "?",
          shortTitle = None,
          operationId = Some(OperationId("1234-1234-1234-1234"))
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
    questionId = Some(QuestionId("1234-1234-1234-1234"))
  )

  val validTag: Tag = newTag(validTagText, TagId("valid-tag"))
  val newValidTag: Tag = newTag(s"new$validTagText", TagId("valid-tag"))
  val helloWorldTagId: String = "hello-world"
  val helloWorldTag: Tag = newTag(helloWorldTagText, TagId(helloWorldTagId))
  val fakeTag: Tag = newTag(fakeTagText)
  val tag1: Tag = newTag("tag1", TagId("tag1"))
  val tag2: Tag = newTag("tag2", TagId("tag2"))

  when(tagService.createTag(eqTo(validTagText), any[TagTypeId], any[Question], any[TagDisplay], any[Float]))
    .thenReturn(Future.successful(validTag))
  when(
    tagService
      .createTag(eqTo(s"new$validTagText"), any[TagTypeId], any[Question], any[TagDisplay], any[Float])
  ).thenReturn(Future.successful(newValidTag))
  when(tagService.findByLabel(eqTo(validTagText), any[Boolean]))
    .thenReturn(Future.successful(Seq(validTag)))
  when(tagService.findByLabel(eqTo(s"$validTagText UPDATED"), any[Boolean]))
    .thenReturn(Future.successful(Seq.empty))
  when(tagService.findByLabel(eqTo(s"new$validTagText"), any[Boolean]))
    .thenReturn(Future.successful(Seq.empty))
  when(tagService.getTag(eqTo(TagId(fakeTagText))))
    .thenReturn(Future.successful(None))
  when(tagService.getTag(eqTo(TagId("valid-tag"))))
    .thenReturn(Future.successful(Some(validTag)))
  when(tagService.getTag(eqTo(TagId(helloWorldTagId))))
    .thenReturn(Future.successful(Some(helloWorldTag)))
  when(tagService.findAll())
    .thenReturn(Future.successful(Seq(tag1, tag2)))
  when(
    tagService
      .find(any[Start], any[Option[End]], eqTo(Some("label")), eqTo(Some(Order.asc)), any[Boolean], any[TagFilter])
  ).thenReturn(Future.successful(Seq(tag1, tag2)))

  when(
    tagService.updateTag(
      eqTo(TagId(helloWorldTagId)),
      eqTo(s"$validTagText UPDATED"),
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
      eqTo(TagId(fakeTagText)),
      eqTo(s"$validTagText UPDATED"),
      any[TagDisplay],
      any[TagTypeId],
      any[Float],
      any[Question],
      any[RequestContext]
    )
  ).thenReturn(Future.successful(None))

  when(tagService.count(any[TagFilter])).thenReturn(Future.successful(2))

  val routes: Route = sealRoute(moderationTagApi.routes)

  Feature("create a tag") {
    Scenario("unauthenticated") {
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

    Scenario("authenticated citizen") {
      Given("an authenticated user with the citizen role")
      When("the user wants to create a tag")
      Then("he should get an forbidden (403) return code")

      Post("/moderation/tags")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"label": "$validTagText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("authenticated moderator") {
      Given("an authenticated user with the moderator role")
      When("the user wants to create a tag")
      Then("he should get an forbidden (403) return code")

      Post("/moderation/tags")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"label": "$validTagText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("authenticated admin") {
      Given("an authenticated user with the admin role")
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
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }

      Given("an authenticated user with the admin role")
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
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }

      Given("an authenticated user with the admin role")
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
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  Feature("get a tag") {

    Scenario("tag not exist") {
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
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Given(s"a moderator user and a tag id '$fakeTagText' that not exist")
      When(s"she wants to get a tag from id '$fakeTagText'")
      Then("she should get a not found response")

      Get(s"/moderation/tags/$fakeTagText")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    Scenario("valid tag") {
      Given(s"a registered tag with a label '$helloWorldTagText' and an id '$helloWorldTagSlug'")
      When(s"I get a tag from id '$helloWorldTagId'")
      Then("I should get an ok response")
      And(s"I should get a tag with a label '$helloWorldTagText' and an id '$helloWorldTagSlug")

      Get("/moderation/tags/hello-world")
        .withHeaders(Accept(MediaTypes.`application/json`))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val tag: TagResponse = entityAs[TagResponse]
        tag.label should be(helloWorldTagText)
        tag.id.value should be(helloWorldTagSlug)
      }
    }

    Scenario("list tag") {
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
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }

      Given("some registered tags")
      When("I get list tag")
      Then("I get a list of all tags")

      Get("/moderation/tags?_start=0&_end=10&_sort=label&_order=ASC")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        header("x-total-count").map(_.value) should be(Some("2"))
        val tags: Seq[TagResponse] = entityAs[Seq[TagResponse]]
        tags.size should be(2)
        tags(1).id.value should be("tag2")
        tags.head.id.value should be("tag1")
      }
    }
  }

  Feature("update a tag") {
    val updateTagRequest =
      s"""{
         |"label": "$validTagText UPDATED",
         |"tagTypeId": "1234-1234-1234-1234",
         |"questionId": "1234-1234-1234-1234",
         |"display": "INHERIT",
         |"weight": "0"
       }""".stripMargin

    Scenario("update tag with anonymous user") {
      Given("unauthenticated user")
      When(s"I update tag from id '$fakeTag")
      Then("I get an unauthorized response")
      Put(s"/moderation/tags/$fakeTag") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("update tag with citizen user") {
      Given("an authenticated user with role citizen")
      When(s"I update tag from id '$fakeTag")
      Then("I get a forbidden response")
      Put(s"/moderation/tags/$fakeTag")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("update tag with admin user") {
      Given("an authenticated user with role admin")
      When(s"I update tag from id '$fakeTag")
      Then("I get a forbidden response")
      Put(s"/moderation/tags/$fakeTag")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("update non existing tag as admin") {
      Given("an authenticated user with role admin")
      When("I update non existing tag")
      Then("I get a not found response")
      Put(s"/moderation/tags/$fakeTagText")
        .withEntity(HttpEntity(ContentTypes.`application/json`, updateTagRequest))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    Scenario("update tag with an invalid request") {
      Given("a registered tag")
      When("I update tag with an invalid body")
      Then("I get a bad request response")
      Put(s"/moderation/tags/$fakeTagText")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("update tag") {
      Given("a registered tag 'hello-world'")
      When("I update tag label to 'hello-world UPDATED'")
      Then("I get an OK response")
      Put(s"/moderation/tags/$helloWorldTagId")
        .withEntity(HttpEntity(ContentTypes.`application/json`, updateTagRequest))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val tag: TagResponse = entityAs[TagResponse]
        tag.id.value should be(helloWorldTagId)
        tag.tagTypeId should be(TagTypeId("1234-1234-1234-1234"))
      }
    }

  }
}
