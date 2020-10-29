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
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.util.ByteString
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.{
  OperationOfQuestionService,
  OperationOfQuestionServiceComponent,
  OperationService,
  OperationServiceComponent
}
import org.make.api.proposal._
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.api.technical.storage.Content.FileContent
import org.make.api.technical.storage.{FileType, StorageService, StorageServiceComponent, UploadResponse}
import org.make.core.operation._
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.indexed._
import org.make.core.proposal.{ProposalId, _}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.Future
import scala.concurrent.duration._

class ModerationQuestionApiTest
    extends MakeApiTestBase
    with DefaultModerationQuestionComponent
    with ProposalServiceComponent
    with QuestionServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with MakeAuthentication
    with StorageServiceComponent
    with OperationOfQuestionServiceComponent
    with OperationServiceComponent {

  override val questionService: QuestionService = mock[QuestionService]

  val routes: Route = sealRoute(moderationQuestionApi.routes)

  override lazy val proposalService: ProposalService = mock[ProposalService]
  override lazy val storageService: StorageService = mock[StorageService]
  override lazy val operationService: OperationService = mock[OperationService]
  override lazy val operationOfQuestionService: OperationOfQuestionService =
    mock[OperationOfQuestionService]

  val baseSimpleOperation: SimpleOperation =
    TestUtils.simpleOperation(id = OperationId("operation-id"))
  val baseQuestion: Question =
    TestUtils.question(id = QuestionId("question-id"), operationId = Some(OperationId("operation-id")))
  val baseOperationOfQuestion: OperationOfQuestion =
    TestUtils.operationOfQuestion(QuestionId("question-id"), OperationId("operation-id"))

  Feature("list questions") {

    when(questionService.countQuestion(any[SearchQuestionRequest])).thenReturn(Future.successful(42))
    when(questionService.searchQuestion(any[SearchQuestionRequest])).thenReturn(Future.successful(Seq(baseQuestion)))

    val uri = "/moderation/questions?start=0&end=1&operationId=foo&country=FR&language=fr"

    Scenario("authenticated list questions") {
      Get(uri).withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        header("x-total-count").isDefined shouldBe true
        val questions: Seq[ModerationQuestionResponse] = entityAs[Seq[ModerationQuestionResponse]]
        questions.size should be(1)
        questions.head.id.value should be(baseQuestion.questionId.value)
      }

    }
    Scenario("unauthorized list questions") {
      Get(uri) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
    Scenario("forbidden list questions") {
      Get(uri).withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
  }

  Feature("get question") {
    def uri(id: String = "question-id"): String = s"/moderation/questions/$id"

    when(questionService.getQuestion(any[QuestionId]))
      .thenReturn(Future.successful(None))

    when(questionService.getQuestion(QuestionId("question-id")))
      .thenReturn(Future.successful(Some(baseQuestion)))

    Scenario("authenticated get question") {
      Get(uri()).withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
    Scenario("unauthorized get question") {
      Get(uri()) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
    Scenario("forbidden get question") {
      Get(uri()).withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    Scenario("not found get question") {
      Get(uri("not-found"))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }

  Feature("refuse initial proposals") {
    val uri: String = "/moderation/questions/question-id/initial-proposals/refuse"

    Scenario("unauthorized refuse proposals") {
      Post(uri) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbidden refuse proposal (citizen)") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbidden create question (moderator)") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("authenticated refuse initial proposals") {
      when(proposalService.search(any[Option[UserId]], any[SearchQuery], any[RequestContext])).thenReturn(
        Future.successful(
          ProposalsSearchResult(
            total = 1,
            results = Seq(
              TestUtils.indexedProposal(
                id = ProposalId("aaa-bbb-ccc"),
                userId = UserId("foo-bar"),
                content = "il faut fou",
                createdAt = DateHelper.now(),
                updatedAt = None,
                toEnrich = true,
                initialProposal = true
              )
            )
          )
        )
      )

      when(
        proposalService
          .refuseProposal(eqTo(ProposalId("aaa-bbb-ccc")), any[UserId], any[RequestContext], any[RefuseProposalRequest])
      ).thenReturn(
        Future.successful(
          Some(
            ModerationProposalResponse(
              id = ProposalId("aaa-bbb-ccc"),
              proposalId = ProposalId("aaa-bbb-ccc"),
              content = "il faut fou",
              slug = "il-faut-fou",
              author = ModerationProposalAuthorResponse(
                UserId("Georges RR Martin"),
                firstName = Some("Georges"),
                lastName = Some("Martin"),
                displayName = Some("Georges Martin"),
                organisationName = None,
                postalCode = None,
                age = None,
                avatarUrl = None,
                organisationSlug = None
              ),
              labels = Seq(),
              status = Accepted,
              tags = Seq(),
              votes = Seq(Vote.empty(VoteKey.Agree), Vote.empty(VoteKey.Disagree), Vote.empty(VoteKey.Neutral)),
              context = RequestContext.empty.copy(country = Some(Country("FR")), language = Some(Language("fr"))),
              createdAt = Some(DateHelper.now()),
              updatedAt = Some(DateHelper.now()),
              events = Nil,
              idea = None,
              ideaProposals = Seq.empty,
              operationId = None,
              questionId = Some(QuestionId("my-question"))
            )
          )
        )
      )

      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }
  }

  Feature("create initial proposals") {
    val uri = "/moderation/questions/question-id/initial-proposals"
    val request =
      """
      |{
      | "content": "Il faut test",
      | "country": "FR",
      | "author":
      | {
      |  "age": "42",
      |  "firstName": "name"
      | },
      | "tags": []
      |}
    """.stripMargin

    val requestWithoutCountry =
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

    val requestWithBadCountry =
      """
      |{
      | "content": "Il faut test",
      | "country": "ES",
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
        any[Country],
        any[Seq[TagId]],
        any[AuthorRequest],
        any[UserId],
        any[RequestContext]
      )
    ).thenReturn(Future.successful(ProposalId("proposal-id")))

    Scenario("authenticated create proposal") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.Created)
        val proposalIdResponse: ProposalIdResponse = entityAs[ProposalIdResponse]
        proposalIdResponse.id.value should be("proposal-id")
        proposalIdResponse.proposalId.value should be("proposal-id")
      }
    }
    Scenario("unauthorized create proposal") {
      Post(uri)
        .withEntity(HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
    Scenario("forbidden create proposal (citizen)") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    Scenario("forbidden create proposal (moderator)") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    Scenario("bad request create proposal: firstName None") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, badRequest1)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
    Scenario("bad request create proposal: firstName empty string") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, badRequest2)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
    Scenario("bad request create proposal: no country") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, requestWithoutCountry)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
    Scenario("bad request create proposal: country not in question") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin)))
        .withEntity(HttpEntity(ContentTypes.`application/json`, requestWithBadCountry)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  Feature("upload image") {
    implicit val timeout: RouteTestTimeout = RouteTestTimeout(300.seconds)
    def uri(id: String = "question-id") = s"/moderation/questions/$id/image"

    when(questionService.getQuestion(QuestionId("question-id-no-operation")))
      .thenReturn(Future.successful(Some(baseQuestion.copy(operationId = None))))
    when(operationOfQuestionService.findByQuestionId(QuestionId("fake-question")))
      .thenReturn(Future.successful(None))
    when(operationOfQuestionService.findByQuestionId(QuestionId("question-id")))
      .thenReturn(Future.successful(Some(baseOperationOfQuestion)))
    when(operationService.findOneSimple(OperationId("operation-id")))
      .thenReturn(Future.successful(Some(baseSimpleOperation)))

    Scenario("unauthorized not connected") {
      Post(uri()) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbidden citizen") {
      Post(uri())
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbidden moderator") {
      Post(uri())
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("question not found") {
      Post(uri("fake-question"))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    Scenario("incorrect file type") {
      val request: Multipart = Multipart.FormData(fields = Map(
        "data" -> HttpEntity
          .Strict(ContentTypes.`application/x-www-form-urlencoded`, ByteString("incorrect file type"))
      )
      )

      Post(uri(), request)
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("storage unavailable") {
      when(storageService.uploadFile(eqTo(FileType.Operation), any[String], any[String], any[FileContent]))
        .thenReturn(Future.failed(new Exception("swift client error")))
      val request: Multipart =
        Multipart.FormData(
          Multipart.FormData.BodyPart
            .Strict(
              "data",
              HttpEntity.Strict(ContentType(MediaTypes.`image/jpeg`), ByteString("image")),
              Map("filename" -> "image.jpeg")
            )
        )

      Post(uri(), request)
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.InternalServerError)
      }
    }

    Scenario("large file successfully uploaded and returned by admin") {
      when(storageService.uploadFile(eqTo(FileType.Operation), any[String], any[String], any[FileContent]))
        .thenReturn(Future.successful("path/to/uploaded/image.jpeg"))

      def entityOfSize(size: Int): Multipart = Multipart.FormData(
        Multipart.FormData.BodyPart
          .Strict(
            "data",
            HttpEntity.Strict(ContentType(MediaTypes.`image/jpeg`), ByteString("0" * size)),
            Map("filename" -> "image.jpeg")
          )
      )
      Post(uri(), entityOfSize(256000 + 1))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)

        val path: UploadResponse = entityAs[UploadResponse]
        path.path shouldBe "path/to/uploaded/image.jpeg"
      }
    }
  }
}
