package org.make.api.proposal

import java.util.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import io.circe.syntax._
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.idea.{IdeaService, IdeaServiceComponent}
import org.make.api.operation.{OperationService, OperationServiceComponent}
import org.make.api.technical.auth.{MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent, ReadJournalComponent}
import org.make.api.theme.{ThemeService, ThemeServiceComponent}
import org.make.api.user.{UserResponse, UserService, UserServiceComponent}
import org.make.api.{ActorSystemComponent, MakeApiTestUtils}
import org.make.core.auth.UserRights
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.indexed._
import org.make.core.proposal.{ProposalId, ProposalStatus, SearchQuery, _}
import org.make.core.reference._
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationError, ValidationFailedError}
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito._

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scalaoauth2.provider.{AccessToken, AuthInfo}

class ModerationProposalApiTest
    extends MakeApiTestUtils
    with ProposalApi
    with ModerationProposalApi
    with IdeaServiceComponent
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with ProposalServiceComponent
    with MakeSettingsComponent
    with UserServiceComponent
    with ThemeServiceComponent
    with OperationServiceComponent
    with ProposalCoordinatorServiceComponent
    with ReadJournalComponent
    with ActorSystemComponent {

  override val makeSettings: MakeSettings = mock[MakeSettings]

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val proposalService: ProposalService = mock[ProposalService]

  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  private val oauthConfiguration = mock[makeSettings.Oauth.type]

  when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  when(sessionCookieConfiguration.isSecure).thenReturn(false)
  when(sessionCookieConfiguration.lifetime).thenReturn(Duration("20 minutes"))
  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(makeSettings.Oauth).thenReturn(oauthConfiguration)
  when(idGenerator.nextId()).thenReturn("next-id")

  override val userService: UserService = mock[UserService]
  override val themeService: ThemeService = mock[ThemeService]
  override val operationService: OperationService = mock[OperationService]
  override val ideaService: IdeaService = mock[IdeaService]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]
  override val actorSystem: ActorSystem = mock[ActorSystem]
  override val readJournal: ReadJournalComponent.MakeReadJournal = mock[ReadJournalComponent.MakeReadJournal]

  private val john = User(
    userId = UserId("my-user-id"),
    email = "john.snow@night-watch.com",
    firstName = Some("John"),
    lastName = Some("Snoww"),
    lastIp = None,
    hashedPassword = None,
    enabled = true,
    verified = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleCitizen),
    profile = None,
    createdAt = None,
    updatedAt = None
  )

  val daenerys = User(
    userId = UserId("the-mother-of-dragons"),
    email = "d.narys@tergarian.com",
    firstName = Some("Daenerys"),
    lastName = Some("Tergarian"),
    lastIp = None,
    hashedPassword = None,
    enabled = true,
    verified = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleAdmin),
    profile = None,
    createdAt = None,
    updatedAt = None
  )

  val tyrion = User(
    userId = UserId("the-dwarf"),
    email = "tyrion@pays-his-debts.com",
    firstName = Some("Tyrion"),
    lastName = Some("Lannister"),
    lastIp = None,
    hashedPassword = None,
    enabled = true,
    verified = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleModerator),
    profile = None,
    createdAt = None,
    updatedAt = None
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

  val validateProposalEntity: String = ValidateProposalRequest(
    newContent = None,
    sendNotificationEmail = true,
    theme = Some(ThemeId("fire and ice")),
    labels = Seq(LabelId("sex"), LabelId("violence")),
    tags = Seq(TagId("dragon"), TagId("sword")),
    similarProposals = Seq(),
    idea = Some(IdeaId("becoming-king")),
    operation = None
  ).asJson.toString

  val refuseProposalWithReasonEntity: String =
    RefuseProposalRequest(sendNotificationEmail = true, refusalReason = Some("not allowed word")).asJson.toString

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))
  when(oauth2DataHandler.findAccessToken(adminToken)).thenReturn(Future.successful(Some(adminAccessToken)))
  when(oauth2DataHandler.findAccessToken(moderatorToken)).thenReturn(Future.successful(Some(moderatorAccessToken)))
  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(accessToken)))
    .thenReturn(Future.successful(Some(AuthInfo(UserRights(john.userId, john.roles), None, Some("user"), None))))
  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future.successful(Some(AuthInfo(UserRights(userId = daenerys.userId, roles = daenerys.roles), None, None, None)))
    )
  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(Future.successful(Some(AuthInfo(UserRights(tyrion.userId, tyrion.roles), None, None, None))))

  when(
    proposalService
      .validateProposal(matches(ProposalId("123456")), any[UserId], any[RequestContext], any[ValidateProposalRequest])
  ).thenReturn(Future.successful(Some(proposal(ProposalId("123456")))))
  when(
    proposalService
      .validateProposal(matches(ProposalId("987654")), any[UserId], any[RequestContext], any[ValidateProposalRequest])
  ).thenReturn(Future.successful(Some(proposal(ProposalId("987654")))))
  when(
    proposalService
      .validateProposal(matches(ProposalId("nop")), any[UserId], any[RequestContext], any[ValidateProposalRequest])
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
    events = Nil
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
    events = Nil
  )

  when(
    proposalService
      .getModerationProposalById(matches(proposalSim123.proposalId))
  ).thenReturn(
    Future.successful(
      Some(
        ProposalResponse(
          proposalId = proposalSim123.proposalId,
          slug = proposalSim123.slug,
          content = proposalSim123.content,
          author = UserResponse(
            proposalSim123.author,
            email = "g@rr.martin",
            firstName = Some("Georges"),
            lastName = Some("Martin"),
            enabled = true,
            verified = true,
            lastConnection = DateHelper.now(),
            roles = Seq.empty,
            None
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
          similarProposals = Seq(ProposalId("sim-456"), ProposalId("sim-789")),
          idea = None,
          ideaProposals = Seq.empty,
          operationId = None
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
        ProposalResponse(
          proposalId = proposalSim124.proposalId,
          slug = proposalSim124.slug,
          content = proposalSim124.content,
          author = UserResponse(
            proposalSim124.author,
            email = "g@rr.martin",
            firstName = Some("Georges"),
            lastName = Some("Martin"),
            enabled = true,
            verified = true,
            lastConnection = DateHelper.now(),
            roles = Seq.empty,
            None
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
          similarProposals = Seq(ProposalId("sim-111"), ProposalId("sim-222")),
          idea = None,
          ideaProposals = Seq.empty,
          operationId = None
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

  val proposalResult = ProposalResult(
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
    author = Author(None, None, None),
    country = "TN",
    language = "ar",
    themeId = None,
    tags = Seq.empty,
    myProposal = false,
    idea = None
  )

  when(
    proposalService
      .searchForUser(any[Option[UserId]], any[SearchQuery], any[Option[Int]], any[RequestContext])
  ).thenReturn(Future.successful(ProposalsResultSeededResponse(1, Seq(proposalResult), Some(42))))

  private def proposal(id: ProposalId): ProposalResponse = {
    ProposalResponse(
      proposalId = id,
      slug = "a-song-of-fire-and-ice",
      content = "A song of fire and ice",
      author = UserResponse(
        UserId("Georges RR Martin"),
        email = "g@rr.martin",
        firstName = Some("Georges"),
        lastName = Some("Martin"),
        enabled = true,
        verified = true,
        lastConnection = DateHelper.now(),
        roles = Seq.empty,
        None
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
      similarProposals = Seq.empty,
      idea = None,
      ideaProposals = Seq.empty,
      operationId = None
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

  when(proposalService.getDuplicates(any[UserId], matches(ProposalId("123456")), any[RequestContext]))
    .thenReturn(
      Future.successful(
        Seq(
          DuplicateResponse(IdeaId("Idea 1"), "Idea One", ProposalId("Proposal 1"), "Proposal One", 1.23456),
          DuplicateResponse(IdeaId("Idea 2"), "Idea Two", ProposalId("Proposal 2"), "Proposal Two", 0.123456)
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

  val routes: Route = sealRoute(moderationProposalRoutes)

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

    scenario("validation with moderation role") {

      Post("/moderation/proposals/123456/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntity))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("validation with admin role") {
      Post("/moderation/proposals/987654/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntity))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("validation of non existing with admin role") {
      Post("/moderation/proposals/nop/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntity))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    // Todo: implement this test
    scenario("validation of proposal without Tag: this test should be done") {}
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
        val proposalResponse: ProposalResponse = entityAs[ProposalResponse]
        proposalResponse.similarProposals.length should be(2)
        proposalResponse.similarProposals should be(Seq(ProposalId("sim-456"), ProposalId("sim-789")))
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
      Given("an authenticated user with moderator role")
      When("the user change idea of proposals")
      Then("he should get an success (201) return code")

      Post("/moderation/proposals/change-idea")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"proposalIds": ["sim-123", "sim-124"], "ideaId":"Idea 3" }""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }

    scenario("invalid idea id") {
      Given("an authenticated user with moderator role")
      And("an invalid ideaId")
      When("the user change idea of proposals using invalid ideaId")
      Then("he should get a bad request (400) return code")

      Post("/moderation/proposals/change-idea")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"proposalIds": ["sim-123", "sim-124"], "ideaId":"fake" }""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "ideaId")
        contentError should be(Some(ValidationError("ideaId", Some("Invalid idea id"))))
      }
    }

    scenario("invalid proposal id") {
      Given("an authenticated user with moderator role")
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
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "proposalIds")
        contentError should be(Some(ValidationError("proposalIds", Some("Some proposal ids are invalid: fake, fake2"))))
      }
    }

  }
}
