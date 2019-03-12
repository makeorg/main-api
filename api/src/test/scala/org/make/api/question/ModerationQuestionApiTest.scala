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

package org.make.api.question
import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal.{
  ModerationProposalResponse,
  ProposalService,
  ProposalServiceComponent,
  RefuseProposalRequest
}
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.api.user.UserResponse
import org.make.core.{DateHelper, RequestContext}
import org.make.core.auth.UserRights
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.{ProposalId, _}
import org.make.core.proposal.indexed._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers.{any, eq => matches}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class ModerationQuestionApiTest
    extends MakeApiTestBase
    with MockitoSugar
    with DefaultModerationQuestionComponent
    with ProposalServiceComponent
    with QuestionServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with MakeAuthentication {

  override val questionService: QuestionService = mock[QuestionService]

  val routes: Route = sealRoute(moderationQuestionApi.routes)

  override lazy val proposalService: ProposalService = mock[ProposalService]

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
          AuthInfo(UserRights(UserId("my-citizen-user-id"), Seq(RoleCitizen), Seq.empty), None, Some("citizen"), None)
        )
      )
    )
  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(UserId("my-moderator-user-id"), Seq(RoleModerator), Seq.empty),
            None,
            Some("moderator"),
            None
          )
        )
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future.successful(
        Some(AuthInfo(UserRights(UserId("my-admin-user-id"), Seq(RoleAdmin), Seq.empty), None, Some("admin"), None))
      )
    )

  val baseQuestion = Question(QuestionId("question-id"), "slug", Country("FR"), Language("fr"), "Slug ?", None, None)

  feature("list questions") {

    when(questionService.countQuestion(any[SearchQuestionRequest])).thenReturn(Future.successful(42))
    when(questionService.searchQuestion(any[SearchQuestionRequest])).thenReturn(Future.successful(Seq(baseQuestion)))

    val uri = "/moderation/questions?start=0&end=1&operationId=foo&country=FR&language=fr"

    scenario("authenticated list questions") {
      Get(uri).withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        header("x-total-count").isDefined shouldBe true
        val questions: Seq[ModerationQuestionResponse] = entityAs[Seq[ModerationQuestionResponse]]
        questions.size should be(1)
        questions.head.id.value should be(baseQuestion.questionId.value)
      }

    }
    scenario("unauthorized list questions") {
      Get(uri) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
    scenario("forbidden list questions") {
      Get(uri).withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
  }

  feature("get question") {
    def uri(id: String = "question-id"): String = s"/moderation/questions/$id"

    when(questionService.getQuestion(any[QuestionId]))
      .thenReturn(Future.successful(None))

    when(questionService.getQuestion(QuestionId("question-id")))
      .thenReturn(Future.successful(Some(baseQuestion)))

    scenario("authenticated get question") {
      Get(uri()).withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
    scenario("unauthorized get question") {
      Get(uri()) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
    scenario("forbidden get question") {
      Get(uri()).withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    scenario("not found get question") {
      Get(uri("not-found"))
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }

  feature("create question") {
    val uri = "/moderation/questions"
    val request =
      """
        |{
        | "question": "question",
        | "country": "FR",
        | "language": "fr",
        | "slug": "question-slug"
        |}
      """.stripMargin

    when(questionService.createQuestion(any[Country], any[Language], any[String], any[String]))
      .thenReturn(Future.successful(baseQuestion))

    scenario("authenticated create question") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
    scenario("unauthorized create question") {
      Post(uri)
        .withEntity(HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
    scenario("forbidden create question (citizen)") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    scenario("forbidden create question (moderator)") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    scenario("bad request create question") {
      Post(uri).withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  feature("refuse initial proposals") {
    val uri: String = "/moderation/questions/question-id/initial-proposals/refuse"

    scenario("unauthorized refuse proposals") {
      Post(uri) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbidden refuse proposal (citizen)") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("forbidden create question (moderator)") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated refuse initial proposals") {
      when(proposalService.search(any[Option[UserId]], any[SearchQuery], any[RequestContext])).thenReturn(
        Future.successful(
          ProposalsSearchResult(
            total = 1,
            results = Seq(
              IndexedProposal(
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
                country = Country("FR"),
                language = Language("fr"),
                themeId = None,
                tags = Seq.empty,
                votesCount = 0,
                votesVerifiedCount = 0,
                toEnrich = true,
                operationId = None,
                questionId = None,
                initialProposal = true,
                ideaId = None,
                refusalReason = None,
                scores = IndexedScores.empty,
                sequencePool = SequencePool.New
              )
            )
          )
        )
      )

      when(
        proposalService
          .refuseProposal(
            matches(ProposalId("aaa-bbb-ccc")),
            any[UserId],
            any[RequestContext],
            any[RefuseProposalRequest]
          )
      ).thenReturn(
        Future.successful(
          Some(
            ModerationProposalResponse(
              proposalId = ProposalId("aaa-bbb-ccc"),
              content = "il faut fou",
              slug = "il-faut-fou",
              author = UserResponse(
                UserId("Georges RR Martin"),
                email = "g@rr.martin",
                firstName = Some("Georges"),
                lastName = Some("Martin"),
                organisationName = None,
                enabled = true,
                emailVerified = true,
                isOrganisation = false,
                lastConnection = DateHelper.now(),
                roles = Seq.empty,
                None,
                country = Country("FR"),
                language = Language("fr"),
                isHardBounce = false,
                lastMailingError = None,
                hasPassword = false,
                followedUsers = Seq.empty
              ),
              labels = Seq(),
              theme = None,
              status = Accepted,
              tags = Seq(),
              votes = Seq(
                Vote(key = VoteKey.Agree, qualifications = Seq.empty),
                Vote(key = VoteKey.Disagree, qualifications = Seq.empty),
                Vote(key = VoteKey.Neutral, qualifications = Seq.empty)
              ),
              context = RequestContext.empty,
              createdAt = Some(DateHelper.now()),
              updatedAt = Some(DateHelper.now()),
              events = Nil,
              idea = None,
              ideaProposals = Seq.empty,
              operationId = None,
              language = Some(Language("fr")),
              country = Some(Country("FR")),
              questionId = Some(QuestionId("my-question"))
            )
          )
        )
      )

      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }
  }

  feature("create initial proposals") {
    val uri = "/moderation/questions/question-id/initial-proposals"
    val request =
      """
      |{
      | "content": "Il faut test",
      | "author":
      | {
      |  "age": "42",
      |  "firstName": "name"
      | },
      | "tags": []
      |}
    """.stripMargin

    val badRequest1 =
      """
        |{
        | "content": "Il faut test",
        | "author":
        | {
        |  "age": "42",
        |  "lastName": "name"
        | },
        | "tags": []
        |}
      """.stripMargin

    val badRequest2 =
      """
        |{
        | "content": "Il faut test",
        | "author":
        | {
        |  "age": "42",
        |  "firstName": "",
        |  "lastName": "name"
        | },
        | "tags": []
        |}
      """.stripMargin

    when(questionService.getQuestion(QuestionId("question-id")))
      .thenReturn(Future.successful(Some(baseQuestion)))

    when(
      proposalService.createInitialProposal(
        any[String],
        any[Question],
        any[Seq[TagId]],
        any[AuthorRequest],
        any[UserId],
        any[RequestContext]
      )
    ).thenReturn(Future.successful(ProposalId("proposal-id")))

    scenario("authenticated create proposal") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.Created)
        val proposalIdResponse: ProposalIdResponse = entityAs[ProposalIdResponse]
        proposalIdResponse.proposalId.value should be("proposal-id")
      }
    }
    scenario("unauthorized create proposal") {
      Post(uri)
        .withEntity(HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
    scenario("forbidden create proposal (citizen)") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(validCitizenAccessToken)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    scenario("forbidden create proposal (moderator)") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(validModeratorAccessToken)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    scenario("bad request create proposal: firstName None") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, badRequest1)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
    scenario("bad request create proposal: firstName empty string") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(validAdminAccessToken)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, badRequest2)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }
}
