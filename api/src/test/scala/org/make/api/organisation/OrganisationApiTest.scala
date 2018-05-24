package org.make.api.organisation

import java.time.ZonedDateTime
import java.util.Date

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestUtils
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.proposal.{ProposalResult, ProposalService, ProposalServiceComponent, ProposalsResultSeededResponse}
import org.make.api.technical.auth.{MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.api.user.{OrganisationService, OrganisationServiceComponent, UserResponse}
import org.make.core.auth.UserRights
import org.make.core.idea.IdeaId
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.reference.ThemeId
import org.make.core.user.Role.{RoleActor, RoleCitizen}
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers.{eq => matches}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito
import org.mockito.Mockito.when
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future
import scala.concurrent.duration.Duration

class OrganisationApiTest
    extends MakeApiTestUtils
    with OrganisationApi
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with MakeSettingsComponent
    with ProposalServiceComponent
    with OrganisationServiceComponent {

  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val proposalService: ProposalService = mock[ProposalService]
  override val organisationService: OrganisationService = mock[OrganisationService]

  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  private val oauthConfiguration = mock[makeSettings.Oauth.type]
  private val validAccessToken = "my-valid-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))
  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(accessToken)))
    .thenReturn(
      Future.successful(Some(AuthInfo(UserRights(UserId("user-citizen"), Seq(RoleCitizen)), None, Some("user"), None)))
    )
  Mockito.when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  Mockito.when(sessionCookieConfiguration.isSecure).thenReturn(false)
  Mockito.when(sessionCookieConfiguration.lifetime).thenReturn(Duration("20 minutes"))
  Mockito.when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  Mockito.when(makeSettings.Oauth).thenReturn(oauthConfiguration)
  Mockito.when(idGenerator.nextId()).thenReturn("next-id")

  val routes: Route = sealRoute(organisationRoutes)

  val now: ZonedDateTime = DateHelper.now()

  Mockito
    .when(organisationService.getOrganisation(UserId("make-org")))
    .thenReturn(
      Future.successful(
        Some(
          User(
            userId = UserId("make-org"),
            email = "make@make.org",
            firstName = None,
            lastName = None,
            organisationName = Some("make.org"),
            lastIp = None,
            hashedPassword = None,
            enabled = true,
            emailVerified = true,
            isOrganisation = true,
            lastConnection = now,
            verificationToken = None,
            verificationTokenExpiresAt = None,
            resetToken = None,
            resetTokenExpiresAt = None,
            roles = Seq(RoleActor),
            country = "FR",
            language = "fr",
            profile = None,
            createdAt = None,
            updatedAt = None,
            lastMailingError = None
          )
        )
      )
    )

  Mockito
    .when(organisationService.getOrganisation(UserId("classic-user")))
    .thenReturn(
      Future.successful(
        Some(
          User(
            userId = UserId("classic-user"),
            email = "classic@user.org",
            firstName = Some("Classic"),
            lastName = Some("User"),
            lastIp = None,
            hashedPassword = None,
            enabled = true,
            emailVerified = true,
            lastConnection = now,
            verificationToken = None,
            verificationTokenExpiresAt = None,
            resetToken = None,
            resetTokenExpiresAt = None,
            roles = Seq(RoleCitizen),
            country = "FR",
            language = "fr",
            profile = None,
            createdAt = None,
            updatedAt = None,
            lastMailingError = None
          )
        )
      )
    )

  Mockito
    .when(organisationService.getOrganisation(UserId("non-existant")))
    .thenReturn(Future.successful(None))

  val proposalsList = ProposalsResultSeededResponse(
    total = 2,
    results = Seq(
      ProposalResult(
        id = ProposalId("proposal-1"),
        country = "FR",
        language = "fr",
        userId = UserId("make-org"),
        content = "blabla",
        slug = "blabla",
        createdAt = ZonedDateTime.now(),
        updatedAt = Some(ZonedDateTime.now()),
        votes = Seq.empty,
        context = Some(Context(source = None, operation = None, location = None, question = None)),
        trending = None,
        labels = Seq.empty,
        author = Author(firstName = None, postalCode = None, age = None),
        themeId = Some(ThemeId("foo-theme")),
        tags = Seq.empty,
        status = ProposalStatus.Accepted,
        idea = Some(IdeaId("idea-id")),
        operationId = None,
        myProposal = false
      ),
      ProposalResult(
        id = ProposalId("proposal-2"),
        country = "FR",
        language = "fr",
        userId = UserId("make-org"),
        content = "blablabla",
        slug = "blablabla",
        createdAt = ZonedDateTime.now(),
        updatedAt = Some(ZonedDateTime.now()),
        votes = Seq.empty,
        context = Some(Context(source = None, operation = None, location = None, question = None)),
        trending = None,
        labels = Seq.empty,
        author = Author(firstName = None, postalCode = None, age = None),
        themeId = Some(ThemeId("bar-theme")),
        tags = Seq.empty,
        status = ProposalStatus.Accepted,
        idea = Some(IdeaId("other-idea-id")),
        operationId = None,
        myProposal = false
      )
    ),
    None
  )

  Mockito
    .when(proposalService.searchForUser(any[Option[UserId]], any[SearchQuery], any[Option[Int]], any[RequestContext]))
    .thenReturn(Future.successful(proposalsList))

  feature("get organisation") {
    scenario("get existing organisation") {
      Get("/organisations/make-org") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val organisation: UserResponse = entityAs[UserResponse]
        organisation.userId should be(UserId("make-org"))
      }
    }

    scenario("get non existing organisation") {
      Get("/organisations/non-existant") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  feature("Get list of organisation proposals") {
    scenario("organisationId does not correspond to an organisation") {
      Get("/organisations/classic-user/proposals") ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("search organisation proposals unauthenticated") {
      Get("/organisations/make-org/proposals") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val proposalsSearchResult: ProposalsSearchResult = entityAs[ProposalsSearchResult]
        proposalsSearchResult.total shouldBe 2
        proposalsSearchResult.results.size shouldBe 2
        proposalsSearchResult.results.exists(_.id == ProposalId("proposal-1")) shouldBe true
        proposalsSearchResult.results.exists(_.id == ProposalId("proposal-2")) shouldBe true
      }
    }

    scenario("search organisation proposals authenticated") {
      Get("/organisations/make-org/proposals")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val proposalsSearchResult: ProposalsSearchResult = entityAs[ProposalsSearchResult]
        proposalsSearchResult.total shouldBe 2
        proposalsSearchResult.results.size shouldBe 2
        proposalsSearchResult.results.exists(_.id == ProposalId("proposal-1")) shouldBe true
        proposalsSearchResult.results.exists(_.id == ProposalId("proposal-2")) shouldBe true
      }
    }
  }

}
