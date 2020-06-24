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

package org.make.api.operation

import java.time.ZonedDateTime
import java.util.UUID

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.sequence.{SequenceService, SequenceServiceComponent}
import org.make.api.tag.{TagService, TagServiceComponent}
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.core.operation._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.make.core.user.{Role, User, UserId}
import org.make.core.{DateHelper, ValidationError}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._

import scala.concurrent.Future

class ModerationOperationApiTest
    extends MakeApiTestBase
    with DefaultModerationOperationApiComponent
    with TagServiceComponent
    with SequenceServiceComponent
    with OperationServiceComponent
    with UserServiceComponent {

  override val operationService: OperationService = mock[OperationService]
  override val tagService: TagService = mock[TagService]
  override val sequenceService: SequenceService = mock[SequenceService]
  override val userService: UserService = mock[UserService]

  when(tagService.findByQuestionIds(any[Seq[QuestionId]]))
    .thenReturn(Future.successful(Map.empty[QuestionId, Seq[TagId]]))

  val operationRoutes: Route = sealRoute(moderationOperationApi.routes)
  val userId: UserId = UserId(UUID.randomUUID().toString)
  val now: ZonedDateTime = DateHelper.now()

  private val john = TestUtils.user(
    id = UserId("my-user-id"),
    email = "john.snow@night-watch.com",
    firstName = Some("John"),
    lastName = Some("Snoww")
  )
  val daenerys = TestUtils.user(
    id = UserId("the-mother-of-dragons"),
    email = "d.narys@tergarian.com",
    firstName = Some("Daenerys"),
    lastName = Some("Tergarian"),
    roles = Seq(Role.RoleAdmin)
  )
  val tyrion = TestUtils.user(
    id = UserId("the-dwarf"),
    email = "tyrion@pays-his-debts.com",
    firstName = Some("Tyrion"),
    lastName = Some("Lannister"),
    roles = Seq(Role.RoleModerator)
  )

  val firstOperation: SimpleOperation = SimpleOperation(
    status = OperationStatus.Pending,
    operationId = OperationId("firstOperation"),
    slug = "first-operation",
    defaultLanguage = Language("fr"),
    allowedSources = Seq("core"),
    operationKind = OperationKind.PublicConsultation,
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now())
  )

  val firstFullOperation: Operation = Operation(
    status = OperationStatus.Pending,
    operationId = OperationId("firstOperation"),
    slug = "first-operation",
    defaultLanguage = Language("fr"),
    allowedSources = Seq("core"),
    operationKind = OperationKind.PublicConsultation,
    events = List(
      OperationAction(
        date = now,
        makeUserId = john.userId,
        actionType = OperationCreateAction.name,
        arguments = Map("arg1" -> "valueArg1")
      )
    ),
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now()),
    questions = Seq(
      QuestionWithDetails(
        question = Question(
          country = Country("BR"),
          language = Language("fr"),
          questionId = QuestionId("first-question-id"),
          slug = "first-operation-BR",
          question = "first question?",
          shortTitle = None,
          operationId = Some(OperationId("firstOperation"))
        ),
        details = operationOfQuestion(
          questionId = QuestionId("first-question-id"),
          operationId = OperationId("firstOperation"),
          startDate = Some(ZonedDateTime.parse("2018-02-02T10:15:30+00:00")),
          endDate = None,
          operationTitle = "première operation",
          landingSequenceId = SequenceId("first-sequence-id")
        )
      )
    )
  )

  val secondOperation: SimpleOperation = SimpleOperation(
    status = OperationStatus.Pending,
    operationId = OperationId("secondOperation"),
    slug = "second-operation",
    defaultLanguage = Language("it"),
    allowedSources = Seq("core"),
    operationKind = OperationKind.PublicConsultation,
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now())
  )

  val validCreateJson: String =
    """
      |{
      |  "slug": "my-create-operation",
      |  "defaultLanguage": "fr",
      |  "countriesConfiguration": [
      |    {
      |      "countryCode": "FR",
      |      "tagIds": [
      |        "hello"
      |      ],
      |      "landingSequenceId": "29625b5a-56da-4539-b195-15303187c20b"
      |    }
      |  ],
      |  "allowedSources": ["core"],
      |  "operationKind": "PUBLIC_CONSULTATION"
      |}
    """.stripMargin

  val validUpdateJson: String =
    """
      |{
      |  "status": "Active",
      |  "slug": "my-update-operation",
      |  "defaultLanguage": "fr",
      |  "countriesConfiguration": [
      |    {
      |      "countryCode": "FR",
      |      "tagIds": [
      |        "hello"
      |      ],
      |      "landingSequenceId": "29625b5a-56da-4539-b195-15303187c20b",
      |      "startDate": "2018-02-02",
      |      "endDate": "2018-05-02"
      |    }
      |  ],
      |  "allowedSources": ["core"],
      |  "operationKind": "GREAT_CAUSE"
      |}
    """.stripMargin

  val johnToken = "john-citizen-token"
  val tyrionToken = "tyrion-citizen-token"

  override def customUserByToken: Map[String, User] = Map(johnToken -> john, tyrionToken -> tyrion)

  when(userService.getUser(ArgumentMatchers.eq(john.userId))).thenReturn(Future.successful(Some(john)))
  when(userService.getUser(ArgumentMatchers.eq(tyrion.userId))).thenReturn(Future.successful(Some(tyrion)))
  when(userService.getUser(ArgumentMatchers.eq(daenerys.userId))).thenReturn(Future.successful(Some(daenerys)))

  when(operationService.findOneSimple(OperationId("firstOperation")))
    .thenReturn(Future.successful(Some(firstOperation)))
  when(operationService.findOneSimple(OperationId("fakeid"))).thenReturn(Future.successful(None))
  when(
    operationService.findSimple(
      start = 0,
      end = None,
      sort = None,
      order = None,
      slug = Some("second-operation"),
      operationKinds = None
    )
  ).thenReturn(Future.successful(Seq(secondOperation)))
  when(operationService.count(slug = Some("second-operation"), operationKinds = None))
    .thenReturn(Future.successful(1))
  when(
    operationService.findSimple(start = 0, end = None, sort = None, order = None, slug = None, operationKinds = None)
  ).thenReturn(Future.successful(Seq(firstOperation, secondOperation)))
  when(operationService.count(slug = None, operationKinds = None)).thenReturn(Future.successful(2))
  when(tagService.findByTagIds(Seq(TagId("hello")))).thenReturn(
    Future.successful(
      Seq(
        Tag(
          tagId = TagId("hello"),
          label = "hello",
          display = TagDisplay.Inherit,
          weight = 0f,
          tagTypeId = TagTypeId("11111111-1111-1111-1111-11111111111"),
          operationId = None,
          country = Country("FR"),
          language = Language("fr"),
          questionId = None
        )
      )
    )
  )
  when(tagService.findByTagIds(Seq(TagId("fakeTag")))).thenReturn(Future.successful(Seq()))

  when(operationService.findOneBySlug("my-create-operation")).thenReturn(Future.successful(None))
  when(operationService.findOneBySlug("my-update-operation")).thenReturn(Future.successful(None))
  when(operationService.findOneBySlug("existing-operation-slug"))
    .thenReturn(Future.successful(Some(firstFullOperation)))
  when(operationService.findOneBySlug("existing-operation-slug-second"))
    .thenReturn(Future.successful(Some(firstFullOperation.copy(operationId = OperationId("updateOperationId")))))
  when(
    operationService.create(
      userId = tyrion.userId,
      slug = "my-create-operation",
      defaultLanguage = Language("fr"),
      allowedSources = Seq("core"),
      operationKind = OperationKind.PublicConsultation
    )
  ).thenReturn(Future.successful(OperationId("createdOperationId")))

  when(operationService.findOneSimple(OperationId("updateOperationId")))
    .thenReturn(Future.successful(Some(firstOperation)))
  when(
    operationService.update(
      operationId = OperationId("updateOperationId"),
      userId = tyrion.userId,
      status = Some(OperationStatus.Active),
      slug = Some("my-update-operation"),
      defaultLanguage = Some(Language("fr")),
      allowedSources = Some(Seq("core")),
      operationKind = Some(OperationKind.GreatCause)
    )
  ).thenReturn(Future.successful(Some(OperationId("updateOperationId"))))
  when(
    operationService.update(
      operationId = OperationId("updateOperationId"),
      userId = tyrion.userId,
      status = Some(OperationStatus.Active),
      slug = Some("existing-operation-slug-second"),
      defaultLanguage = Some(Language("fr")),
      allowedSources = Some(Seq("core")),
      operationKind = Some(OperationKind.GreatCause)
    )
  ).thenReturn(Future.successful(Some(OperationId("updateOperationId"))))

  when(userService.getUsersByUserIds(Seq(john.userId)))
    .thenReturn(Future.successful(Seq(john)))

  feature("get operations") {

    scenario("get all operations without authentication") {
      Given("2 registered operations")
      When("I get all proposals without authentication")
      Then("I get an unauthorized status response")
      Get("/moderation/operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> operationRoutes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("get all operations with bad credentials") {
      Given("2 registered operations")
      When("I get all proposals with a citizen role authentication")
      Then("I get a forbidden status response")
      Get("/moderation/operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(johnToken))) ~> operationRoutes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("get all operations") {
      Given("2 registered operations")
      When("I get all proposals")
      Then("I get a list of 2 operations")
      Get("/moderation/operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tyrionToken))) ~> operationRoutes ~> check {
        status should be(StatusCodes.OK)
        header("x-total-count").map(_.value) should be(Some("2"))
        val moderationOperationsResponse: Seq[ModerationOperationResponse] =
          entityAs[Seq[ModerationOperationResponse]]
        moderationOperationsResponse.map { moderationOperationResponse =>
          moderationOperationResponse shouldBe a[ModerationOperationResponse]
        }

        moderationOperationsResponse.count(_.id.value == "firstOperation") should be(1)
        val firstOperationResult: ModerationOperationResponse =
          moderationOperationsResponse.filter(_.id.value == "firstOperation").head
        firstOperationResult.slug should be("first-operation")
        firstOperationResult.defaultLanguage should be(Language("fr"))
      }
    }

    scenario("get an operation by slug") {
      Given("2 registered operations")
      When("I get all proposals with a filter by slug")
      Then("I get a list of 1 operation")
      And("the operation match the slug")
      Get("/moderation/operations?slug=second-operation")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tyrionToken))) ~> operationRoutes ~> check {
        status should be(StatusCodes.OK)
        header("x-total-count").map(_.value) should be(Some("1"))
        val moderationOperationsResponse: Seq[ModerationOperationResponse] =
          entityAs[Seq[ModerationOperationResponse]]
        moderationOperationsResponse.head shouldBe a[ModerationOperationResponse]

        val secondOperationResult: ModerationOperationResponse = moderationOperationsResponse.head
        secondOperationResult.slug should be("second-operation")
        secondOperationResult.defaultLanguage should be(Language("it"))
      }
    }
  }

  feature("get an operation") {

    scenario("get an operation without authentication") {
      Given("2 registered operations")
      When("I get a proposal without authentication")
      Then("I get an unauthorized status response")
      Get("/moderation/operations/firstOperation")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> operationRoutes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("get an operation with bad credentials") {
      Given("2 registered operations")
      When("I get a proposal with a citizen role authentication")
      Then("I get a forbidden status response")
      Get("/moderation/operations/firstOperation")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(johnToken))) ~> operationRoutes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("get an operation with invalid id") {
      Given("2 registered operations")
      When("I get a proposal with an invalid id")
      Then("I get a not found status response")
      Get("/moderation/operations/fakeid")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tyrionToken))) ~> operationRoutes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("get an operation") {
      Given("2 registered operations")
      When("I get a proposal with a moderation authentication")
      Then("the call success")
      Get("/moderation/operations/firstOperation")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tyrionToken))) ~> operationRoutes ~> check {
        status should be(StatusCodes.OK)
        val firstOperationResult: ModerationOperationResponse =
          entityAs[ModerationOperationResponse]
        firstOperationResult shouldBe a[ModerationOperationResponse]
        firstOperationResult.slug should be("first-operation")
        firstOperationResult.defaultLanguage should be(Language("fr"))
      }
    }
  }

  feature("create an operation") {
    scenario("create an operation without authentication") {
      When("I create an operation without authentication")
      Then("I get an unauthorized status response")
      Post("/moderation/operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"$validCreateJson")) ~> operationRoutes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("create an operation with bad credentials") {
      When("I create a proposal with a citizen role authentication")
      Then("I get a forbidden status response")
      Post("/moderation/operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"$validCreateJson"))
        .withHeaders(Authorization(OAuth2BearerToken(johnToken))) ~> operationRoutes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("create an operation") {
      When("I create a proposal with a moderation role authentication")
      Then("I get a success status")
      And("operation is registered")
      Post("/moderation/operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"$validCreateJson"))
        .withHeaders(Authorization(OAuth2BearerToken(tyrionToken))) ~> operationRoutes ~> check {
        status should be(StatusCodes.Created)
      }
    }

    scenario("create an operation with an existing slug") {
      When("I create a proposal with an existing slug")
      Then("I get a bad request status")
      And("a correct error message")
      Post("/moderation/operations")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            "my-create-operation".r.replaceFirstIn(s"$validCreateJson", "existing-operation-slug")
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tyrionToken))) ~> operationRoutes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "slug")
        contentError should be(
          Some(ValidationError("slug", "non_empty", Some("Slug 'existing-operation-slug' already exist")))
        )
      }
    }

  }

  feature("update an operation") {
    scenario("create an operation without authentication") {
      Given("a registered operation")
      When("I update the operation without authentication")
      Then("I get an unauthorized status response")
      Put("/moderation/operations/updateOperationId")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"$validUpdateJson")) ~> operationRoutes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("create an operation with bad credentials") {
      Given("a registered operation")
      When("I update a proposal with a citizen role authentication")
      Then("I get a forbidden status response")
      Put("/moderation/operations/updateOperationId")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"$validUpdateJson"))
        .withHeaders(Authorization(OAuth2BearerToken(johnToken))) ~> operationRoutes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("update an operation") {
      When("I create a proposal with a moderation role authentication")
      Then("I get a success status")
      And("operation is registered")
      Put("/moderation/operations/updateOperationId")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"$validUpdateJson"))
        .withHeaders(Authorization(OAuth2BearerToken(tyrionToken))) ~> operationRoutes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("update an operation with an existing slug") {
      When("I update a proposal with an existing slug")
      Then("I get a bad request status")
      And("a correct error message")
      Put("/moderation/operations/updateOperationId")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            "my-update-operation".r.replaceFirstIn(s"$validUpdateJson", "existing-operation-slug")
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tyrionToken))) ~> operationRoutes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "slug")
        contentError should be(
          Some(ValidationError("slug", "invalid_value", Some("Slug 'existing-operation-slug' already exist")))
        )
      }
    }

    scenario("update an operation with his own slug") {
      When("I update a proposal with his own slug")
      Then("I get a success status")
      And("any error message")
      Put("/moderation/operations/updateOperationId")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            "my-update-operation".r.replaceFirstIn(s"$validUpdateJson", "existing-operation-slug-second")
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tyrionToken))) ~> operationRoutes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }
}
