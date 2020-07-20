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

import java.time.Instant
import java.util.{Date, UUID}

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{`Set-Cookie`, Authorization, Cookie, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.technical._
import org.make.core.RequestContext
import org.make.core.auth.UserRights
import org.make.core.session.SessionId
import org.make.core.user.UserId
import scalaoauth2.provider.{AccessToken, AuthInfo, TokenEndpoint}

import scala.concurrent.Future

class AuthenticationApiTest
    extends MakeApiTestBase
    with MakeAuthenticationDirectives
    with DefaultAuthenticationApiComponent {

  override val tokenEndpoint: TokenEndpoint = mock[TokenEndpoint]

  when(sessionHistoryCoordinatorService.convertSession(any[SessionId], any[UserId], any[RequestContext]))
    .thenReturn(Future.successful {})

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
        .thenReturn(Future.successful {})

      When("logout is called")
      val logoutRoute: RouteTestResult = Post("/logout").withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes

      Then("the service must delete at least one row")
      logoutRoute ~> check {
        status should be(StatusCodes.NoContent)
        response.headers[`Set-Cookie`].map(_.cookie).find(_.name == makeSettings.VisitorCookie.name) should be(None)
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
