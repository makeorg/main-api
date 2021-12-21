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

package org.make.api.technical.auth

import akka.http.scaladsl.model.headers.{`Set-Cookie`, Authorization, Cookie, OAuth2BearerToken}
import akka.http.scaladsl.model.{FormData, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.technical._
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.core.auth.{ClientId, Token, UserRights}
import org.make.core.session.SessionId
import org.make.core.user.{User, UserId}
import org.make.core.{ApplicationName, DateHelper, RequestContext}
import scalaoauth2.provider._

import java.time.Instant
import java.util.{Date, UUID}
import scala.concurrent.{ExecutionContext, Future}

class AuthenticationApiTest
    extends MakeApiTestBase
    with MakeAuthenticationDirectives
    with DefaultAuthenticationApiComponent
    with UserServiceComponent {

  override val tokenEndpoint: TokenEndpoint = mock[TokenEndpoint]
  override val userService: UserService = mock[UserService]

  when(sessionHistoryCoordinatorService.convertSession(any[SessionId], any[UserId], any[RequestContext]))
    .thenReturn(Future.unit)

  reset(oauth2DataHandler)

  val routes: Route = sealRoute(authenticationApi.routes)

  Feature("get a token user") {
    Scenario("successful get token") {
      val token = "TOKEN"
      val accessToken: AccessToken =
        AccessToken("ACCESS_TOKEN", None, None, None, Date.from(Instant.now))
      val fakeAuthInfo: AuthInfo[UserRights] =
        AuthInfo(
          UserRights(userId = UserId("ABCD"), roles = Seq.empty, availableQuestions = Seq.empty, emailVerified = true),
          None,
          None,
          None
        )
      when(oauth2DataHandler.findAccessToken(eqTo(token)))
        .thenReturn(Future.successful(Some(accessToken)))
      when(oauth2DataHandler.findAuthInfoByAccessToken(eqTo(accessToken)))
        .thenReturn(Future.successful(Some(fakeAuthInfo)))
      when(oauth2DataHandler.getStoredAccessToken(eqTo(fakeAuthInfo)))
        .thenReturn(Future.successful(Some(accessToken)))

      When("access token is called")
      val getAccessTokenRoute: RouteTestResult = Get("/oauth/access_token").withHeaders(
        Authorization(OAuth2BearerToken(token))
      ) ~> routes

      Then("the service must return the access token")
      getAccessTokenRoute ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("unauthorize an empty authentication") {
      Given("an invalid authentication")
      val invalidToken: String = "FAULTY_TOKEN"
      when(oauth2DataHandler.findAccessToken(same(invalidToken)))
        .thenReturn(Future.successful(None))

      When("access token is called")
      val getAccessTokenRoute: RouteTestResult = Get("/oauth/access_token").withHeaders(
        Authorization(OAuth2BearerToken(invalidToken))
      ) ~> routes

      Then("the service must return unauthorized")
      getAccessTokenRoute ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
  }

  Feature("create access token") {
    Scenario("create token") {
      val authInfo: AuthInfo[UserRights] =
        AuthInfo(
          UserRights(
            userId = UserId("ABCD-privacy-policy"),
            roles = Seq.empty,
            availableQuestions = Seq.empty,
            emailVerified = true
          ),
          None,
          None,
          None
        )
      val grantResult: GrantHandlerResult[UserRights] =
        GrantHandlerResult[UserRights](authInfo, "password", "token", None, None, None, Map.empty)
      val result: Either[OAuthError, GrantHandlerResult[UserRights]] = Right(grantResult)

      when(
        tokenEndpoint
          .handleRequest(any[AuthorizationRequest], any[AuthorizationHandler[UserRights]])(any[ExecutionContext])
      ).thenReturn(Future.successful(result))

      Post(
        "/oauth/access_token",
        FormData("username" -> "yopmail@make.org", "password" -> "p4ssW0Rd", "grant_type" -> "password")
      ) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("create token and set privacy policy approval date") {
      val authInfo: AuthInfo[UserRights] =
        AuthInfo(
          UserRights(userId = UserId("ABCD"), roles = Seq.empty, availableQuestions = Seq.empty, emailVerified = true),
          None,
          None,
          None
        )
      val grantResult: GrantHandlerResult[UserRights] =
        GrantHandlerResult[UserRights](authInfo, "password", "token", None, None, None, Map.empty)
      val result: Either[OAuthError, GrantHandlerResult[UserRights]] = Right(grantResult)

      when(
        tokenEndpoint
          .handleRequest(any[AuthorizationRequest], any[AuthorizationHandler[UserRights]])(any[ExecutionContext])
      ).thenReturn(Future.successful(result))

      when(userService.getUser(eqTo(authInfo.user.userId)))
        .thenReturn(Future.successful(Some(user(id = authInfo.user.userId))))
      when(userService.update(any[User], any[RequestContext]))
        .thenReturn(Future.successful(user(id = authInfo.user.userId)))

      Post(
        "/oauth/access_token",
        FormData(
          "username" -> "yopmail@make.org",
          "password" -> "p4ssW0Rd",
          "grant_type" -> "password",
          "approvePrivacyPolicy" -> "true"
        )
      ) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  Feature("refresh access token") {
    val authInfo: AuthInfo[UserRights] =
      AuthInfo(
        UserRights(
          userId = UserId("ABCD-refresh"),
          roles = Seq.empty,
          availableQuestions = Seq.empty,
          emailVerified = true
        ),
        None,
        None,
        None
      )

    Scenario("refresh token from app with cookies") {
      val grantResult: GrantHandlerResult[UserRights] =
        GrantHandlerResult[UserRights](
          authInfo,
          "Bearer",
          "new-token-cookie",
          Some(42L),
          Some("refresh-token"),
          None,
          Map.empty
        )
      val result: Either[OAuthError, GrantHandlerResult[UserRights]] = Right(grantResult)

      when(
        tokenEndpoint
          .handleRequest(any[AuthorizationRequest], any[AuthorizationHandler[UserRights]])(any[ExecutionContext])
      ).thenReturn(Future.successful(result))

      val client = TestUtils.client(ClientId("client-id"))
      val token = Token("token-cookie", Some("refresh"), None, 42, 84, authInfo.user, client, None, None)
      when(oauth2DataHandler.refreshIfTokenIsExpired(eqTo("token-cookie"))).thenReturn(Future.successful(Some(token)))

      val accessToken =
        AccessToken("token-cookie", Some("refresh"), None, None, Date.from(DateHelper.now().toInstant), Map.empty)
      when(oauth2DataHandler.findAccessToken(eqTo("token-cookie")))
        .thenReturn(Future.successful(Some(accessToken)))
      when(oauth2DataHandler.findAuthInfoByAccessToken(eqTo(accessToken)))
        .thenReturn(Future.successful(Some(authInfo)))

      Post(
        "/oauth/access_token",
        FormData("username" -> "yopmail@make.org", "refresh_token" -> "refresh-token", "grant_type" -> "refresh_token")
      ).withHeaders(
        Cookie(makeSettings.SecureCookie.name, "token-cookie"),
        `X-Session-Id`("session-id"),
        Authorization(OAuth2BearerToken("token-header")),
        `X-Make-App-Name`(ApplicationName.MainFrontend.value)
      ) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val token = entityAs[TokenResponse]
        token.accessToken shouldBe "new-token-cookie"
        token.refreshToken should contain("refresh-token")
      }
    }

    Scenario("refresh token from app with cookiesless") {
      val grantResult: GrantHandlerResult[UserRights] =
        GrantHandlerResult[UserRights](
          authInfo,
          "Bearer",
          "new-token-header",
          Some(42L),
          Some("refresh-token"),
          None,
          Map.empty
        )
      val result: Either[OAuthError, GrantHandlerResult[UserRights]] = Right(grantResult)

      when(
        tokenEndpoint
          .handleRequest(any[AuthorizationRequest], any[AuthorizationHandler[UserRights]])(any[ExecutionContext])
      ).thenReturn(Future.successful(result))

      val token =
        AccessToken("token-header", Some("refresh"), None, None, Date.from(DateHelper.now().toInstant), Map.empty)
      when(oauth2DataHandler.findAccessToken(eqTo("token-header"))).thenReturn(Future.successful(Some(token)))
      when(oauth2DataHandler.findAuthInfoByAccessToken(eqTo(token))).thenReturn(Future.successful(Some(authInfo)))

      Post(
        "/oauth/access_token",
        FormData("username" -> "yopmail@make.org", "refresh_token" -> "refresh-token", "grant_type" -> "refresh_token")
      ).withHeaders(
        Cookie(makeSettings.SecureCookie.name, "token-cookie"),
        Authorization(OAuth2BearerToken("token-header")),
        `X-Make-App-Name`(ApplicationName.Backoffice.value)
      ) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val token = entityAs[TokenResponse]
        token.accessToken shouldBe "new-token-header"
        token.refreshToken should contain("refresh-token")
      }
    }
  }

  Feature("logout user by deleting its token") {
    Scenario("successful logout") {
      Given("a valid authentication")
      val token = "TOKEN"
      val accessToken: AccessToken =
        AccessToken("ACCESS_TOKEN", None, None, None, Date.from(Instant.now))
      val fakeAuthInfo: AuthInfo[UserRights] =
        AuthInfo(
          UserRights(userId = UserId("ABCD"), roles = Seq.empty, availableQuestions = Seq.empty, emailVerified = true),
          None,
          None,
          None
        )
      when(oauth2DataHandler.findAccessToken(eqTo(token)))
        .thenReturn(Future.successful(Some(accessToken)))
      when(oauth2DataHandler.findAuthInfoByAccessToken(eqTo(accessToken)))
        .thenReturn(Future.successful(Some(fakeAuthInfo)))
      when(oauth2DataHandler.getStoredAccessToken(eqTo(fakeAuthInfo)))
        .thenReturn(Future.successful(Some(accessToken)))
      when(oauth2DataHandler.removeToken(eqTo("TOKEN")))
        .thenReturn(Future.unit)

      When("logout is called")
      val logoutRoute: RouteTestResult = Post("/logout").withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes

      Then("the service must delete at least one row")
      logoutRoute ~> check {
        status should be(StatusCodes.NoContent)
        response
          .headers[`Set-Cookie`]
          .map(_.cookie)
          .find(_.name == makeSettings.VisitorCookie.name) should be(None)
      }
    }

    Scenario("logout an invalid user") {
      Given("an invalid authentication")
      val invalidToken: String = "FAULTY_TOKEN"
      when(oauth2DataHandler.findAccessToken(same(invalidToken)))
        .thenReturn(Future.successful(None))

      When("logout is called")
      val logoutRoute
        : RouteTestResult = Post("/logout").withHeaders(Authorization(OAuth2BearerToken(invalidToken))) ~> routes

      Then("the service must return unauthorized")
      logoutRoute ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("logout an anonymous visitor") {
      When("logout is called without authentication")
      val logoutRoute: RouteTestResult = Post("/logout") ~> routes

      Then("the service must return unauthorized")
      logoutRoute ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("reset all cookies") {
      When("reset is called ")
      val sessionId = UUID.randomUUID.toString
      val resetRoute: RouteTestResult = Post("/resetCookies").withHeaders(
        Cookie(makeSettings.SessionCookie.name, sessionId)
      ) ~> routes

      Then("a new session is created and other cookies are reset")
      resetRoute ~> check {

        status should be(StatusCodes.NoContent)

        response
          .headers[`Set-Cookie`]
          .map(_.cookie)
          .find(_.name == makeSettings.SessionCookie.name)
          .map(_.value)
          .get should not be sessionId

        Seq(makeSettings.SecureCookie.name, makeSettings.UserIdCookie.name, makeSettings.VisitorCookie.name).foreach(
          name =>
            response
              .headers[`Set-Cookie`]
              .map(_.cookie)
              .find(_.name == name)
              .flatMap(_.maxAge) should be(Some(0))
        )

      }
    }
  }
}
