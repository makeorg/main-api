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

import java.time.ZonedDateTime
import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import io.circe.syntax._
import org.make.api.MakeApiTestBase
import org.make.api.idea.{IdeaService, IdeaServiceComponent}
import org.make.api.operation.{OperationService, OperationServiceComponent}
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.api.theme.{ThemeService, ThemeServiceComponent}
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.core.auth.UserRights
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.operation.OperationId
import org.make.core.proposal.indexed._
import org.make.core.proposal.{ProposalId, ProposalStatus, SearchQuery}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference._
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationError}
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito._
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class ProposalApiTest
    extends MakeApiTestBase
    with ProposalApi
    with IdeaServiceComponent
    with ProposalServiceComponent
    with UserServiceComponent
    with ThemeServiceComponent
    with OperationServiceComponent
    with QuestionServiceComponent {

  override val proposalService: ProposalService = mock[ProposalService]

  override val userService: UserService = mock[UserService]
  override val themeService: ThemeService = mock[ThemeService]
  override val ideaService: IdeaService = mock[IdeaService]
  override val questionService: QuestionService = mock[QuestionService]
  override val operationService: OperationService = mock[OperationService]

  when(questionService.findQuestion(any[Option[ThemeId]], any[Option[OperationId]], any[Country], any[Language]))
    .thenAnswer(
      invocation =>
        Future.successful(
          Some(
            Question(
              QuestionId("my-question"),
              slug = "my-question",
              country = invocation.getArgument[Country](2),
              language = invocation.getArgument[Language](3),
              question = "my question",
              operationId = invocation.getArgument[Option[OperationId]](1),
              themeId = invocation.getArgument[Option[ThemeId]](0)
            )
          )
      )
    )

  when(
    questionService.findQuestionByQuestionIdOrThemeOrOperation(
      any[Option[QuestionId]],
      any[Option[ThemeId]],
      any[Option[OperationId]],
      any[Country],
      any[Language]
    )
  ).thenAnswer(
    invocation =>
      Future.successful(
        Some(
          Question(
            QuestionId("my-question"),
            slug = "my-question",
            country = invocation.getArgument[Country](3),
            language = invocation.getArgument[Language](4),
            question = "my question",
            operationId = invocation.getArgument[Option[OperationId]](2),
            themeId = invocation.getArgument[Option[ThemeId]](1)
          )
        )
    )
  )

  private val john = User(
    userId = UserId("my-user-id"),
    email = "john.snow@night-watch.com",
    firstName = Some("John"),
    lastName = Some("Snoww"),
    lastIp = None,
    hashedPassword = None,
    enabled = true,
    emailVerified = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleCitizen),
    country = Country("FR"),
    language = Language("fr"),
    profile = None,
    createdAt = None,
    updatedAt = None,
    lastMailingError = None,
    availableQuestions = Seq.empty
  )

  val daenerys = User(
    userId = UserId("the-mother-of-dragons"),
    email = "d.narys@tergarian.com",
    firstName = Some("Daenerys"),
    lastName = Some("Tergarian"),
    lastIp = None,
    hashedPassword = None,
    enabled = true,
    emailVerified = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleAdmin),
    country = Country("FR"),
    language = Language("fr"),
    profile = None,
    createdAt = None,
    updatedAt = None,
    availableQuestions = Seq.empty
  )

  val tyrion = User(
    userId = UserId("the-dwarf"),
    email = "tyrion@pays-his-debts.com",
    firstName = Some("Tyrion"),
    lastName = Some("Lannister"),
    lastIp = None,
    hashedPassword = None,
    enabled = true,
    emailVerified = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleModerator),
    country = Country("FR"),
    language = Language("fr"),
    profile = None,
    createdAt = None,
    updatedAt = None,
    availableQuestions = Seq.empty
  )

  when(userService.getUser(any[UserId])).thenReturn(Future.successful(Some(john)))

  val validAccessToken = "my-valid-access-token"
  val adminToken = "my-admin-access-token"
  val moderatorToken = "my-moderator-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)
  private val adminAccessToken = AccessToken(adminToken, None, None, Some(1234567890L), tokenCreationDate)
  private val moderatorAccessToken =
    AccessToken(moderatorToken, None, None, Some(1234567890L), tokenCreationDate)

  val refuseProposalWithReasonEntity: String =
    RefuseProposalRequest(sendNotificationEmail = true, refusalReason = Some("not allowed word")).asJson.toString

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))
  when(oauth2DataHandler.findAccessToken(adminToken)).thenReturn(Future.successful(Some(adminAccessToken)))
  when(oauth2DataHandler.findAccessToken(moderatorToken)).thenReturn(Future.successful(Some(moderatorAccessToken)))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(accessToken)))
    .thenReturn(
      Future.successful(
        Some(AuthInfo(UserRights(john.userId, john.roles, john.availableQuestions), None, Some("user"), None))
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(userId = daenerys.userId, roles = daenerys.roles, daenerys.availableQuestions),
            None,
            None,
            None
          )
        )
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(
      Future.successful(
        Some(AuthInfo(UserRights(tyrion.userId, tyrion.roles, tyrion.availableQuestions), None, None, None))
      )
    )

  val validProposalText: String = "Il faut que tout le monde respecte les conventions de code"
  val invalidMaxLengthProposalText: String =
    "Il faut que le texte de la proposition n'exède pas une certaine limite, par exemple 140 caractères car sinon, " +
      "ça fait vraiment troooooop long. D'un autre côté on en dit peu en 140 caractères..."

  val invalidMinLengthProposalText: String = "Il faut"

  when(
    proposalService
      .propose(
        any[User],
        any[RequestContext],
        any[ZonedDateTime],
        matches(validProposalText),
        any[Question],
        any[Boolean]
      )
  ).thenReturn(Future.successful(ProposalId("my-proposal-id")))

  val proposalResult: ProposalResult = ProposalResult(
    id = ProposalId("aaa-bbb-ccc"),
    userId = UserId("foo-bar"),
    content = "il faut fou",
    slug = "il-faut-fou",
    status = ProposalStatus.Accepted,
    createdAt = DateHelper.now(),
    updatedAt = None,
    votes = Seq.empty,
    context = None,
    trending = None,
    labels = Seq.empty,
    author = Author(None, None, None, None, None, None),
    organisations = Seq.empty,
    country = Country("TN"),
    language = Language("ar"),
    themeId = None,
    tags = Seq.empty,
    myProposal = false,
    idea = None,
    operationId = None,
    questionId = None
  )
  when(
    proposalService
      .searchForUser(any[Option[UserId]], any[SearchQuery], any[RequestContext])
  ).thenReturn(Future.successful(ProposalsResultSeededResponse(1, Seq(proposalResult), Some(42))))

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

  val routes: Route = sealRoute(proposalRoutes)

  when(
    questionService
      .findQuestion(matches(None), matches(Some(OperationId("fake"))), matches(Country("FR")), matches(Language("fr")))
  ).thenReturn(Future.successful(None))

  when(
    questionService
      .findQuestionByQuestionIdOrThemeOrOperation(
        matches(None),
        matches(None),
        matches(Some(OperationId("fake"))),
        matches(Country("FR")),
        matches(Language("fr"))
      )
  ).thenReturn(Future.successful(None))

  feature("proposing") {
    scenario("unauthenticated proposal") {
      Given("an un authenticated user")
      When("the user wants to propose")
      Then("he should get an unauthorized (401) return code")
      Post("/proposals").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated proposal") {
      Given("an authenticated user")
      When("the user wants to propose")
      Then("the proposal should be saved if valid")

      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$validProposalText", "language": "fr", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }

    scenario("invalid proposal due to max length") {
      Given("an authenticated user")
      When("the user wants to propose a long proposal")
      Then("the proposal should be rejected if invalid")

      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$invalidMaxLengthProposalText", "language": "fr", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "content")
        contentError should be(Some(ValidationError("content", Some("content should not be longer than 140"))))
      }
    }

    scenario("invalid proposal due to min length") {
      Given("an authenticated user")
      When("the user wants to propose a short proposal")
      Then("the proposal should be rejected if invalid")

      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$invalidMinLengthProposalText", "language": "fr", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "content")
        contentError should be(Some(ValidationError("content", Some("content should not be shorter than 12"))))
      }
    }

    scenario("invalid proposal due to bad operation") {
      Given("an authenticated user")
      And("a bad operationId")
      When("the user want to propose in an operation context")
      Then("the proposal should be rejected")
      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$validProposalText", "operationId": "fake", "language": "fr", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "question")
        contentError should be(Some(ValidationError("question", Some("This proposal refers to no known question"))))
      }
    }

    scenario("invalid proposal without language") {
      Given("an authenticated user")
      And("an empty language")
      When("the user want to propose without language")
      Then("the proposal should be rejected")
      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$validProposalText", "operationId": "fake", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "language")
        contentError should be(Some(ValidationError("language", Some("The field [.language] is missing."))))
      }
    }

    scenario("invalid proposal without country") {
      Given("an authenticated user")
      And("an empty country")
      When("the user want to propose without country")
      Then("the proposal should be rejected")
      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$validProposalText", "operationId": "fake", "language": "fr"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "country")
        contentError should be(Some(ValidationError("country", Some("The field [.country] is missing."))))
      }
    }

    scenario("valid proposal with operation, language and country") {
      Given("an authenticated user")
      And("a valid operationId")
      When("the user want to propose in an operation context")
      Then("the proposal should be saved")

      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$validProposalText", "operationId": "1234-1234", "language": "fr", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }
  }

}
