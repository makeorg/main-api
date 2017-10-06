package org.make.api.technical.auth

import java.time.Instant
import java.util.Date

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical._
import org.make.api.{MakeApiTestUtils, MakeUnitTest}
import org.make.core.DateHelper
import org.make.core.user.{User, UserId}
import org.mockito.{ArgumentMatchers, Mockito}

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scalaoauth2.provider.{AccessToken, AuthInfo, TokenEndpoint}

class AuthenticationApiTest
    extends MakeUnitTest
    with MakeApiTestUtils
    with MakeAuthenticationDirectives
    with MakeDataHandlerComponent
    with AuthenticationApi
    with MakeSettingsComponent
    with IdGeneratorComponent
    with EventBusServiceComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val tokenEndpoint: TokenEndpoint = mock[TokenEndpoint]
  override lazy val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val makeSettings: MakeSettings = mock[MakeSettings]

  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  private val oauthConfiguration = mock[makeSettings.Oauth.type]
  override val eventBusService: EventBusService = mock[EventBusService]

  Mockito
    .when(makeSettings.SessionCookie)
    .thenReturn(sessionCookieConfiguration)
  Mockito
    .when(makeSettings.Oauth)
    .thenReturn(oauthConfiguration)
  Mockito
    .when(makeSettings.frontUrl)
    .thenReturn("http:://localhost")
  Mockito
    .when(sessionCookieConfiguration.name)
    .thenReturn("cookie-session")
  Mockito
    .when(sessionCookieConfiguration.isSecure)
    .thenReturn(false)
  Mockito
    .when(sessionCookieConfiguration.lifetime)
    .thenReturn(Duration("20 minutes"))
  Mockito
    .when(oauth2DataHandler.removeTokenByAccessToken(ArgumentMatchers.any[String]))
    .thenReturn(Future.successful(1))
  Mockito
    .when(oauth2DataHandler.removeTokenByAccessToken(ArgumentMatchers.eq("FAULTY_TOKEN")))
    .thenReturn(Future.successful(0))
  Mockito
    .when(idGenerator.nextId())
    .thenReturn("some-id")

  val routes: Route = sealRoute(authenticationRoutes)

  val fakeUser = User(
    userId = UserId("ABCD"),
    email = "foo@bar.com",
    firstName = Some("olive"),
    lastName = Some("tom"),
    lastIp = Some("127.0.0.1"),
    hashedPassword = Some("passpass"),
    enabled = true,
    verified = false,
    lastConnection = DateHelper.now(),
    verificationToken = Some("token"),
    verificationTokenExpiresAt = Some(DateHelper.now()),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq.empty,
    profile = None
  )

  feature("logout user by deleting its token") {
    scenario("successful logout") {
      Given("a valid authentication")
      val token = "TOKEN"
      val accessToken: AccessToken =
        AccessToken("ACCESS_TOKEN", None, None, None, Date.from(Instant.now))
      val fakeAuthInfo: AuthInfo[User] = AuthInfo(fakeUser, None, None, None)
      Mockito
        .when(oauth2DataHandler.findAccessToken(ArgumentMatchers.same(token)))
        .thenReturn(Future.successful(Some(accessToken)))
      Mockito
        .when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.same(accessToken)))
        .thenReturn(Future.successful(Some(fakeAuthInfo)))
      Mockito
        .when(oauth2DataHandler.getStoredAccessToken(ArgumentMatchers.same(fakeAuthInfo)))
        .thenReturn(Future.successful(Some(accessToken)))
      Mockito
        .when(oauth2DataHandler.removeTokenByUserId(ArgumentMatchers.same(fakeUser.userId)))
        .thenReturn(Future.successful(42))

      When("logout is called")
      val logoutRoute: RouteTestResult = Post("/logout").withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes

      Then("the service must delete at least one row")
      logoutRoute ~> check {
        status should be(StatusCodes.NoContent)
      }
    }

    scenario("unauthorize an empty authentication") {
      Given("a invalid authentication")
      val invalidToken: String = "FAULTY_TOKEN"
      Mockito
        .when(oauth2DataHandler.findAccessToken(ArgumentMatchers.same(invalidToken)))
        .thenReturn(Future.successful(None))

      When("logout is called")
      val logoutRoute
        : RouteTestResult = Post("/logout").withHeaders(Authorization(OAuth2BearerToken(invalidToken))) ~> routes

      Then("the service must delete at least one row")
      logoutRoute ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
  }
}
