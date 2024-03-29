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

package org.make.api.proposal

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import io.circe.syntax._
import org.make.api.idea.{IdeaService, IdeaServiceComponent}
import org.make.api.operation.{OperationService, OperationServiceComponent}
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.api.technical.security.SecurityConfiguration
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.operation.OperationId
import org.make.core.proposal.indexed.IndexedContext
import org.make.core.proposal.{ProposalId, ProposalStatus}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference._
import org.make.core.user.Role.{RoleAdmin, RoleModerator}
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationError}

import java.time.ZonedDateTime
import scala.concurrent.Future

class ProposalApiTest
    extends MakeApiTestBase
    with DefaultProposalApiComponent
    with IdeaServiceComponent
    with ProposalServiceComponent
    with UserServiceComponent
    with OperationServiceComponent
    with QuestionServiceComponent {

  override val proposalService: ProposalService = mock[ProposalService]

  override val userService: UserService = mock[UserService]
  override val ideaService: IdeaService = mock[IdeaService]
  override val questionService: QuestionService = mock[QuestionService]
  override val operationService: OperationService = mock[OperationService]
  override val securityConfiguration: SecurityConfiguration = mock[SecurityConfiguration]

  when(questionService.findQuestion(any[Option[OperationId]], any[Country], any[Language])).thenAnswer {
    (operationId: Option[OperationId], country: Country, language: Language) =>
      Future.successful(
        Some(
          Question(
            QuestionId("my-question"),
            slug = "my-question",
            countries = NonEmptyList.of(country),
            language = language,
            question = "my question",
            shortTitle = None,
            operationId = operationId
          )
        )
      )
  }

  when(questionService.getQuestion(any[QuestionId])).thenReturn(
    Future.successful(
      Some(
        Question(
          QuestionId("my-question"),
          slug = "my-question",
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          question = "my question",
          shortTitle = None,
          operationId = Some(OperationId("operation"))
        )
      )
    )
  )

  private val john = TestUtils.user(
    id = UserId("my-user-id"),
    email = "john.snow@night-watch.com",
    firstName = Some("John"),
    lastName = Some("Snoww")
  )

  val daenerys: User = TestUtils.user(
    id = UserId("the-mother-of-dragons"),
    email = "d.narys@tergarian.com",
    firstName = Some("Daenerys"),
    lastName = Some("Tergarian"),
    roles = Seq(RoleAdmin)
  )

  val tyrion: User = TestUtils.user(
    id = UserId("the-dwarf"),
    email = "tyrion@pays-his-debts.com",
    firstName = Some("Tyrion"),
    lastName = Some("Lannister"),
    roles = Seq(RoleModerator)
  )

  when(userService.getUser(any[UserId])).thenReturn(Future.successful(Some(john)))

  val refuseProposalWithReasonEntity: String =
    RefuseProposalRequest(sendNotificationEmail = true, refusalReason = Some("not allowed word")).asJson.toString

  val validProposalText: String = "Il faut que tout le monde respecte les conventions de code"
  val invalidMaxLengthProposalText: String =
    "Il faut que le texte de la proposition n'exède pas une certaine limite, par exemple 140 caractères car sinon, " +
      "ça fait vraiment troooooop long. D'un autre côté on en dit peu en 140 caractères..."

  val invalidMinLengthProposalText: String = "Il faut"

  when(
    proposalService
      .propose(any[User], any[RequestContext], any[ZonedDateTime], eqTo(validProposalText), any[Question], any[Boolean])
  ).thenReturn(Future.successful(ProposalId("my-proposal-id")))

  val proposalResult: ProposalResponse = ProposalResponse(
    id = ProposalId("aaa-bbb-ccc"),
    userId = UserId("foo-bar"),
    content = "il faut fou",
    slug = "il-faut-fou",
    status = ProposalStatus.Accepted,
    createdAt = DateHelper.now(),
    updatedAt = None,
    votes = Seq.empty,
    context = Some(
      ProposalContextResponse.fromIndexedContext(
        IndexedContext(RequestContext.empty.copy(country = Some(Country("TN")), language = Some(Language("ar"))))
      )
    ),
    trending = None,
    labels = Seq.empty,
    author = AuthorResponse(
      firstName = None,
      displayName = None,
      organisationName = None,
      organisationSlug = None,
      postalCode = None,
      age = None,
      avatarUrl = None,
      userType = None
    ),
    organisations = Seq.empty,
    tags = Seq.empty,
    selectedStakeTag = None,
    myProposal = false,
    idea = None,
    operationId = None,
    question = None,
    proposalKey = "pr0p0541k3y",
    keywords = Nil
  )

  when(ideaService.fetchOne(any[IdeaId]))
    .thenReturn(
      Future.successful(
        Some(
          Idea(
            ideaId = IdeaId("foo"),
            name = "Foo",
            createdAt = Some(DateHelper.now()),
            updatedAt = Some(DateHelper.now())
          )
        )
      )
    )

  val routes: Route = sealRoute(proposalApi.routes)

  when(
    questionService
      .getQuestion(eqTo(QuestionId("not-found")))
  ).thenReturn(Future.successful(None))

  Feature("proposing") {
    Scenario("unauthenticated proposal") {
      Given("an un authenticated user")
      When("the user wants to propose")
      Then("he should get an unauthorized (401) return code")
      Post("/proposals").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("authenticated proposal") {
      Given("an authenticated user")
      When("the user wants to propose")
      Then("the proposal should be saved if valid")

      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$validProposalText", "questionId": "question", "language": "fr", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }

    Scenario("invalid proposal due to max length") {
      Given("an authenticated user")
      When("the user wants to propose a long proposal")
      Then("the proposal should be rejected if invalid")

      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$invalidMaxLengthProposalText", "questionId": "question", "language": "fr", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "content")
        contentError should be(
          Some(ValidationError("content", "too_long", Some("content should not be longer than 140")))
        )
      }
    }

    Scenario("invalid proposal due to min length") {
      Given("an authenticated user")
      When("the user wants to propose a short proposal")
      Then("the proposal should be rejected if invalid")

      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$invalidMinLengthProposalText", "questionId": "question", "language": "fr", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "content")
        contentError should be(
          Some(ValidationError("content", "too_short", Some("content should not be shorter than 12")))
        )
      }
    }

    Scenario("invalid proposal with question not found") {
      Given("an authenticated user")
      And("a question not found")
      When("the user want to propose")
      Then("the proposal should be rejected")
      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$validProposalText", "questionId": "not-found", "country": "FR", "language": "fr"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "question")
        contentError should be(
          Some(ValidationError("question", "mandatory", Some("This proposal refers to no known question")))
        )
      }
    }

    Scenario("invalid proposal without language") {
      Given("an authenticated user")
      And("an empty language")
      When("the user want to propose without language")
      Then("the proposal should be rejected")
      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$validProposalText", "questionId": "fake", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "language")
        contentError should be(
          Some(ValidationError("language", "malformed", Some("The field [.language] is missing.")))
        )
      }
    }

    Scenario("invalid proposal without country") {
      Given("an authenticated user")
      And("an empty country")
      When("the user want to propose without country")
      Then("the proposal should be rejected")
      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$validProposalText", "questionId": "fake", "language": "fr"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "country")
        contentError should be(Some(ValidationError("country", "malformed", Some("The field [.country] is missing."))))
      }
    }

    Scenario("valid proposal with question, language and country") {
      Given("an authenticated user")
      And("a valid operationId")
      When("the user want to propose in an operation context")
      Then("the proposal should be saved")

      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$validProposalText", "questionId": "1234-1234", "language": "fr", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }
  }

}
