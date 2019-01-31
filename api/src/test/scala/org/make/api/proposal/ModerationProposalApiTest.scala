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
import org.make.api.semantic.SimilarIdea
import org.make.api.theme.{ThemeService, ThemeServiceComponent}
import org.make.api.user.{UserResponse, UserService, UserServiceComponent}
import org.make.core.auth.UserRights
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.indexed._
import org.make.core.proposal.{ProposalId, ProposalStatus, SearchQuery, _}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference._
import org.make.core.tag.{TagId, TagTypeId}
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.{Role, User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationError, ValidationFailedError}
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito._
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class ModerationProposalApiTest
    extends MakeApiTestBase
    with DefaultModerationProposalApiComponent
    with IdeaServiceComponent
    with ProposalServiceComponent
    with UserServiceComponent
    with ThemeServiceComponent
    with OperationServiceComponent
    with QuestionServiceComponent
    with ProposalCoordinatorServiceComponent {

  override val proposalService: ProposalService = mock[ProposalService]

  override val userService: UserService = mock[UserService]
  override val themeService: ThemeService = mock[ThemeService]
  override val operationService: OperationService = mock[OperationService]
  override val ideaService: IdeaService = mock[IdeaService]
  override val questionService: QuestionService = mock[QuestionService]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]

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
            country = invocation.getArgument[Country](2),
            language = invocation.getArgument[Language](3),
            question = "my question",
            operationId = invocation.getArgument[Option[OperationId]](1),
            themeId = invocation.getArgument[Option[ThemeId]](0)
          )
        )
    )
  )

  when(proposalCoordinatorService.getProposal(any[ProposalId]))
    .thenAnswer(invocation => Future.successful(Some(simpleProposal(invocation.getArgument[ProposalId](0)))))

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
    availableQuestions = Seq(
      QuestionId("my-question"),
      QuestionId("question-fire-and-ice"),
      QuestionId("some-question"),
      QuestionId("question-mieux-vivre-ensemble"),
      QuestionId("question-vff")
    )
  )

  val arya = User(
    userId = UserId("the-faceless"),
    email = "arya@kills-the-bad-guys.com",
    firstName = Some("Arya"),
    lastName = Some("Stark"),
    lastIp = None,
    hashedPassword = None,
    enabled = true,
    emailVerified = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleCitizen),
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
  val userToken = "my-user-access-token"
  val moderatorToken = "my-moderator-access-token"
  val moderator2Token = "my-moderator-access-token2"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)
  private val userAccessToken = AccessToken(userToken, None, None, Some(1234567890L), tokenCreationDate)
  private val adminAccessToken = AccessToken(adminToken, None, None, Some(1234567890L), tokenCreationDate)
  private val moderatorAccessToken =
    AccessToken(moderatorToken, None, None, Some(1234567890L), tokenCreationDate)
  private val moderator2AccessToken =
    AccessToken(moderator2Token, None, None, Some(1234567890L), tokenCreationDate)

  val validateProposalEntity: String = ValidateProposalRequest(
    newContent = None,
    sendNotificationEmail = true,
    labels = Seq(LabelId("sex"), LabelId("violence")),
    tags = Seq(TagId("dragon"), TagId("sword")),
    idea = Some(IdeaId("becoming-king")),
    questionId = Some(QuestionId("question-fire-and-ice")),
    predictedTags = None,
    predictedTagsModelName = None
  ).asJson.toString

  val validateProposalEntityWithoutTagNorIdea: String = ValidateProposalRequest(
    newContent = None,
    sendNotificationEmail = true,
    labels = Seq(LabelId("sex"), LabelId("violence")),
    tags = Seq.empty,
    idea = None,
    questionId = Some(QuestionId("question-fire-and-ice")),
    predictedTags = None,
    predictedTagsModelName = None
  ).asJson.toString

  val refuseProposalWithReasonEntity: String =
    RefuseProposalRequest(sendNotificationEmail = true, refusalReason = Some("not allowed word")).asJson.toString

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))
  when(oauth2DataHandler.findAccessToken(adminToken)).thenReturn(Future.successful(Some(adminAccessToken)))
  when(oauth2DataHandler.findAccessToken(moderatorToken)).thenReturn(Future.successful(Some(moderatorAccessToken)))
  when(oauth2DataHandler.findAccessToken(moderator2Token)).thenReturn(Future.successful(Some(moderator2AccessToken)))
  when(oauth2DataHandler.findAccessToken(userToken)).thenReturn(Future.successful(Some(userAccessToken)))
  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(accessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(john.userId, john.roles, availableQuestions = john.availableQuestions),
            None,
            Some("user"),
            None
          )
        )
      )
    )
  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(
              userId = daenerys.userId,
              roles = daenerys.roles,
              availableQuestions = daenerys.availableQuestions
            ),
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

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderator2AccessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(
              UserId("no-right-moderator"),
              Seq(RoleModerator),
              Seq(QuestionId("some-question-without-answer"))
            ),
            None,
            None,
            None
          )
        )
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(userAccessToken)))
    .thenReturn(
      Future.successful(Some(AuthInfo(UserRights(arya.userId, arya.roles, arya.availableQuestions), None, None, None)))
    )

  when(
    proposalService
      .validateProposal(
        matches(ProposalId("123456")),
        any[UserId],
        any[RequestContext],
        any[Question],
        any[Option[String]],
        any[Boolean],
        any[Option[IdeaId]],
        any[Seq[TagId]],
        any[Option[Seq[TagId]]],
        any[Option[String]]
      )
  ).thenReturn(Future.successful(Some(proposal(ProposalId("123456")))))
  when(
    proposalService
      .validateProposal(
        matches(ProposalId("987654")),
        any[UserId],
        any[RequestContext],
        any[Question],
        any[Option[String]],
        any[Boolean],
        any[Option[IdeaId]],
        any[Seq[TagId]],
        any[Option[Seq[TagId]]],
        any[Option[String]]
      )
  ).thenReturn(Future.successful(Some(proposal(ProposalId("987654")))))
  when(
    proposalService
      .validateProposal(
        matches(ProposalId("nop")),
        any[UserId],
        any[RequestContext],
        any[Question],
        any[Option[String]],
        any[Boolean],
        any[Option[IdeaId]],
        any[Seq[TagId]],
        any[Option[Seq[TagId]]],
        any[Option[String]]
      )
  ).thenReturn(Future.failed(ValidationFailedError(Seq())))
  when(
    proposalService
      .refuseProposal(matches(ProposalId("123456")), any[UserId], any[RequestContext], any[RefuseProposalRequest])
  ).thenReturn(Future.successful(Some(proposal(ProposalId("123456")))))
  when(
    proposalService
      .refuseProposal(matches(ProposalId("987654")), any[UserId], any[RequestContext], any[RefuseProposalRequest])
  ).thenReturn(Future.successful(Some(proposal(ProposalId("987654")))))
  when(
    proposalService
      .lockProposal(matches(ProposalId("123456")), any[UserId], any[RequestContext])
  ).thenReturn(Future.failed(ValidationFailedError(Seq(ValidationError("moderatorName", Some("mauderator"))))))
  when(
    proposalService
      .lockProposal(matches(ProposalId("123456")), matches(tyrion.userId), any[RequestContext])
  ).thenReturn(Future.successful(Some(tyrion.userId)))

  val proposalSim123: Proposal = Proposal(
    proposalId = ProposalId("sim-123"),
    slug = "a-song-of-fire-and-ice",
    content = "A song of fire and ice",
    author = UserId("Georges RR Martin"),
    labels = Seq.empty,
    votes = Seq(
      Vote(key = VoteKey.Agree, qualifications = Seq.empty),
      Vote(key = VoteKey.Disagree, qualifications = Seq.empty),
      Vote(key = VoteKey.Neutral, qualifications = Seq.empty)
    ),
    creationContext = RequestContext.empty,
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now()),
    events = Nil,
    language = Some(Language("fr")),
    country = Some(Country("FR")),
    questionId = Some(QuestionId("question-fire-and-ice"))
  )

  val proposalSim124: Proposal = Proposal(
    proposalId = ProposalId("sim-124"),
    slug = "a-song-of-fire-and-ice-2",
    content = "A song of fire and ice 2",
    author = UserId("Georges RR Martin"),
    labels = Seq.empty,
    votes = Seq(
      Vote(key = VoteKey.Agree, qualifications = Seq.empty),
      Vote(key = VoteKey.Disagree, qualifications = Seq.empty),
      Vote(key = VoteKey.Neutral, qualifications = Seq.empty)
    ),
    creationContext = RequestContext.empty,
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now()),
    events = Nil,
    language = Some(Language("fr")),
    country = Some(Country("FR"))
  )

  when(
    proposalService
      .getModerationProposalById(matches(proposalSim123.proposalId))
  ).thenReturn(
    Future.successful(
      Some(
        ModerationProposalResponse(
          proposalId = proposalSim123.proposalId,
          slug = proposalSim123.slug,
          content = proposalSim123.content,
          author = UserResponse(
            proposalSim123.author,
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
          labels = proposalSim123.labels,
          theme = None,
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
          language = Some(Language("fr")),
          country = Some(Country("FR")),
          questionId = Some(QuestionId("question-fire-and-ice"))
        )
      )
    )
  )

  when(
    proposalService
      .getModerationProposalById(matches(proposalSim124.proposalId))
  ).thenReturn(
    Future.successful(
      Some(
        ModerationProposalResponse(
          proposalId = proposalSim124.proposalId,
          slug = proposalSim124.slug,
          content = proposalSim124.content,
          author = UserResponse(
            proposalSim124.author,
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
          labels = proposalSim124.labels,
          theme = None,
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
          language = Some(Language("fr")),
          country = Some(Country("FR")),
          questionId = None
        )
      )
    )
  )

  when(
    proposalService
      .getModerationProposalById(matches(ProposalId("fake")))
  ).thenReturn(Future.successful(None))
  when(
    proposalService
      .getModerationProposalById(matches(ProposalId("fake2")))
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
    questionId = None,
    proposalKey = "pr0p0541k3y"
  )

  when(
    proposalService
      .searchForUser(any[Option[UserId]], any[SearchQuery], any[RequestContext])
  ).thenReturn(Future.successful(ProposalsResultSeededResponse(1, Seq(proposalResult), Some(42))))

  private def proposal(id: ProposalId): ModerationProposalResponse = {
    ModerationProposalResponse(
      proposalId = id,
      slug = "a-song-of-fire-and-ice",
      content = "A song of fire and ice",
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
  }

  private def simpleProposal(id: ProposalId): Proposal = {
    Proposal(
      proposalId = id,
      slug = "a-song-of-fire-and-ice",
      content = "A song of fire and ice",
      author = UserId("Georges RR Martin"),
      labels = Seq(),
      theme = None,
      status = Accepted,
      tags = Seq(),
      votes = Seq(
        Vote(key = VoteKey.Agree, qualifications = Seq.empty),
        Vote(key = VoteKey.Disagree, qualifications = Seq.empty),
        Vote(key = VoteKey.Neutral, qualifications = Seq.empty)
      ),
      refusalReason = None,
      organisations = Seq.empty,
      creationContext = RequestContext.empty,
      createdAt = Some(DateHelper.now()),
      updatedAt = Some(DateHelper.now()),
      events = Nil,
      idea = None,
      operation = None,
      questionId = Some(QuestionId("some-question")),
      language = Some(Language("fr")),
      country = Some(Country("FR"))
    )
  }

  when(ideaService.fetchOne(matches(IdeaId("fake"))))
    .thenReturn(Future.successful(None))
  when(ideaService.fetchOne(matches(IdeaId("Idea 1"))))
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
  when(ideaService.fetchOne(matches(IdeaId("Idea 2"))))
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
  when(ideaService.fetchOne(matches(IdeaId("Idea 3"))))
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

  val indexedProposal: IndexedProposal = mock[IndexedProposal]
  when(indexedProposal.questionId).thenReturn(Some(QuestionId("question-fire-and-ice")))

  when(proposalService.getProposalById(matches(ProposalId("123456")), any[RequestContext]))
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
        matches(Seq(proposalSim123.proposalId, proposalSim124.proposalId)),
        any[UserId],
        matches(IdeaId("Idea 3"))
      )
  ).thenReturn(Future.successful(Seq(proposalSim123, proposalSim124)))

  val routes: Route = sealRoute(moderationProposalApi.routes)

  feature("proposal validation") {
    scenario("unauthenticated validation") {
      Post("/moderation/proposals/123456/accept") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("validation with user role") {
      Post("/moderation/proposals/123456/accept")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("validation with moderation role without right on question") {

      when(questionService.getQuestion(QuestionId("question-fire-and-ice"))).thenReturn(
        Future.successful(
          Some(
            Question(
              questionId = QuestionId("question-fire-and-ice"),
              slug = "question-fire-and-ice",
              country = Country("FR"),
              language = Language("fr"),
              question = "",
              operationId = None,
              themeId = Some(ThemeId("fire and ice"))
            )
          )
        )
      )

      Post("/moderation/proposals/123456/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntity))
        .withHeaders(Authorization(OAuth2BearerToken(moderator2Token))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("validation with moderation role") {

      when(
        questionService.findQuestion(matches(Some(ThemeId("fire and ice"))), matches(None), any[Country], any[Language])
      ).thenReturn(
        Future.successful(
          Some(
            Question(
              questionId = QuestionId("question-fire-and-ice"),
              slug = "question-fire-and-ice",
              country = Country("FR"),
              language = Language("fr"),
              question = "",
              operationId = None,
              themeId = Some(ThemeId("fire and ice"))
            )
          )
        )
      )

      Post("/moderation/proposals/123456/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntity))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("validation with admin role") {
      when(
        questionService.findQuestionByQuestionIdOrThemeOrOperation(
          matches(None),
          matches(Some(ThemeId("fire and ice"))),
          matches(None),
          matches(Country("FR")),
          matches(Language("fr"))
        )
      ).thenReturn(
        Future.successful(
          Some(
            Question(
              questionId = QuestionId("question-fire-and-ice"),
              slug = "question-fire-and-ice",
              country = Country("FR"),
              language = Language("fr"),
              question = "",
              operationId = None,
              themeId = Some(ThemeId("fire and ice"))
            )
          )
        )
      )

      Post("/moderation/proposals/987654/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntity))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("validation of non existing with admin role") {

      when(
        questionService.findQuestionByQuestionIdOrThemeOrOperation(
          matches(None),
          matches(Some(ThemeId("fire and ice"))),
          matches(None),
          matches(Country("FR")),
          matches(Language("fr"))
        )
      ).thenReturn(
        Future.successful(
          Some(
            Question(
              questionId = QuestionId("question-fire-and-ice"),
              slug = "question-fire-and-ice",
              country = Country("FR"),
              language = Language("fr"),
              question = "",
              operationId = None,
              themeId = Some(ThemeId("fire and ice"))
            )
          )
        )
      )

      Post("/moderation/proposals/nop/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntity))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("validation of proposal without Tag nor Idea") {
      when(
        questionService.findQuestionByQuestionIdOrThemeOrOperation(
          matches(Some(QuestionId("question-fire-and-ice"))),
          matches(None),
          matches(None),
          matches(Country("FR")),
          matches(Language("fr"))
        )
      ).thenReturn(
        Future.successful(
          Some(
            Question(
              questionId = QuestionId("question-fire-and-ice"),
              slug = "question-fire-and-ice",
              country = Country("FR"),
              language = Language("fr"),
              question = "",
              operationId = None,
              themeId = Some(ThemeId("fire and ice"))
            )
          )
        )
      )

      Post("/moderation/proposals/987654/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntityWithoutTagNorIdea))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  feature("proposal refuse") {
    scenario("unauthenticated refuse") {
      Post("/moderation/proposals/123456/refuse") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("refuse with user role") {
      Post("/moderation/proposals/123456/refuse")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("refusing with moderation role") {
      Post("/moderation/proposals/123456/refuse")
        .withEntity(HttpEntity(ContentTypes.`application/json`, refuseProposalWithReasonEntity))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("refusing with admin role") {
      Post("/moderation/proposals/987654/refuse")
        .withEntity(HttpEntity(ContentTypes.`application/json`, refuseProposalWithReasonEntity))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    // Todo: implement this test
    scenario("refusing proposal without reason with admin role: this test should be done") {}
  }

  // Todo: implement this test suite. Test the behaviour of the service.
  feature("get proposal for moderation") {
    scenario("moderator get proposal by id") {
      Get("/moderation/proposals/sim-123")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        entityAs[ModerationProposalResponse]
      }
    }

    scenario("moderator get proposal by id without right") {
      Get("/moderation/proposals/sim-123")
        .withHeaders(Authorization(OAuth2BearerToken(moderator2Token))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("moderator get proposal as admin") {
      Get("/moderation/proposals/sim-123")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("get new proposal without history") {}
    scenario("get validated proposal gives history with moderator") {}
  }

  feature("lock proposal") {
    scenario("moderator can lock an unlocked proposal") {
      Post("/moderation/proposals/123456/lock")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }

    scenario("moderator can expand the time a proposal is locked by itself") {
      Post("/moderation/proposals/123456/lock")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
      Post("/moderation/proposals/123456/lock")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }

    scenario("user cannot lock an unlocked proposal") {
      Post("/moderation/proposals/123456/lock")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
  }

  feature("get duplicates") {
    scenario("moderator without right") {
      Get("/moderation/proposals/123456/duplicates")
        .withHeaders(Authorization(OAuth2BearerToken(moderator2Token))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    scenario("moderator can fetch duplicate ideas") {
      Get("/moderation/proposals/123456/duplicates")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
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

  feature("change idea of a proposal list") {
    scenario("unauthenticated user") {
      Given("an un authenticated user")
      When("the user change idea of proposals")
      Then("he should get an unauthorized (401) return code")
      Post("/moderation/proposals/change-idea").withEntity(
        HttpEntity(ContentTypes.`application/json`, """{"proposalIds": ["sim-123", "sim-124"], "ideaId":"Idea 3" }""")
      ) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated user with citizen role") {
      Given("an authenticated user with citizen role")
      When("the user change idea of proposals")
      Then("he should get an forbidden (403) return code")

      Post("/moderation/proposals/change-idea")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"proposalIds": ["sim-123", "sim-124"], "ideaId":"Idea 3" }""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("change success") {
      Given("an authenticated user with admin role")
      When("the user change idea of proposals")
      Then("he should get an success (201) return code")

      Post("/moderation/proposals/change-idea")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"proposalIds": ["sim-123", "sim-124"], "ideaId":"Idea 3" }""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }

    scenario("invalid idea id") {
      Given("an authenticated user with admin role")
      And("an invalid ideaId")
      When("the user change idea of proposals using invalid ideaId")
      Then("he should get a bad request (400) return code")

      Post("/moderation/proposals/change-idea")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"proposalIds": ["sim-123", "sim-124"], "ideaId":"fake" }""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "ideaId")
        contentError should be(Some(ValidationError("ideaId", Some("Invalid idea id"))))
      }
    }

    scenario("invalid proposal id") {
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
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "proposalIds")
        contentError should be(Some(ValidationError("proposalIds", Some("Some proposal ids are invalid: fake, fake2"))))
      }
    }

  }

  feature("next proposal to moderate") {

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
            country = Country("FR"),
            language = Language("fr"),
            question = "",
            operationId = Some(OperationId("vff")),
            themeId = None
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
              country = Country("FR"),
              language = Language("fr"),
              question = "",
              operationId = Some(OperationId("mieux-vivre-ensemble")),
              themeId = None
            )
          )
        )
      )

    scenario("unauthenticated call") {
      Given("an unauthenticated user")
      When("the user requests the next proposal to moderate")
      Then("The return code should be 401")

      Post("/moderation/proposals/next")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validPayload)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }

    }

    scenario("calling with a user right") {
      Given("an authenticated user with the user role")
      When("the user requests the next proposal to moderate")
      Then("The return code should be 403")

      Post("/moderation/proposals/next")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validPayload))
        .withHeaders(Authorization(OAuth2BearerToken(userToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("calling without right on question") {
      Given("an authenticated user with the moderator role, without rights")
      When("the user requests the next proposal to moderate")
      Then("The return code should be 403")

      Post("/moderation/proposals/next")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validPayload))
        .withHeaders(Authorization(OAuth2BearerToken(moderator2Token))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("no proposal to moderate") {
      Given("an authenticated user with the moderator role")
      When("the user requests the next proposal to moderate")
      And("there is no proposal matching criterias")
      Then("The return code should be 404")

      when(
        proposalService.searchAndLockProposalToModerate(
          matches(QuestionId("question-vff")),
          matches(tyrion.userId),
          any[RequestContext],
          any[Boolean],
          any[Option[Int]],
          any[Option[Float]]
        )
      ).thenReturn(Future.successful(None))

      Post("/moderation/proposals/next")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validPayload))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("normal case") {
      Given("an authenticated user with the moderator role")
      When("the user requests the next proposal to moderate")
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
        proposalService.searchAndLockProposalToModerate(
          matches(QuestionId("question-mieux-vivre-ensemble")),
          matches(tyrion.userId),
          any[RequestContext],
          any[Boolean],
          any[Option[Int]],
          any[Option[Float]]
        )
      ).thenReturn(Future.successful(Some(proposal(ProposalId("123456789")))))

      Post("/moderation/proposals/next")
        .withEntity(HttpEntity(ContentTypes.`application/json`, payload))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("invalid payload") {
      Given("an authenticated user with the moderator role")
      When("the user requests the next proposal to moderate")
      And("there is a proposal matching criterias")
      Then("The return code should be 200")

      val payload =
        """
          |{
          |  "toEnrich": false
          |}
        """.stripMargin

      when(
        proposalService.searchAndLockProposalToModerate(
          matches(QuestionId("question-mieux-vivre-ensemble")),
          matches(tyrion.userId),
          any[RequestContext],
          any[Boolean],
          any[Option[Int]],
          any[Option[Float]]
        )
      ).thenReturn(Future.successful(Some(proposal(ProposalId("123456789")))))

      Post("/moderation/proposals/next")
        .withEntity(HttpEntity(ContentTypes.`application/json`, payload))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  feature("get proposals") {
    scenario("get proposals by created at date") {
      Given("an authenticated user with the moderator role")
      When("the user search proposals using created before")
      And("there is a proposal matching criterias")
      Then("The return code should be 200")

      val beforeDateString: String = "2017-06-04T01:01:01.123Z"
      val beforeDate: ZonedDateTime = ZonedDateTime.from(dateFormatter.parse(beforeDateString))

      when(
        proposalService.search(
          any[Option[UserId]],
          matches(
            SearchQuery(
              filters = Some(
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
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val results: ProposalsSearchResult = entityAs[ProposalsSearchResult]
        results.total should be(42)
      }

    }
  }

  feature("get predicted tags for proposal") {
    scenario("unauthorized user") {
      Get("/moderation/proposals/123456/predicted-tags") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
    scenario("forbidden citizen") {
      Get("/moderation/proposals/123456/predicted-tags")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    scenario("allowed moderator") {
      when(proposalService.getTagsForProposal(any[Proposal]))
        .thenReturn(
          Future.successful(
            TagsForProposalResponse(
              tags = Seq(
                TagForProposalResponse(
                  id = TagId("tag-id"),
                  label = "label",
                  tagTypeId = TagTypeId("tag-type-id"),
                  weight = 1.0F,
                  questionId = Some(QuestionId("question-id")),
                  checked = true,
                  predicted = true
                )
              ),
              modelName = "auto"
            )
          )
        )
      Get("/moderation/proposals/123456/predicted-tags")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val results: TagsForProposalResponse = entityAs[TagsForProposalResponse]
        results.tags.length should be(1)
        results.tags.head.id should be(TagId("tag-id"))

      }
    }
    scenario("not allowed moderator") {
      when(proposalService.getTagsForProposal(any[Proposal]))
        .thenReturn(
          Future.successful(
            TagsForProposalResponse(
              tags = Seq(
                TagForProposalResponse(
                  id = TagId("tag-id"),
                  label = "label",
                  tagTypeId = TagTypeId("tag-type-id"),
                  weight = 1.0F,
                  questionId = Some(QuestionId("question-id")),
                  checked = true,
                  predicted = true
                )
              ),
              modelName = "auto"
            )
          )
        )
      Get("/moderation/proposals/123456/predicted-tags")
        .withHeaders(Authorization(OAuth2BearerToken(moderator2Token))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    scenario("proposal not found") {
      when(proposalCoordinatorService.getProposal(any[ProposalId]))
        .thenReturn(Future.successful(None))

      Get("/moderation/proposals/invalid/predicted-tags")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }
}
