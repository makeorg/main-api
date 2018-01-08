package org.make.api.proposal

import java.time.ZonedDateTime
import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import io.circe.syntax._
import org.make.api.MakeApiTestUtils
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.idea.{IdeaService, IdeaServiceComponent}
import org.make.api.technical.auth.{MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.api.theme.{ThemeService, ThemeServiceComponent}
import org.make.api.user.{UserResponse, UserService, UserServiceComponent}
import org.make.core.auth.UserRights
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
    with ThemeServiceComponent {

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
  override val ideaService: IdeaService = mock[IdeaService]

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
    idea = Some(IdeaId("becoming-king"))
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

  val validProposalText: String = "Il faut que tout le monde respecte les conventions de code"
  val invalidMaxLengthProposalText: String =
    "Il faut que le texte de la proposition n'exède pas une certaine limite, par exemple 140 caractères car sinon, " +
      "ça fait vraiment troooooop long. D'un autre côté on en dit peu en 140 caractères..."

  val invalidMinLengthProposalText: String = "Il faut"

  when(
    proposalService
      .propose(any[User], any[RequestContext], any[ZonedDateTime], matches(validProposalText), any[Option[ThemeId]])
  ).thenReturn(Future.successful(ProposalId("my-proposal-id")))

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

  when(
    proposalService
      .getModerationProposalById(matches(ProposalId("sim-123")))
  ).thenReturn(
    Future.successful(
      Some(
        ProposalResponse(
          proposalId = ProposalId("sim-123"),
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
          similarProposals = Seq(ProposalId("sim-456"), ProposalId("sim-789")),
          idea = None,
          ideaProposals = Seq.empty
        )
      )
    )
  )

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
      ideaProposals = Seq.empty
    )
  }

  when(ideaService.fetchOne(any[IdeaId]))
    .thenReturn(Future.successful(Some(Idea(IdeaId("foo"), "Foo", None, None, None, None))))

  when(proposalService.getDuplicates(any[UserId], matches(ProposalId("123456")), any[RequestContext]))
    .thenReturn(
      Future.successful(
        Seq(
          DuplicateResponse(IdeaId("Idea 1"), "Idea One", ProposalId("Proposal 1"), "Proposal One", 1.23456),
          DuplicateResponse(IdeaId("Idea 2"), "Idea Two", ProposalId("Proposal 2"), "Proposal Two", 0.123456)
        )
      )
    )

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
        results(0).ideaId should be(IdeaId("Idea 1"))
        results(1).ideaName should be("Idea Two")
        results(0).score should be(1.23456)
        results(1).proposalId should be(ProposalId("Proposal 2"))
        results(0).proposalContent should be("Proposal One")
      }
    }
  }
}