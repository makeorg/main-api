package org.make.api.organisation

import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestUtils
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical._
import org.make.api.technical.auth.{MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.user._
import org.make.core.auth.UserRights
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleOrganisation}
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito.when
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future
import scala.concurrent.duration.Duration

class ModerationOrganisationApiTest
    extends MakeApiTestUtils
    with ModerationOrganisationApi
    with OrganisationServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent {

  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val organisationService: OrganisationService = mock[OrganisationService]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]

  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  private val oauthConfiguration = mock[makeSettings.Oauth.type]

  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(makeSettings.Oauth).thenReturn(oauthConfiguration)
  when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  when(sessionCookieConfiguration.isSecure).thenReturn(false)
  when(idGenerator.nextId()).thenReturn("some-id")
  when(sessionCookieConfiguration.lifetime).thenReturn(Duration("20 minutes"))

  val routes: Route = sealRoute(moderationOrganisationRoutes)

  val fakeOrganisation = User(
    userId = UserId("ABCD"),
    email = "foo@bar.com",
    firstName = None,
    lastName = None,
    organisationName = Some("JohnDoe Corp."),
    lastIp = Some("127.0.0.1"),
    hashedPassword = Some("passpass"),
    enabled = true,
    emailVerified = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleOrganisation),
    country = "FR",
    language = "fr",
    profile = None
  )

  val validAccessToken = "my-valid-access-token"
  val adminToken = "my-admin-access-token"
  val moderatorToken = "my-moderator-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)
  private val adminAccessToken = AccessToken(adminToken, None, None, Some(1234567890L), tokenCreationDate)

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))
  when(oauth2DataHandler.findAccessToken(adminToken)).thenReturn(Future.successful(Some(adminAccessToken)))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(accessToken)))
    .thenReturn(
      Future.successful(Some(AuthInfo(UserRights(UserId("user-citizen"), Seq(RoleCitizen)), None, Some("user"), None)))
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future.successful(Some(AuthInfo(UserRights(UserId("user-admin"), roles = Seq(RoleAdmin)), None, None, None)))
    )

  feature("register organisation") {
    scenario("register organisation unauthenticate") {
      Given("a unauthenticate user")
      When("I want to register an organisation")
      Then("I should get an unauthorized error")
      Post("/moderation/organisations").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("register organisation without admin rights") {
      Given("a non admin user")
      When("I want to register an organisation")
      Then("I should get a forbidden error")
      Post("/moderation/organisations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("register organisation with admin rights") {
      Given("a admin user")
      When("I want to register an organisation")
      Then("I should get a Created status")
      when(organisationService.register(any[OrganisationRegisterData], any[RequestContext]))
        .thenReturn(Future.successful(fakeOrganisation))
      Post("/moderation/organisations")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{"name": "orga", "email": "bar@foo.com", "password": "azertyui"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Created
      }
    }

    scenario("register organisation without organisation name") {
      Given("a admin user")
      When("I want to register an organisation")
      Then("I should get a BadRequest status")
      Post("/moderation/organisations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"email": "bar@foo.com", "password": "azertyui"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }
}
