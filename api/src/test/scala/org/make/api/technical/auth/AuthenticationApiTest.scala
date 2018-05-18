package org.make.api.technical.auth

import java.time.Instant
import java.util.Date

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.technical._
import org.make.core.auth.UserRights
import org.make.core.session.SessionId
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import scalaoauth2.provider.{AccessToken, AuthInfo, TokenEndpoint}

import scala.concurrent.Future

class AuthenticationApiTest extends MakeApiTestBase with MakeAuthenticationDirectives with AuthenticationApi {

  override val tokenEndpoint: TokenEndpoint = mock[TokenEndpoint]

  when(sessionHistoryCoordinatorService.convertSession(any[SessionId], any[UserId]))
    .thenReturn(Future.successful {})

  when(oauth2DataHandler.removeTokenByAccessToken(any[String]))
    .thenReturn(Future.successful(1))
  when(oauth2DataHandler.removeTokenByAccessToken(ArgumentMatchers.eq("FAULTY_TOKEN")))
    .thenReturn(Future.successful(0))

  val routes: Route = sealRoute(authenticationRoutes)

  feature("logout user by deleting its token") {
    scenario("successful logout") {
      Given("a valid authentication")
      val token = "TOKEN"
      val accessToken: AccessToken =
        AccessToken("ACCESS_TOKEN", None, None, None, Date.from(Instant.now))
      val fakeAuthInfo: AuthInfo[UserRights] = AuthInfo(UserRights(UserId("ABCD"), Seq.empty), None, None, None)
      when(oauth2DataHandler.findAccessToken(ArgumentMatchers.eq(token)))
        .thenReturn(Future.successful(Some(accessToken)))
      when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.eq(accessToken)))
        .thenReturn(Future.successful(Some(fakeAuthInfo)))
      when(oauth2DataHandler.getStoredAccessToken(ArgumentMatchers.eq(fakeAuthInfo)))
        .thenReturn(Future.successful(Some(accessToken)))
      when(oauth2DataHandler.removeTokenByUserId(ArgumentMatchers.eq(UserId("ABCD"))))
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
      when(oauth2DataHandler.findAccessToken(ArgumentMatchers.same(invalidToken)))
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
