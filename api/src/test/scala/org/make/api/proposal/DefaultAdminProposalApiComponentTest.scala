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

import akka.Done
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import io.circe.syntax._
import org.make.api.MakeApiTestBase
import org.make.api.idea.{IdeaService, IdeaServiceComponent}
import org.make.api.operation.{OperationService, OperationServiceComponent}
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.api.tag.{TagService, TagServiceComponent}
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.QualificationKey.{
  DoNotCare,
  DoNotUnderstand,
  Doable,
  Impossible,
  LikeIt,
  NoOpinion,
  NoWay,
  PlatitudeAgree,
  PlatitudeDisagree
}
import org.make.core.proposal.VoteKey.{Agree, Disagree}
import org.make.core.proposal._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext, ValidationError}

import java.time.ZonedDateTime
import scala.concurrent.{Future, Promise}

class DefaultAdminProposalApiComponentTest
    extends MakeApiTestBase
    with DefaultAdminProposalApiComponent
    with ProposalServiceComponent
    with ProposalCoordinatorServiceComponent
    with QuestionServiceComponent
    with IdeaServiceComponent
    with OperationServiceComponent
    with UserServiceComponent
    with TagServiceComponent {

  override val proposalService: ProposalService = mock[ProposalService]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]
  override val questionService: QuestionService = mock[QuestionService]
  override val ideaService: IdeaService = mock[IdeaService]
  override val operationService: OperationService = mock[OperationService]
  override val userService: UserService = mock[UserService]
  override val tagService: TagService = mock[TagService]

  val routes: Route = sealRoute(adminProposalApi.routes)

  Feature("update verified votes") {
    Scenario("unauthorized user") {
      Put("/admin/proposals/123456/fix-trolled-proposal") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbidden citizen") {
      Put("/admin/proposals/123456/fix-trolled-proposal")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbidden moderator") {
      Put("/admin/proposals/123456/fix-trolled-proposal")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("proposal not found") {
      when(proposalCoordinatorService.getProposal(any[ProposalId]))
        .thenReturn(Future.successful(None))

      val verifiedVotesRequest = UpdateProposalVotesRequest(Seq.empty)
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/proposals/invalid/fix-trolled-proposal")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(HttpEntity(ContentTypes.`application/json`, verifiedVotesRequest.asJson.toString)) ~>
          routes ~>
          check {
            status should be(StatusCodes.NotFound)
          }
      }
    }

    Scenario("allowed admin") {

      val proposalCounts123: Proposal = Proposal(
        proposalId = ProposalId("counts-123"),
        slug = "a-song-of-fire-and-ice-2",
        content = "A song of fire and ice 2",
        author = UserId("Georges RR Martin"),
        labels = Seq.empty,
        votes = Seq(
          Vote(
            key = VoteKey.Agree,
            count = 100,
            countVerified = 100,
            countSequence = 100,
            countSegment = 100,
            qualifications = Seq(
              Qualification(LikeIt, 50, 50, 50, 50),
              Qualification(Doable, 50, 50, 50, 50),
              Qualification(PlatitudeAgree, 50, 50, 50, 50)
            )
          ),
          Vote(
            key = VoteKey.Disagree,
            count = 100,
            countVerified = 100,
            countSequence = 100,
            countSegment = 100,
            qualifications = Seq(
              Qualification(NoWay, 50, 50, 50, 50),
              Qualification(Impossible, 50, 50, 50, 50),
              Qualification(PlatitudeDisagree, 50, 50, 50, 50)
            )
          ),
          Vote(
            key = VoteKey.Neutral,
            count = 100,
            countVerified = 100,
            countSequence = 100,
            countSegment = 100,
            qualifications = Seq(
              Qualification(DoNotUnderstand, 50, 50, 50, 50),
              Qualification(DoNotCare, 50, 50, 50, 50),
              Qualification(NoOpinion, 50, 50, 50, 50)
            )
          )
        ),
        questionId = Some(QuestionId("to-be-or-not-to-be")),
        creationContext = RequestContext.empty.copy(country = Some(Country("FR")), language = Some(Language("fr"))),
        createdAt = Some(DateHelper.now()),
        updatedAt = Some(DateHelper.now()),
        events = Nil,
        keywords = Nil
      )
      val hamlet = Question(
        QuestionId("to-be-or-not-to-be"),
        "hamlet",
        NonEmptyList.of(Country("GB")),
        Language("en"),
        "To be or not to be ?",
        None,
        None
      )
      when(proposalCoordinatorService.getProposal(ProposalId("counts-123")))
        .thenReturn(Future.successful(Some(proposalCounts123)))
      when(questionService.getQuestion(QuestionId("to-be-or-not-to-be")))
        .thenReturn(Future.successful(Some(hamlet)))
      when(
        proposalService
          .updateVotes(
            any[ProposalId],
            any[UserId],
            any[RequestContext],
            any[ZonedDateTime],
            any[Seq[UpdateVoteRequest]]
          )
      ).thenReturn(Future.successful(Some(proposal(ProposalId("counts-123")))))
      val verifiedVotesRequest = UpdateProposalVotesRequest(votes = Seq(
        UpdateVoteRequest(
          key = Agree,
          count = Some(12),
          countVerified = Some(12),
          countSequence = Some(12),
          countSegment = Some(12),
          qualifications = Seq(
            UpdateQualificationRequest(
              LikeIt,
              countVerified = Some(1),
              count = Some(1),
              countSequence = Some(1),
              countSegment = Some(1)
            ),
            UpdateQualificationRequest(
              Doable,
              countVerified = Some(2),
              count = Some(2),
              countSequence = Some(2),
              countSegment = Some(2)
            ),
            UpdateQualificationRequest(
              PlatitudeAgree,
              countVerified = Some(3),
              count = Some(3),
              countSequence = Some(3),
              countSegment = Some(3)
            )
          )
        ),
        UpdateVoteRequest(
          key = Disagree,
          count = Some(24),
          countVerified = Some(24),
          countSequence = Some(24),
          countSegment = Some(24),
          qualifications = Seq(
            UpdateQualificationRequest(
              NoWay,
              countVerified = Some(4),
              count = Some(4),
              countSequence = Some(4),
              countSegment = Some(4)
            ),
            UpdateQualificationRequest(
              Impossible,
              countVerified = Some(5),
              count = Some(5),
              countSequence = Some(5),
              countSegment = Some(5)
            ),
            UpdateQualificationRequest(
              PlatitudeDisagree,
              countVerified = Some(6),
              count = Some(6),
              countSequence = Some(6),
              countSegment = Some(6)
            )
          )
        ),
        UpdateVoteRequest(
          key = VoteKey.Neutral,
          count = Some(36),
          countVerified = Some(36),
          countSequence = Some(36),
          countSegment = Some(36),
          qualifications = Seq(
            UpdateQualificationRequest(
              NoOpinion,
              countVerified = Some(7),
              count = Some(7),
              countSequence = Some(7),
              countSegment = Some(7)
            ),
            UpdateQualificationRequest(
              DoNotUnderstand,
              countVerified = Some(8),
              count = Some(8),
              countSequence = Some(8),
              countSegment = Some(8)
            ),
            UpdateQualificationRequest(
              DoNotCare,
              countVerified = Some(9),
              count = Some(9),
              countSequence = Some(9),
              countSegment = Some(9)
            )
          )
        )
      )
      )
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/proposals/counts-123/fix-trolled-proposal")
          .withEntity(HttpEntity(ContentTypes.`application/json`, verifiedVotesRequest.asJson.toString))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
        }
      }
    }
  }

  Feature("reset votes") {
    Scenario("unauthorized user") {
      Post("/admin/proposals/reset-votes") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbidden citizen") {
      Post("/admin/proposals/reset-votes")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbidden moderator") {
      Post("/admin/proposals/reset-votes")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allowed admin") {
      when(proposalService.resetVotes(any[UserId], any[RequestContext]))
        .thenReturn(Future.successful(Done))
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/proposals/reset-votes")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.Accepted)
        }
      }
    }

    Scenario("allowed admin with a lot of proposals") {
      when(proposalService.resetVotes(any[UserId], any[RequestContext]))
        .thenReturn(Promise[Done]().future)
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/proposals/reset-votes")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.Accepted)
        }
      }
    }
  }

  Feature("patch") {
    val id = ProposalId("patch-proposal-id")
    when(proposalService.patchProposal(eqTo(id), any[UserId], any[RequestContext], any[PatchProposalRequest]))
      .thenReturn(Future.successful(Some(proposal(id))))
    when(
      proposalService
        .patchProposal(eqTo(ProposalId("fake")), any[UserId], any[RequestContext], any[PatchProposalRequest])
    ).thenReturn(Future.successful(None))

    when(userService.getUser(eqTo(UserId("fake")))).thenReturn(Future.successful(None))
    when(ideaService.fetchOne(eqTo(IdeaId("fake")))).thenReturn(Future.successful(None))
    when(operationService.findOneSimple(eqTo(OperationId("fake")))).thenReturn(Future.successful(None))
    when(questionService.getQuestion(eqTo(QuestionId("fake")))).thenReturn(Future.successful(None))

    val ideaId = IdeaId("idea")
    val authorId = UserId("author")
    val operationId = OperationId("operation")
    val questionId = QuestionId("question")
    val tags = Seq(TagId("tag-1"), TagId("tag-2"))
    when(ideaService.fetchOne(eqTo(ideaId))).thenReturn(Future.successful(Some(idea(ideaId))))
    when(userService.getUser(eqTo(authorId))).thenReturn(Future.successful(Some(user(authorId))))
    when(operationService.findOneSimple(eqTo(operationId)))
      .thenReturn(Future.successful(Some(simpleOperation(operationId))))
    when(questionService.getQuestion(eqTo(questionId))).thenReturn(Future.successful(Some(question(questionId))))
    when(tagService.findByTagIds(eqTo(tags))).thenReturn(Future.successful(Seq(tag(tags.head), tag(tags(1)))))
    when(tagService.findByTagIds(eqTo(tags :+ TagId("fake"))))
      .thenReturn(Future.successful(Seq(tag(tags.head), tag(tags(1)))))

    val validPatch = PatchProposalRequest(
      ideaId = Some(ideaId),
      author = Some(authorId),
      tags = Some(tags),
      operation = Some(operationId),
      questionId = Some(questionId),
      keywords = Some(Seq(ProposalKeyword(ProposalKeywordKey("key"), "label")))
    )

    Scenario("unauthorized user") {
      Patch("/admin/proposals/patch-proposal-id") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbidden citizen") {
      Patch("/admin/proposals/patch-proposal-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbidden moderator") {
      Patch("/admin/proposals/patch-proposal-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allowed admin") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Patch("/admin/proposals/patch-proposal-id")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(HttpEntity(ContentTypes.`application/json`, validPatch.asJson.toString)) ~> routes ~> check {
          status should be(StatusCodes.OK)
        }
      }
    }

    Scenario("proposal does not exist") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Patch("/admin/proposals/fake")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(HttpEntity(ContentTypes.`application/json`, validPatch.asJson.toString)) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }

    Scenario("idea does not exist") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Patch("/admin/proposals/patch-proposal-id")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(
            HttpEntity(ContentTypes.`application/json`, validPatch.copy(ideaId = Some(IdeaId("fake"))).asJson.toString)
          ) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size shouldBe 1
          errors.head.field shouldBe "ideaId"
        }
      }
    }

    Scenario("author does not exist") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Patch("/admin/proposals/patch-proposal-id")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(
            HttpEntity(ContentTypes.`application/json`, validPatch.copy(author = Some(UserId("fake"))).asJson.toString)
          ) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size shouldBe 1
          errors.head.field shouldBe "author"
        }
      }
    }

    Scenario("operation does not exist") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Patch("/admin/proposals/patch-proposal-id")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(
            HttpEntity(
              ContentTypes.`application/json`,
              validPatch.copy(operation = Some(OperationId("fake"))).asJson.toString
            )
          ) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size shouldBe 1
          errors.head.field shouldBe "operation"
        }
      }
    }

    Scenario("question does not exist") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Patch("/admin/proposals/patch-proposal-id")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(
            HttpEntity(
              ContentTypes.`application/json`,
              validPatch.copy(questionId = Some(QuestionId("fake"))).asJson.toString
            )
          ) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size shouldBe 1
          errors.head.field shouldBe "questionId"
        }
      }
    }

    Scenario("ok even if a tag does not exist") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Patch("/admin/proposals/patch-proposal-id")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(
            HttpEntity(
              ContentTypes.`application/json`,
              validPatch.copy(tags = Some(tags :+ TagId("fake"))).asJson.toString
            )
          ) ~> routes ~> check {
          status should be(StatusCodes.OK)
        }
      }
    }
  }

  Feature("add keywords") {

    val request =
      """
        |[
        |  {"proposalId": "123", "keywords": [
        |    {"key": "a", "label": "à"},
        |    {"key": "b", "label": "ᴃ"}
        |  ]},
        |  {"proposalId": "456", "keywords": []}]""".stripMargin

    when(proposalService.setKeywords(any, any)).thenAnswer {
      (requests: Seq[ProposalKeywordRequest], _: RequestContext) =>
        Future.successful(
          requests.map(
            request =>
              ProposalKeywordsResponse(
                request.proposalId,
                if (request.keywords.isEmpty) ProposalKeywordsResponseStatus.Error
                else ProposalKeywordsResponseStatus.Ok,
                if (request.keywords.isEmpty) Some("warning") else None
              )
          )
        )
    }

    Scenario("forbidden to non-admins") {
      for (maybeToken <- Seq(None, Some(tokenCitizen), Some(tokenModerator)))
        Post("/admin/proposals/keywords")
          .withHeaders(maybeToken.toList.map(token => Authorization(OAuth2BearerToken(token))))
          .withEntity(HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
          status shouldBe maybeToken.fold(StatusCodes.Unauthorized)(_ => StatusCodes.Forbidden)
        }
    }

    Scenario("it works for admins") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/proposals/keywords")
          .withHeaders(Authorization(OAuth2BearerToken(token)))
          .withEntity(HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = entityAs[Seq[ProposalKeywordsResponse]]
          response should contain theSameElementsAs Seq(
            ProposalKeywordsResponse(ProposalId("123"), ProposalKeywordsResponseStatus.Ok, None),
            ProposalKeywordsResponse(ProposalId("456"), ProposalKeywordsResponseStatus.Error, Some("warning"))
          )
        }
      }
    }

  }

  private def proposal(id: ProposalId): ModerationProposalResponse = {
    ModerationProposalResponse(
      id = id,
      proposalId = id,
      slug = "a-song-of-fire-and-ice",
      content = "A song of fire and ice",
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
      votes = Seq(
        Vote(
          key = VoteKey.Agree,
          qualifications = Seq.empty,
          count = 0,
          countVerified = 0,
          countSequence = 0,
          countSegment = 0
        ),
        Vote(
          key = VoteKey.Disagree,
          qualifications = Seq.empty,
          count = 0,
          countVerified = 0,
          countSequence = 0,
          countSegment = 0
        ),
        Vote(
          key = VoteKey.Neutral,
          qualifications = Seq.empty,
          count = 0,
          countVerified = 0,
          countSequence = 0,
          countSegment = 0
        )
      ),
      context = RequestContext.empty.copy(country = Some(Country("FR")), language = Some(Language("fr"))),
      createdAt = Some(DateHelper.now()),
      updatedAt = Some(DateHelper.now()),
      events = Nil,
      idea = None,
      ideaProposals = Seq.empty,
      operationId = None,
      questionId = Some(QuestionId("my-question")),
      keywords = Nil
    )
  }
}
