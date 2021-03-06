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

import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{MediaTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.core.question.QuestionId
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.make.core.Order

import scala.concurrent.Future
import org.make.core.technical.Pagination.{End, Start}

class TagApiTest
    extends MakeApiTestBase
    with DefaultTagApiComponent
    with TagServiceComponent
    with QuestionServiceComponent {

  override val tagService: TagService = mock[TagService]
  override val questionService: QuestionService = mock[QuestionService]

  val validTagText: String = "tag"
  val helloWorldTagText: String = "hello world"
  val helloWorldTagId: String = "hello-world"
  val fakeTag: String = "fake-tag"
  def newTag(label: String, tagId: Option[String] = None): Tag = Tag(
    tagId = TagId(tagId.getOrElse(label)),
    label = label,
    display = TagDisplay.Inherit,
    weight = 0f,
    tagTypeId = TagTypeId("11111111-1111-1111-1111-11111111111"),
    operationId = None,
    questionId = None
  )

  when(tagService.getTag(eqTo(TagId(fakeTag))))
    .thenReturn(Future.successful(None))
  when(tagService.getTag(eqTo(TagId(helloWorldTagId))))
    .thenReturn(Future.successful(Some(newTag(helloWorldTagText, Some(helloWorldTagId)))))

  val routes: Route = sealRoute(tagApi.routes)

  Feature("get a tag") {

    Scenario("tag not exist") {
      Given(s"a tag id '$fakeTag' that not exist")
      When(s"I get a tag from id '$fakeTag'")
      Then("I should get a not found response")

      Get(s"/tags/$fakeTag") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    Scenario("valid tag") {
      Given(s"a registered tag with a label '$helloWorldTagText' and an id '$helloWorldTagId'")
      When(s"I get a tag from id '$helloWorldTagId'")
      Then("I should get an ok response")
      And(s"I should get a tag with a label '$helloWorldTagText' and an id '$helloWorldTagId")

      Get("/tags/hello-world").withHeaders(Accept(MediaTypes.`application/json`)) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val tag: Tag = entityAs[Tag]
        tag.label should be(helloWorldTagText)
        tag.tagId.value should be(helloWorldTagId)
      }
    }
  }

  Feature("list tags") {
    Scenario("list tag") {
      Given("some registered tags")
      When("I get list tag")
      Then("I get a list of all tags")

      when(
        tagService.find(
          eqTo(Start.zero),
          eqTo(Some(End(2))),
          any[Option[String]],
          any[Option[Order]],
          eqTo(true),
          eqTo(TagFilter(questionIds = Some(Seq(QuestionId("foo")))))
        )
      ).thenReturn(Future.successful(Seq(newTag("tag1"), newTag("tag2"))))

      Get("/tags?start=0&end=2&questionId=foo") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val tags: Seq[Tag] = entityAs[Seq[Tag]]
        tags.size should be(2)
        tags(1).tagId.value should be("tag2")
        tags.head.tagId.value should be("tag1")
      }
    }
  }
}
