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

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import io.circe.syntax._
import org.make.api.idea.{IdeaService, IdeaServiceComponent}
import org.make.api.operation.{OperationService, OperationServiceComponent}
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.api.semantic.SimilarIdea
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.indexed.{IndexedContext, IndexedProposal, IndexedProposalQuestion, ProposalsSearchResult}
import org.make.core.proposal.{ProposalId, ProposalStatus, SearchQuery, _}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference._
import org.make.core.tag.{TagId, TagTypeId}
import org.make.core.user.Role.{RoleAdmin, RoleModerator}
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationError, ValidationFailedError}

import scala.concurrent.Future

class ModerationProposalApiTest
    extends MakeApiTestBase
    with DefaultModerationProposalApiComponent
    with IdeaServiceComponent
    with ProposalServiceComponent
    with UserServiceComponent
    with OperationServiceComponent
    with QuestionServiceComponent
    with ProposalCoordinatorServiceComponent {

  override val proposalService: ProposalService = mock[ProposalService]

  override val userService: UserService = mock[UserService]
  override val operationService: OperationService = mock[OperationService]
  override val ideaService: IdeaService = mock[IdeaService]
  override val questionService: QuestionService = mock[QuestionService]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]

  when(proposalCoordinatorService.getProposal(any[ProposalId])).thenAnswer { id: ProposalId =>
    Future.successful(Some(proposal(id)))
  }

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
    roles = Seq(RoleModerator),
    availableQuestions = Seq(
      QuestionId("question"),
      QuestionId("question-fire-and-ice"),
      QuestionId("some-question"),
      QuestionId("question-mieux-vivre-ensemble"),
      QuestionId("question-vff")
    )
  )

  val arya: User = TestUtils.user(
    id = UserId("the-faceless"),
    email = "arya@kills-the-bad-guys.com",
    firstName = Some("Arya"),
    lastName = Some("Stark")
  )

  val moderatorNoRight: User = TestUtils.user(
    id = UserId("no-right-moderator"),
    roles = Seq(RoleModerator),
    availableQuestions = Seq(QuestionId("some-question-without-answer")),
    firstName = Some("No"),
    lastName = Some("Right")
  )

  when(userService.getUser(eqTo(john.userId))).thenReturn(Future.successful(Some(john)))
  when(userService.getUser(eqTo(tyrion.userId))).thenReturn(Future.successful(Some(tyrion)))

  val validateProposalEntity: String = ValidateProposalRequest(
    newContent = None,
    sendNotificationEmail = true,
    tags = Seq(TagId("dragon"), TagId("sword")),
    questionId = Some(QuestionId("question-fire-and-ice")),
    predictedTags = None,
    predictedTagsModelName = None
  ).asJson.toString

  val validateProposalEntityWithoutTagNorIdea: String = ValidateProposalRequest(
    newContent = None,
    sendNotificationEmail = true,
    tags = Seq.empty,
    questionId = Some(QuestionId("question-fire-and-ice")),
    predictedTags = None,
    predictedTagsModelName = None
  ).asJson.toString

  val refuseProposalWithReasonEntity: String =
    RefuseProposalRequest(sendNotificationEmail = true, refusalReason = Some("not allowed word")).asJson.toString

  val tokenAryaCitizen = "arya-citizen-access-token"
  val tokenTyrionModerator = "tyrion-moderator-access-token"
  val tokenDaenerysAdmin = "daenerys-admin-access-token"
  val tokenJohnCitizen = "john-citizen-access-token"
  val tokenNoRightModerator = "my-moderator-access-token2"

  override def customUserByToken: Map[String, User] =
    Map(
      tokenAryaCitizen -> arya,
      tokenTyrionModerator -> tyrion,
      tokenDaenerysAdmin -> daenerys,
      tokenJohnCitizen -> john,
      tokenNoRightModerator -> moderatorNoRight
    )

  when(
    proposalService
      .validateProposal(
        eqTo(ProposalId("123456")),
        any[UserId],
        any[RequestContext],
        any[Question],
        any[Option[String]],
        any[Boolean],
        any[Seq[TagId]],
        any[Option[Seq[TagId]]],
        any[Option[String]]
      )
  ).thenReturn(Future.successful(Some(proposalResponse(ProposalId("123456")))))
  when(
    proposalService
      .validateProposal(
        eqTo(ProposalId("987654")),
        any[UserId],
        any[RequestContext],
        any[Question],
        any[Option[String]],
        any[Boolean],
        any[Seq[TagId]],
        any[Option[Seq[TagId]]],
        any[Option[String]]
      )
  ).thenReturn(Future.successful(Some(proposalResponse(ProposalId("987654")))))
  when(
    proposalService
      .validateProposal(
        eqTo(ProposalId("nop")),
        any[UserId],
        any[RequestContext],
        any[Question],
        any[Option[String]],
        any[Boolean],
        any[Seq[TagId]],
        any[Option[Seq[TagId]]],
        any[Option[String]]
      )
  ).thenReturn(Future.failed(ValidationFailedError(Seq())))
  when(
    proposalService
      .refuseProposal(eqTo(ProposalId("123456")), any[UserId], any[RequestContext], any[RefuseProposalRequest])
  ).thenReturn(Future.successful(Some(proposalResponse(ProposalId("123456")))))
  when(
    proposalService
      .refuseProposal(eqTo(ProposalId("987654")), any[UserId], any[RequestContext], any[RefuseProposalRequest])
  ).thenReturn(Future.successful(Some(proposalResponse(ProposalId("987654")))))
  when(
    proposalService
      .lockProposal(eqTo(ProposalId("123456")), eqTo(john.userId), eqTo(john.displayName), any[RequestContext])
  ).thenReturn(
    Future.failed(ValidationFailedError(Seq(ValidationError("moderatorName", "already_locked", Some("mauderator")))))
  )
  when(
    proposalService
      .lockProposal(eqTo(ProposalId("123456")), eqTo(tyrion.userId), eqTo(tyrion.fullName), any[RequestContext])
  ).thenReturn(Future.unit)

  val proposalSim123: Proposal = proposal(
    id = ProposalId("sim-123"),
    content = "A song of fire and ice",
    author = UserId("Georges RR Martin"),
    requestContext = RequestContext.empty.copy(country = Some(Country("FR")), language = Some(Language("fr"))),
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now()),
    questionId = QuestionId("question-fire-and-ice")
  )

  val proposalSim124: Proposal = proposal(
    id = ProposalId("sim-124"),
    content = "A song of fire and ice 2",
    author = UserId("Georges RR Martin"),
    requestContext = RequestContext.empty.copy(country = Some(Country("FR")), language = Some(Language("fr"))),
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now())
  )

  when(
    proposalService
      .getModerationProposalById(eqTo(proposalSim123.proposalId))
  ).thenReturn(
    Future.successful(
      Some(
        ModerationProposalResponse(
          id = proposalSim123.proposalId,
          proposalId = proposalSim123.proposalId,
          slug = proposalSim123.slug,
          content = proposalSim123.content,
          author = ModerationProposalAuthorResponse(
            proposalSim123.author,
            firstName = Some("Georges"),
            lastName = Some("Martin"),
            displayName = Some("Georges Martin"),
            postalCode = None,
            age = None,
            avatarUrl = None,
            organisationName = None,
            organisationSlug = None
          ),
          labels = proposalSim123.labels,
          status = Accepted,
          tags = Seq(),
          votes = proposalSim123.votes,
          context = proposalSim123.creationContext,
          createdAt = proposalSim123.createdAt,
          updatedAt = proposalSim123.updatedAt,
          events = Nil,
          idea = None,
          ideaProposals = Seq.empty,
          operationId = None,
          questionId = Some(QuestionId("question-fire-and-ice")),
          keywords = Nil
        )
      )
    )
  )

  when(
    proposalService
      .getModerationProposalById(eqTo(proposalSim124.proposalId))
  ).thenReturn(
    Future.successful(
      Some(
        ModerationProposalResponse(
          id = proposalSim124.proposalId,
          proposalId = proposalSim124.proposalId,
          slug = proposalSim124.slug,
          content = proposalSim124.content,
          author = ModerationProposalAuthorResponse(
            proposalSim124.author,
            firstName = Some("Georges"),
            lastName = Some("Martin"),
            displayName = Some("Georges Martin"),
            organisationName = None,
            organisationSlug = None,
            postalCode = None,
            age = None,
            avatarUrl = None
          ),
          labels = proposalSim124.labels,
          status = Accepted,
          tags = Seq(),
          votes = proposalSim124.votes,
          context = proposalSim124.creationContext,
          createdAt = proposalSim124.createdAt,
          updatedAt = proposalSim124.updatedAt,
          events = Nil,
          idea = None,
          ideaProposals = Seq.empty,
          operationId = None,
          questionId = None,
          keywords = Nil
        )
      )
    )
  )

  when(
    proposalService
      .getModerationProposalById(eqTo(ProposalId("fake")))
  ).thenReturn(Future.successful(None))
  when(
    proposalService
      .getModerationProposalById(eqTo(ProposalId("fake2")))
  ).thenReturn(Future.successful(None))

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

  when(
    proposalService
      .searchForUser(any[Option[UserId]], any[SearchQuery], any[RequestContext])
  ).thenReturn(Future.successful(ProposalsResultSeededResponse(1, Seq(proposalResult), Some(42))))

  private def proposalResponse(id: ProposalId): ModerationProposalResponse = {
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
      questionId = Some(QuestionId("question")),
      keywords = Nil
    )
  }

  when(ideaService.fetchOne(eqTo(IdeaId("fake"))))
    .thenReturn(Future.successful(None))
  when(ideaService.fetchOne(eqTo(IdeaId("Idea 1"))))
    .thenReturn(
      Future.successful(
        Some(
          Idea(
            ideaId = IdeaId("Idea 1"),
            name = "Idea 1",
            createdAt = Some(DateHelper.now()),
            updatedAt = Some(DateHelper.now())
          )
        )
      )
    )
  when(ideaService.fetchOne(eqTo(IdeaId("Idea 2"))))
    .thenReturn(
      Future.successful(
        Some(
          Idea(
            ideaId = IdeaId("Idea 2"),
            name = "Idea 2",
            createdAt = Some(DateHelper.now()),
            updatedAt = Some(DateHelper.now())
          )
        )
      )
    )
  when(ideaService.fetchOne(eqTo(IdeaId("Idea 3"))))
    .thenReturn(
      Future.successful(
        Some(
          Idea(
            ideaId = IdeaId("Idea 3"),
            name = "Idea1",
            createdAt = Some(DateHelper.now()),
            updatedAt = Some(DateHelper.now())
          )
        )
      )
    )

  val indexedProposal: IndexedProposal = indexedProposal(id = idGenerator.nextProposalId()).copy(question = Some(
    IndexedProposalQuestion(
      questionId = QuestionId("question-fire-and-ice"),
      slug = "question-fire-and-ice",
      title = "title",
      question = "question ?",
      countries = NonEmptyList.of(Country("FR")),
      language = Language("fr"),
      startDate = ZonedDateTime.parse("1968-07-03T00:00:00.000Z"),
      endDate = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
      isOpen = true
    )
  )
  )

  when(proposalService.getProposalById(eqTo(ProposalId("123456")), any[RequestContext]))
    .thenReturn(Future.successful(Some(indexedProposal)))

  when(proposalService.getSimilar(any[UserId], any[IndexedProposal], any[RequestContext]))
    .thenReturn(
      Future.successful(
        Seq(
          SimilarIdea(IdeaId("Idea 1"), "Idea One", ProposalId("Proposal 1"), "Proposal One", 1.23456),
          SimilarIdea(IdeaId("Idea 2"), "Idea Two", ProposalId("Proposal 2"), "Proposal Two", 0.123456)
        )
      )
    )

  when(
    proposalService
      .changeProposalsIdea(
        eqTo(Seq(proposalSim123.proposalId, proposalSim124.proposalId)),
        any[UserId],
        eqTo(IdeaId("Idea 3"))
      )
  ).thenReturn(Future.successful(Seq(proposalSim123, proposalSim124)))

  val routes: Route = sealRoute(moderationProposalApi.routes)

  Feature("proposal validation") {
    Scenario("unauthenticated validation") {
      Post("/moderation/proposals/123456/accept") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("validation with user role") {
      Post("/moderation/proposals/123456/accept")
        .withHeaders(Authorization(OAuth2BearerToken(tokenJohnCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("validation with moderation role without right on question") {

      when(questionService.getQuestion(QuestionId("question-fire-and-ice"))).thenReturn(
        Future.successful(
          Some(
            Question(
              questionId = QuestionId("question-fire-and-ice"),
              slug = "question-fire-and-ice",
              countries = NonEmptyList.of(Country("FR")),
              language = Language("fr"),
              question = "",
              shortTitle = None,
              operationId = None
            )
          )
        )
      )

      Post("/moderation/proposals/123456/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntity))
        .withHeaders(Authorization(OAuth2BearerToken(tokenNoRightModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("validation with moderation role") {
      Post("/moderation/proposals/123456/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntity))
        .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("validation with admin role") {

      Post("/moderation/proposals/987654/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntity))
        .withHeaders(Authorization(OAuth2BearerToken(tokenDaenerysAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("validation of non existing with admin role") {

      Post("/moderation/proposals/nop/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntity))
        .withHeaders(Authorization(OAuth2BearerToken(tokenDaenerysAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("validation of proposal without Tag nor Idea") {

      Post("/moderation/proposals/987654/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntityWithoutTagNorIdea))
        .withHeaders(Authorization(OAuth2BearerToken(tokenDaenerysAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  Feature("proposal refuse") {
    Scenario("unauthenticated refuse") {
      Post("/moderation/proposals/123456/refuse") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("refuse with user role") {
      Post("/moderation/proposals/123456/refuse")
        .withHeaders(Authorization(OAuth2BearerToken(tokenJohnCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("refusing with moderation role") {
      Post("/moderation/proposals/123456/refuse")
        .withEntity(HttpEntity(ContentTypes.`application/json`, refuseProposalWithReasonEntity))
        .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("refusing with admin role") {
      Post("/moderation/proposals/987654/refuse")
        .withEntity(HttpEntity(ContentTypes.`application/json`, refuseProposalWithReasonEntity))
        .withHeaders(Authorization(OAuth2BearerToken(tokenDaenerysAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    // Todo: implement this test
    Scenario("refusing proposal without reason with admin role: this test should be done") {}
  }

  // Todo: implement this test suite. Test the behaviour of the service.
  Feature("get proposal for moderation") {
    Scenario("moderator get proposal by id") {
      Get("/moderation/proposals/sim-123")
        .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        entityAs[ModerationProposalResponse]
      }
    }

    Scenario("moderator get proposal by id without right") {
      Get("/moderation/proposals/sim-123")
        .withHeaders(Authorization(OAuth2BearerToken(tokenNoRightModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("moderator get proposal as admin") {
      Get("/moderation/proposals/sim-123")
        .withHeaders(Authorization(OAuth2BearerToken(tokenDaenerysAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("get new proposal without history") {}
    Scenario("get validated proposal gives history with moderator") {}
  }

  Feature("lock proposal") {
    Scenario("moderator can lock an unlocked proposal") {
      Post("/moderation/proposals/123456/lock")
        .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }

    Scenario("moderator can expand the time a proposal is locked by itself") {
      Post("/moderation/proposals/123456/lock")
        .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
      Post("/moderation/proposals/123456/lock")
        .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }

    Scenario("user cannot lock a locked proposal") {
      Post("/moderation/proposals/123456/lock")
        .withHeaders(Authorization(OAuth2BearerToken(tokenJohnCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
  }

  Feature("lock multiple proposals") {
    val ids = Seq(ProposalId("p-1"), ProposalId("p-2"), ProposalId("p-3"))
    val fake = ProposalId("fake")
    val unavailableProposalId = ProposalId("other-unavailable-question")
    def entity(ids: Seq[ProposalId]) = LockProposalsRequest(ids.toSet).asJson.toString
    val indexedProposals = ids.map(id => indexedProposal(id, questionId = QuestionId("question-fire-and-ice")))

    def query(ids: Seq[ProposalId]) =
      SearchQuery(
        filters = Some(
          SearchFilters(
            proposal = Some(ProposalSearchFilter(ids)),
            status = Some(StatusSearchFilter(ProposalStatus.values))
          )
        ),
        limit = Some(ids.size + 1)
      )
    when(
      proposalService
        .search(userId = any[Option[UserId]], query = eqTo(query(ids)), requestContext = any[RequestContext])
    ).thenReturn(Future.successful(ProposalsSearchResult(ids.size, indexedProposals)))
    when(
      proposalService
        .search(userId = any[Option[UserId]], query = eqTo(query(ids :+ fake)), requestContext = any[RequestContext])
    ).thenReturn(Future.successful(ProposalsSearchResult(ids.size, indexedProposals)))
    when(
      proposalService.search(
        userId = any[Option[UserId]],
        query = eqTo(query(ids :+ unavailableProposalId)),
        requestContext = any[RequestContext]
      )
    ).thenReturn(
      Future.successful(
        ProposalsSearchResult(
          ids.size,
          indexedProposals :+ indexedProposal(
            unavailableProposalId,
            questionId = QuestionId("other-unavailable-question-id")
          )
        )
      )
    )
    when(proposalService.getProposalsById(eqTo(ids :+ unavailableProposalId), any[RequestContext]))
      .thenReturn(
        Future.successful(
          ids.map(id => indexedProposal(id, questionId = QuestionId("question-fire-and-ice"))) :+ indexedProposal(
            unavailableProposalId,
            questionId = QuestionId("other-unavailable-question-id")
          )
        )
      )
    when(proposalService.lockProposals(eqTo(ids), eqTo(tyrion.userId), eqTo(tyrion.fullName), any[RequestContext]))
      .thenReturn(Future.successful(Some(tyrion.userId)))

    Scenario("moderator can lock unlocked proposals if the question is available to them") {
      Post("/moderation/proposals/lock")
        .withEntity(HttpEntity(ContentTypes.`application/json`, entity(ids)))
        .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }

    Scenario("moderator cannot lock unlocked proposals if the question is not available to them") {
      Post("/moderation/proposals/lock")
        .withEntity(HttpEntity(ContentTypes.`application/json`, entity(ids :+ unavailableProposalId)))
        .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("moderator cannot lock non existent proposals") {
      Post("/moderation/proposals/lock")
        .withEntity(HttpEntity(ContentTypes.`application/json`, entity(ids :+ fake)))
        .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("user cannot lock an unlocked proposal") {
      Post("/moderation/proposals/lock")
        .withEntity(HttpEntity(ContentTypes.`application/json`, entity(ids)))
        .withHeaders(Authorization(OAuth2BearerToken(tokenJohnCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
  }

  Feature("get duplicates") {
    Scenario("moderator without right") {
      Get("/moderation/proposals/123456/duplicates")
        .withHeaders(Authorization(OAuth2BearerToken(tokenNoRightModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    Scenario("moderator can fetch duplicate ideas") {
      Get("/moderation/proposals/123456/duplicates")
        .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val results: Seq[DuplicateResponse] = entityAs[Seq[DuplicateResponse]]
        results.length should be(2)
        results.head.ideaId should be(IdeaId("Idea 1"))
        results(1).ideaName should be("Idea Two")
        results.head.score should be(1.23456)
        results(1).proposalId should be(ProposalId("Proposal 2"))
        results.head.proposalContent should be("Proposal One")
      }
    }
  }

  Feature("change idea of a proposal list") {
    Scenario("unauthenticated user") {
      Given("an un authenticated user")
      When("the user change idea of proposals")
      Then("he should get an unauthorized (401) return code")
      Post("/moderation/proposals/change-idea").withEntity(
        HttpEntity(ContentTypes.`application/json`, """{"proposalIds": ["sim-123", "sim-124"], "ideaId":"Idea 3" }""")
      ) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("authenticated user with citizen role") {
      Given("an authenticated user with citizen role")
      When("the user change idea of proposals")
      Then("he should get an forbidden (403) return code")

      Post("/moderation/proposals/change-idea")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"proposalIds": ["sim-123", "sim-124"], "ideaId":"Idea 3" }""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenJohnCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("change success") {
      Given("an authenticated user with admin role")
      When("the user change idea of proposals")
      Then("he should get an success (201) return code")

      Post("/moderation/proposals/change-idea")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"proposalIds": ["sim-123", "sim-124"], "ideaId":"Idea 3" }""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenDaenerysAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }

    Scenario("invalid idea id") {
      Given("an authenticated user with admin role")
      And("an invalid ideaId")
      When("the user change idea of proposals using invalid ideaId")
      Then("he should get a bad request (400) return code")

      Post("/moderation/proposals/change-idea")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"proposalIds": ["sim-123", "sim-124"], "ideaId":"fake" }""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenDaenerysAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "ideaId")
        contentError should be(Some(ValidationError("ideaId", "mandatory", Some("Invalid idea id"))))
      }
    }

    Scenario("invalid proposal id") {
      Given("an authenticated user with admin role")
      And("an invalid ideaId")
      When("the user change idea of proposals using invalid ideaId")
      Then("he should get a bad request (400) return code")

      Post("/moderation/proposals/change-idea")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{"proposalIds": ["sim-123", "sim-124", "fake", "fake2"], "ideaId":"Idea 3" }"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenDaenerysAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "proposalIds")
        contentError should be(
          Some(ValidationError("proposalIds", "invalid_value", Some("Some proposal ids are invalid: fake, fake2")))
        )
      }
    }

  }

  Feature("next author/proposal to moderate") {

    case class TestCase[T](
      entity: String,
      endpoint: String,
      method: (
        QuestionId,
        UserId,
        Option[String],
        RequestContext,
        Boolean,
        Option[Int],
        Option[Double]
      )                                     => Future[Option[T]],
      transform: ModerationProposalResponse => T
    )

    val cases = Seq(
      TestCase("proposal", "next", proposalService.searchAndLockProposalToModerate, identity),
      TestCase(
        "author",
        "next-author-to-moderate",
        proposalService.searchAndLockAuthorToModerate,
        proposal => ModerationAuthorResponse(proposal.author, Seq(proposal), 1)
      )
    )

    val validPayload =
      """
        |{
        |  "questionId": "question-vff",
        |  "toEnrich": false
        |}
      """.stripMargin

    when(questionService.getQuestion(QuestionId("question-vff"))).thenReturn(
      Future.successful(
        Some(
          Question(
            questionId = QuestionId("question-vff"),
            slug = "question-vff",
            countries = NonEmptyList.of(Country("FR")),
            language = Language("fr"),
            question = "",
            shortTitle = None,
            operationId = Some(OperationId("vff"))
          )
        )
      )
    )

    when(questionService.getQuestion(QuestionId("question-mieux-vivre-ensemble")))
      .thenReturn(
        Future.successful(
          Some(
            Question(
              questionId = QuestionId("question-mieux-vivre-ensemble"),
              slug = "question-mieux-vivre-ensemble",
              countries = NonEmptyList.of(Country("FR")),
              language = Language("fr"),
              question = "",
              shortTitle = None,
              operationId = Some(OperationId("mieux-vivre-ensemble"))
            )
          )
        )
      )

    for (TestCase(entity, endpoint, _, _) <- cases) {
      Scenario(s"unauthenticated call to next $entity") {
        Given("an unauthenticated user")
        When(s"the user requests the next $entity to moderate")
        Then("The return code should be 401")

        Post(s"/moderation/proposals/$endpoint")
          .withEntity(HttpEntity(ContentTypes.`application/json`, validPayload)) ~> routes ~> check {
          status should be(StatusCodes.Unauthorized)
        }
      }
    }

    for (TestCase(entity, endpoint, _, _) <- cases) {
      Scenario(s"calling next $entity with a user right") {
        Given("an authenticated user with the user role")
        When(s"the user requests the next $entity to moderate")
        Then("The return code should be 403")

        Post(s"/moderation/proposals/$endpoint")
          .withEntity(HttpEntity(ContentTypes.`application/json`, validPayload))
          .withHeaders(Authorization(OAuth2BearerToken(tokenAryaCitizen))) ~> routes ~> check {
          status should be(StatusCodes.Forbidden)
        }
      }
    }

    for (TestCase(entity, endpoint, _, _) <- cases) {
      Scenario(s"calling next $entity without right on question") {
        Given("an authenticated user with the moderator role, without rights")
        When(s"the user requests the next $entity to moderate")
        Then("The return code should be 403")

        Post(s"/moderation/proposals/$endpoint")
          .withEntity(HttpEntity(ContentTypes.`application/json`, validPayload))
          .withHeaders(Authorization(OAuth2BearerToken(tokenNoRightModerator))) ~> routes ~> check {
          status should be(StatusCodes.Forbidden)
        }
      }
    }

    for (TestCase(entity, endpoint, method, _) <- cases) {
      Scenario(s"no proposal to moderate for next $entity") {
        Given("an authenticated user with the moderator role")
        When(s"the user requests the next $entity to moderate")
        And("there is no proposal matching criterias")
        Then("The return code should be 404")

        when(
          method(
            eqTo(QuestionId("question-vff")),
            eqTo(tyrion.userId),
            eqTo(tyrion.fullName),
            any[RequestContext],
            any[Boolean],
            any[Option[Int]],
            any[Option[Double]]
          )
        ).thenReturn(Future.successful(None))

        Post(s"/moderation/proposals/$endpoint")
          .withEntity(HttpEntity(ContentTypes.`application/json`, validPayload))
          .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }

    for (TestCase(entity, endpoint, method, transform) <- cases) {
      Scenario(s"normal next $entity case") {
        Given("an authenticated user with the moderator role")
        When(s"the user requests the next $entity to moderate")
        And("there is a proposal matching criterias")
        Then("The return code should be 200")

        val payload =
          """
          |{
          |  "questionId": "question-mieux-vivre-ensemble",
          |  "toEnrich": false
          |}
        """.stripMargin

        when(
          method(
            eqTo(QuestionId("question-mieux-vivre-ensemble")),
            eqTo(tyrion.userId),
            eqTo(tyrion.fullName),
            any[RequestContext],
            any[Boolean],
            any[Option[Int]],
            any[Option[Double]]
          )
        ).thenReturn(Future.successful(Some(transform(proposalResponse(ProposalId("123"))))))

        Post(s"/moderation/proposals/$endpoint")
          .withEntity(HttpEntity(ContentTypes.`application/json`, payload))
          .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
          status should be(StatusCodes.OK)
        }
      }
    }

    for (TestCase(entity, endpoint, method, transform) <- cases) {
      Scenario(s"invalid payload for next $entity") {
        Given("an authenticated user with the moderator role")
        When(s"the user requests the next $entity to moderate")
        And("there is a proposal matching criterias")
        Then("The return code should be 200")

        val payload =
          """
          |{
          |  "toEnrich": false
          |}
        """.stripMargin

        when(
          method(
            eqTo(QuestionId("question-mieux-vivre-ensemble")),
            eqTo(tyrion.userId),
            eqTo(tyrion.fullName),
            any[RequestContext],
            any[Boolean],
            any[Option[Int]],
            any[Option[Double]]
          )
        ).thenReturn(Future.successful(Some(transform(proposalResponse(ProposalId("123456789"))))))

        Post(s"/moderation/proposals/$endpoint")
          .withEntity(HttpEntity(ContentTypes.`application/json`, payload))
          .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
        }
      }
    }
  }

  Feature("get proposals") {
    Scenario("get proposals by created at date") {
      Given("an authenticated user with the moderator role")
      When("the user search proposals using created before")
      And("there is a proposal matching criterias")
      Then("The return code should be 200")

      val beforeDateString: String = "2017-06-04T01:01:01.123Z"
      val beforeDate: ZonedDateTime = ZonedDateTime.from(dateFormatter.parse(beforeDateString))

      when(
        proposalService.search(
          any[Option[UserId]],
          eqTo(
            SearchQuery(filters = Some(
              SearchFilters(
                createdAt = Some(CreatedAtSearchFilter(before = Some(beforeDate), after = None)),
                question = Some(QuestionSearchFilter(tyrion.availableQuestions))
              )
            )
            )
          ),
          any[RequestContext]
        )
      ).thenReturn(Future.successful(ProposalsSearchResult(total = 42, results = Seq.empty)))

      Get(s"/moderation/proposals?createdBefore=$beforeDateString")
        .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val results: ProposalsSearchResult = entityAs[ProposalsSearchResult]
        results.total should be(42)
      }

    }
  }

  Feature("get predicted tags for proposal") {
    Scenario("unauthorized user") {
      Get("/moderation/proposals/123456/tags") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
    Scenario("forbidden citizen") {
      Get("/moderation/proposals/123456/tags")
        .withHeaders(Authorization(OAuth2BearerToken(tokenJohnCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    Scenario("allowed moderator") {
      when(proposalService.getTagsForProposal(any[Proposal]))
        .thenReturn(
          Future.successful(
            TagsForProposalResponse(
              tags = Seq(
                TagForProposalResponse(
                  id = TagId("tag-id"),
                  label = "label",
                  tagTypeId = TagTypeId("tag-type-id"),
                  weight = 1.0f,
                  questionId = Some(QuestionId("question-id")),
                  checked = true,
                  predicted = true
                )
              ),
              modelName = "auto"
            )
          )
        )
      Get("/moderation/proposals/123456/tags")
        .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val results: TagsForProposalResponse = entityAs[TagsForProposalResponse]
        results.tags.length should be(1)
        results.tags.head.id should be(TagId("tag-id"))

      }
    }
    Scenario("not allowed moderator") {
      when(proposalService.getTagsForProposal(any[Proposal]))
        .thenReturn(
          Future.successful(
            TagsForProposalResponse(
              tags = Seq(
                TagForProposalResponse(
                  id = TagId("tag-id"),
                  label = "label",
                  tagTypeId = TagTypeId("tag-type-id"),
                  weight = 1.0f,
                  questionId = Some(QuestionId("question-id")),
                  checked = true,
                  predicted = true
                )
              ),
              modelName = "auto"
            )
          )
        )
      Get("/moderation/proposals/123456/tags")
        .withHeaders(Authorization(OAuth2BearerToken(tokenNoRightModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    Scenario("proposal not found") {
      when(proposalCoordinatorService.getProposal(any[ProposalId]))
        .thenReturn(Future.successful(None))

      Get("/moderation/proposals/invalid/tags")
        .withHeaders(Authorization(OAuth2BearerToken(tokenTyrionModerator))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }

}
