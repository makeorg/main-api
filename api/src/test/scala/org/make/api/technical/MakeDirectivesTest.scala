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

package org.make.api.technical

import java.time.ZonedDateTime
import java.util.Date

import akka.http.javadsl.model.headers.Origin
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.make.api.MakeApiTestBase
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth._
import org.make.core.auth.{ClientId, Token, UserRights}
import org.make.core.user.{Role, UserId}
import org.make.core.{ApplicationName, DateHelper, RequestContext}
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class MakeDirectivesTest
    extends MakeApiTestBase
    with ScalatestRouteTest
    with OauthTokenGeneratorComponent
    with ShortenedNames
    with MakeAuthentication
    with SessionHistoryCoordinatorServiceComponent
    with MakeDataHandlerComponent {

  override val oauthTokenGenerator: OauthTokenGenerator = mock[OauthTokenGenerator]

  when(makeSettings.authorizedCorsUri).thenReturn(Seq("http://make.org"))

  val route: Route = sealRoute(get {
    path("test") {
      makeOperation("test") { _ =>
        complete(StatusCodes.OK)
      }
    }
  })

  val routeRejection: Route = sealRoute(get {
    path("test") {
      makeOperation("test") { _ =>
        reject(MalformedRequestContentRejection("http", new Exception("fake rejection")))
      }
    }
  })

  val routeException: Route = sealRoute(get {
    path("test") {
      makeOperation("test") { _ =>
        throw new Exception("fake exception")
      }
    }
  })

  val routeMakeTrace: Route = sealRoute(get {
    path("testMakeTrace") {
      makeOperation("test Make trace!") { ctx: RequestContext =>
        complete(StatusCodes.OK -> ctx)
      }
    }
  })

  val routeWithParameters: Route = sealRoute(get {
    path("testWithParameter") {
      makeOperation("testWithParameter") { requestContext: RequestContext =>
        complete(StatusCodes.OK -> requestContext.getParameters)
      }
    }
  })

  val tokenRoute: Route = sealRoute(path("test") {
    requireToken(None) { token =>
      complete(token)
    }
  })

  val optionalTokenRoute: Route = sealRoute(path("test") {
    extractToken(None) {
      case Some(token) => complete(token)
      case None        => complete(StatusCodes.NotFound)
    }
  })

  reset(oauth2DataHandler)

  Feature("session id management") {

    Scenario("new session id if no cookie is sent") {
      var firstExpiration = DateHelper.now()

      Get("/test") ~> route ~> check {
        val cookiesHttpHeaders: Seq[HttpHeader] = headers.filter(_.is("set-cookie"))
        val cookiesHeaders: Seq[HttpCookie] = cookiesHttpHeaders.map(_.asInstanceOf[`Set-Cookie`].cookie)
        val maybeSessionCookie: Option[HttpCookie] = cookiesHeaders.find(_.name == "cookie-session")
        val maybeSessionExpirationCookie: Option[HttpCookie] =
          cookiesHeaders.find(_.name == "cookie-session-expiration")

        status should be(StatusCodes.OK)

        maybeSessionCookie.isEmpty shouldBe false
        maybeSessionCookie.get.secure shouldBe false
        maybeSessionCookie.get.httpOnly shouldBe true

        maybeSessionExpirationCookie.isEmpty shouldBe false
        maybeSessionExpirationCookie.get.secure shouldBe false
        maybeSessionExpirationCookie.get.httpOnly shouldBe false

        val expires = ZonedDateTime.parse(maybeSessionExpirationCookie.get.value)
        expires.isAfter(firstExpiration) shouldBe true
        firstExpiration = expires
      }

      Get("/test") ~> route ~> check {
        val cookiesHttpHeaders: Seq[HttpHeader] = headers.filter(_.is("set-cookie"))
        val cookiesHeaders: Seq[HttpCookie] = cookiesHttpHeaders.map(_.asInstanceOf[`Set-Cookie`].cookie)
        val maybeSessionExpirationCookie: Option[HttpCookie] =
          cookiesHeaders.find(_.name == "cookie-session-expiration")
        status should be(StatusCodes.OK)

        ZonedDateTime.parse(maybeSessionExpirationCookie.get.value).isAfter(firstExpiration) shouldBe true
      }
    }

    Scenario("cookie exists if session id is sent") {
      Get("/test").withHeaders(Cookie("cookie-session" -> "123")) ~> route ~> check {
        val cookiesHttpHeaders: Seq[HttpHeader] = headers.filter(_.is("set-cookie"))
        val cookiesHeaders: Seq[HttpCookie] = cookiesHttpHeaders.map(_.asInstanceOf[`Set-Cookie`].cookie)
        status should be(StatusCodes.OK)
        cookiesHeaders.exists(_.name == "cookie-session") shouldBe true
      }
    }

  }

  Feature("visitor id management") {

    Scenario("new visitor id if no cookie is sent") {
      Get("/test") ~> route ~> check {
        val cookiesHttpHeaders: Seq[HttpHeader] = headers.filter(_.is("set-cookie"))
        val cookiesHeaders: Seq[HttpCookie] = cookiesHttpHeaders.map(_.asInstanceOf[`Set-Cookie`].cookie)
        val maybeVisitorCookie: Option[HttpCookie] = cookiesHeaders.find(_.name == "cookie-visitor")

        status should be(StatusCodes.OK)

        maybeVisitorCookie.isEmpty shouldBe false
        maybeVisitorCookie.get.secure shouldBe false
        maybeVisitorCookie.get.httpOnly shouldBe true
      }
    }

    Scenario("no cookie if session id is sent") {
      Get("/test").withHeaders(Cookie("cookie-session" -> "123")) ~> route ~> check {
        val cookiesHttpHeaders: Seq[HttpHeader] = headers.filter(_.is("set-cookie"))
        val cookiesHeaders: Seq[HttpCookie] = cookiesHttpHeaders.map(_.asInstanceOf[`Set-Cookie`].cookie)
        status should be(StatusCodes.OK)
        !cookiesHeaders.exists(_.name == "cookie-visitor") shouldBe false
      }
    }

  }

  Feature("external id management") {
    Scenario("return external request id if provided") {
      Get("/test").withHeaders(`X-Make-External-Id`("test-id")) ~> route ~> check {
        status should be(StatusCodes.OK)
        header[`X-Make-External-Id`].map(_.value) should be(Some("test-id"))
      }
    }

    Scenario("provide a random external id if none is provided") {
      Get("/test") ~> route ~> check {
        status should be(StatusCodes.OK)
        header[`X-Make-External-Id`] shouldBe defined
      }
    }
  }

  Feature("request id management") {
    Scenario("return a request id") {
      Get("/test") ~> route ~> check {
        status should be(StatusCodes.OK)
        header[`X-Request-Id`] shouldBe defined
      }
    }
  }

  Feature("request time management") {
    Scenario("return the request time as a long") {
      Get("/test") ~> route ~> check {
        status should be(StatusCodes.OK)
        header[`X-Route-Time`] shouldBe defined
        // Ensure value can be converted to long
        header[`X-Route-Time`].get.value.toLong
      }
    }
  }

  Feature("route name management") {
    Scenario("return the route name header") {
      Get("/test") ~> route ~> check {
        status should be(StatusCodes.OK)
        header[`X-Route-Name`] shouldBe defined
        header[`X-Route-Name`].map(_.value) shouldBe Some("test")
      }
    }
  }

  Feature("value providers") {
    trait StringProvider {
      def provide: Future[String]
    }
    val provider: StringProvider = mock[StringProvider]

    val route = sealRoute(get {
      pathEndOrSingleSlash {
        provideAsync(provider.provide)(complete(_))
      }
    })

    Scenario("normal providing") {
      when(provider.provide).thenReturn(Future.successful("oki doki"))

      Get("/") ~> route ~> check {
        status should be(StatusCodes.OK)
        responseAs[String] should be("oki doki")
      }
    }

    Scenario("failed providers") {
      when(provider.provide).thenReturn(Future.failed(new IllegalArgumentException("fake")))

      Get("/") ~> route ~> check {
        status should be(StatusCodes.InternalServerError)
      }
    }

  }

  Feature("not found value providers") {
    trait StringProvider {
      def provide: Future[Option[String]]
    }
    val provider: StringProvider = mock[StringProvider]

    val route = sealRoute(get {
      pathEndOrSingleSlash {
        provideAsyncOrNotFound(provider.provide)(complete(_))
      }
    })

    Scenario("some providing") {
      when(provider.provide).thenReturn(Future.successful(Some("oki doki")))

      Get("/") ~> route ~> check {
        status should be(StatusCodes.OK)
        responseAs[String] should be("oki doki")
      }
    }

    Scenario("none providing") {
      when(provider.provide).thenReturn(Future.successful(None))

      Get("/") ~> route ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    Scenario("failed providers") {
      when(provider.provide).thenReturn(Future.failed(new IllegalArgumentException("fake")))

      Get("/") ~> route ~> check {
        status should be(StatusCodes.InternalServerError)
      }
    }

  }

  Feature("access control header") {
    Scenario("return header allow all origins") {
      Get("/test").addHeader(Origin.create(HttpOrigin("http://make.org"))) ~> route ~> check {
        status should be(StatusCodes.OK)
        info(headers.mkString("\n"))
        header[`Access-Control-Allow-Origin`] shouldBe defined
        header[`Access-Control-Allow-Origin`].map(_.value) shouldBe Some("http://make.org")
      }
    }

    Scenario("rejection returns header allow all origins") {
      Get("/test").addHeader(Origin.create(HttpOrigin("http://make.org"))) ~> routeRejection ~> check {
        status should be(StatusCodes.BadRequest)
        header[`Access-Control-Allow-Origin`] shouldBe defined
        header[`Access-Control-Allow-Origin`].map(_.value) shouldBe Some("http://make.org")
      }
    }

    Scenario("exception returns header allow all origins") {
      Get("/test").addHeader(Origin.create(HttpOrigin("http://make.org"))) ~> routeException ~> check {
        status should be(StatusCodes.InternalServerError)
        info(headers.mkString("\n"))
        header[`Access-Control-Allow-Origin`] shouldBe defined
        header[`Access-Control-Allow-Origin`].map(_.value) shouldBe Some("http://make.org")
      }
    }

    Scenario("exception in oauth returns header allow all origins") {
      when(oauth2DataHandler.refreshIfTokenIsExpired(eqTo("invalid")))
        .thenReturn(Future.successful(None))
      when(oauth2DataHandler.findAccessToken("invalid"))
        .thenReturn(Future.failed(TokenAlreadyRefreshed("invalid")))

      Get("/test")
        .addHeader(Origin.create(HttpOrigin("http://make.org")))
        .addHeader(Cookie(makeSettings.SecureCookie.name, "invalid")) ~> routeException ~> check {

        status should be(StatusCodes.PreconditionFailed)
        info(headers.mkString("\n"))
        header[`Access-Control-Allow-Origin`] shouldBe defined
        header[`Access-Control-Allow-Origin`].map(_.value) shouldBe Some("http://make.org")
      }
    }
  }

  Feature("get parameters management") {
    Scenario("return get parameters if provided") {
      Get("/testWithParameter").addHeader(`X-Get-Parameters`("foo=bar&baz=bibi")) ~> routeWithParameters ~> check {
        status should be(StatusCodes.OK)
        responseAs[Map[String, String]] should be(Map("foo" -> "bar", "baz" -> "bibi"))
      }
    }

    Scenario("return get parameters when value not provided") {
      Get("/testWithParameter").addHeader(`X-Get-Parameters`("foo")) ~> routeWithParameters ~> check {
        status should be(StatusCodes.OK)
        responseAs[Map[String, String]] should be(Map("foo" -> ""))
      }
    }
  }

  Feature("make trace parameter") {
    Scenario("slugify the makeTrace parameter") {
      Get("/testMakeTrace") ~> routeMakeTrace ~> check {
        status should be(StatusCodes.OK)
        header[`X-Route-Name`].map(_.value) shouldBe Some("test-make-trace")
      }
    }

    Scenario("Header parsing") {
      Get("/testMakeTrace").withHeaders(`X-Make-Custom-Data`("first%3DG%C3%A9nial%2Cother%3D%26%26%26")) ~>
        routeMakeTrace ~>
        check {
          status should be(StatusCodes.OK)
          val context = entityAs[RequestContext]
          context.customData should be(Map("first" -> "Génial", "other" -> "&&&"))
        }
    }
  }

  Feature("auto refresh token if connected") {
    Scenario("not connected") {
      Get("/test") ~> route ~> check {
        val cookiesHttpHeaders: Seq[HttpHeader] = headers.filter(_.is("set-cookie"))
        val cookiesHeaders: Seq[HttpCookie] = cookiesHttpHeaders.map(_.asInstanceOf[`Set-Cookie`].cookie)
        val maybeSecureCookie: Option[HttpCookie] = cookiesHeaders.find(_.name == "cookie-secure")
        val maybeSecureExpirationCookie: Option[HttpCookie] = cookiesHeaders.find(_.name == "cookie-secure-expiration")

        status should be(StatusCodes.OK)

        maybeSecureCookie.isEmpty shouldBe true
        maybeSecureExpirationCookie.isEmpty shouldBe true
      }
    }

    Scenario("connected token refreshed") {
      val firstExpiration = DateHelper.now()

      val newToken = AccessToken(
        token = "new-token",
        refreshToken = Some("new-refresh-token"),
        scope = None,
        lifeSeconds = Some(42L),
        createdAt = new Date(),
        params = Map.empty
      )

      val authInfo = AuthInfo(
        UserRights(
          userId = UserId("user-id"),
          roles = Seq(Role.RoleCitizen),
          availableQuestions = Seq.empty,
          emailVerified = true
        ),
        None,
        None,
        None
      )

      val token = Token(
        accessToken = newToken.token,
        refreshToken = newToken.refreshToken,
        scope = newToken.scope,
        expiresIn = newToken.lifeSeconds.getOrElse(42L).toInt,
        refreshExpiresIn = 9999,
        user = authInfo.user,
        client = client(ClientId("connected token refreshed")),
        createdAt = None,
        updatedAt = None
      )

      when(oauth2DataHandler.refreshIfTokenIsExpired(eqTo("valid-token")))
        .thenReturn(Future.successful(Some(token)))

      when(oauth2DataHandler.findAccessToken(eqTo("valid-token")))
        .thenReturn(Future.successful(Some(newToken.copy(token = "new-token"))))
      when(oauth2DataHandler.findAccessToken(eqTo("new-token")))
        .thenReturn(Future.successful(Some(newToken)))

      when(oauth2DataHandler.findAuthInfoByAccessToken(eqTo(newToken)))
        .thenReturn(Future.successful(Some(authInfo)))

      Get("/test").withHeaders(Cookie(HttpCookiePair(secureCookieConfiguration.name, "valid-token"))) ~> route ~> check {
        val cookiesHttpHeaders: Seq[HttpHeader] = headers.filter(_.is("set-cookie"))
        val cookiesHeaders: Seq[HttpCookie] = cookiesHttpHeaders.map(_.asInstanceOf[`Set-Cookie`].cookie)
        val maybeSecureCookie: Option[HttpCookie] = cookiesHeaders.find(_.name == "cookie-secure")
        val maybeSecureExpirationCookie: Option[HttpCookie] = cookiesHeaders.find(_.name == "cookie-secure-expiration")

        status should be(StatusCodes.OK)

        maybeSecureCookie.isEmpty shouldBe false
        maybeSecureCookie.map(_.value) shouldBe Some(newToken.token)
        maybeSecureExpirationCookie.isEmpty shouldBe false
        maybeSecureExpirationCookie.map(expDate => ZonedDateTime.parse(expDate.value).isAfter(firstExpiration)) shouldBe Some(
          true
        )
      }
    }

    Scenario("connected token not refreshed") {
      when(oauth2DataHandler.refreshIfTokenIsExpired(eqTo("valid-token")))
        .thenReturn(Future.successful(None))
      when(oauth2DataHandler.findAccessToken(eqTo("valid-token")))
        .thenReturn(Future.successful(None))

      Get("/test").withHeaders(Cookie(HttpCookiePair(secureCookieConfiguration.name, "valid-token"))) ~> route ~> check {
        val cookiesHttpHeaders: Seq[HttpHeader] = headers.filter(_.is("set-cookie"))
        val cookiesHeaders: Seq[HttpCookie] = cookiesHttpHeaders.map(_.asInstanceOf[`Set-Cookie`].cookie)
        val maybeSecureCookie: Option[HttpCookie] = cookiesHeaders.find(_.name == "cookie-secure")
        val maybeSecureExpirationCookie: Option[HttpCookie] = cookiesHeaders.find(_.name == "cookie-secure-expiration")

        status should be(StatusCodes.OK)

        maybeSecureCookie.isEmpty shouldBe true
        maybeSecureExpirationCookie.isEmpty shouldBe true
      }
    }

    Scenario("connected using headers with an expired token") {
      val token = "connected using headers with an expired token"

      when(oauth2DataHandler.findAccessToken(eqTo(token)))
        .thenReturn(Future.successful(None))

      Get("/test").withHeaders(Authorization(OAuth2BearerToken(token))) ~> route ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
  }

  Feature("mandatory connection access") {
    Scenario("core only endpoint") {
      val routeUnlogged = sealRoute(checkMandatoryConnectionEndpointAccess(None, EndpointType.CoreOnly) {
        complete(StatusCodes.OK)
      })
      val routeLoggedUnverified = sealRoute(
        checkMandatoryConnectionEndpointAccess(
          Some(
            UserRights(userId = UserId("a"), roles = Seq.empty, availableQuestions = Seq.empty, emailVerified = false)
          ),
          EndpointType.CoreOnly
        ) {
          complete(StatusCodes.OK)
        }
      )
      val routeLoggedVerified = sealRoute(
        checkMandatoryConnectionEndpointAccess(
          Some(
            UserRights(userId = UserId("a"), roles = Seq.empty, availableQuestions = Seq.empty, emailVerified = true)
          ),
          EndpointType.CoreOnly
        ) {
          complete(StatusCodes.OK)
        }
      )

      Get("/") ~> routeUnlogged ~> check {
        status should be(StatusCodes.Forbidden)
      }
      Get("/") ~> routeLoggedUnverified ~> check {
        status should be(StatusCodes.Forbidden)
      }
      Get("/") ~> routeLoggedVerified ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("public endpoint") {
      val routeUnlogged = sealRoute(checkMandatoryConnectionEndpointAccess(None, EndpointType.Public) {
        complete(StatusCodes.OK)
      })
      val routeLoggedUnverified = sealRoute(
        checkMandatoryConnectionEndpointAccess(
          Some(
            UserRights(userId = UserId("a"), roles = Seq.empty, availableQuestions = Seq.empty, emailVerified = false)
          ),
          EndpointType.Public
        ) {
          complete(StatusCodes.OK)
        }
      )
      val routeLoggedVerified = sealRoute(
        checkMandatoryConnectionEndpointAccess(
          Some(
            UserRights(userId = UserId("a"), roles = Seq.empty, availableQuestions = Seq.empty, emailVerified = true)
          ),
          EndpointType.Public
        ) {
          complete(StatusCodes.OK)
        }
      )

      Get("/") ~> routeUnlogged ~> check {
        status should be(StatusCodes.OK)
      }
      Get("/") ~> routeLoggedUnverified ~> check {
        status should be(StatusCodes.OK)
      }
      Get("/") ~> routeLoggedVerified ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("regular endpoint") {
      val routeUnlogged = sealRoute(checkMandatoryConnectionEndpointAccess(None, EndpointType.Regular) {
        complete(StatusCodes.OK)
      })
      val routeLoggedUnverified = sealRoute(
        checkMandatoryConnectionEndpointAccess(
          Some(
            UserRights(userId = UserId("a"), roles = Seq.empty, availableQuestions = Seq.empty, emailVerified = false)
          ),
          EndpointType.Regular
        ) {
          complete(StatusCodes.OK)
        }
      )
      val routeLoggedVerified = sealRoute(
        checkMandatoryConnectionEndpointAccess(
          Some(
            UserRights(userId = UserId("a"), roles = Seq.empty, availableQuestions = Seq.empty, emailVerified = true)
          ),
          EndpointType.Regular
        ) {
          complete(StatusCodes.OK)
        }
      )

      Get("/") ~> routeUnlogged ~> check {
        status should be(StatusCodes.Unauthorized)
      }
      Get("/") ~> routeLoggedUnverified ~> check {
        status should be(StatusCodes.Forbidden)
      }
      Get("/") ~> routeLoggedVerified ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  Feature("extract token") {
    Scenario("no token") {
      Get("/test") ~> tokenRoute ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("token from cookie") {
      Get("/test").withHeaders(Cookie(makeSettings.SecureCookie.name, "token")) ~> tokenRoute ~> check {
        status should be(StatusCodes.OK)
        responseAs[String] should be("token")
      }
    }

    Scenario("Token from headers") {
      Get("/test").withHeaders(Authorization(OAuth2BearerToken("token"))) ~> tokenRoute ~> check {
        status should be(StatusCodes.OK)
        responseAs[String] should be("token")
      }
    }
  }

  Feature("optional token extraction") {
    Scenario("no token") {
      Get("/test") ~> optionalTokenRoute ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    Scenario("token from cookie") {
      Get("/test").withHeaders(Cookie(makeSettings.SecureCookie.name, "token")) ~> optionalTokenRoute ~> check {
        status should be(StatusCodes.OK)
        responseAs[String] should be("token")
      }
    }

    Scenario("Token from headers") {
      Get("/test").withHeaders(Authorization(OAuth2BearerToken("token"))) ~> optionalTokenRoute ~> check {
        status should be(StatusCodes.OK)
        responseAs[String] should be("token")
      }
    }
  }

  Feature("cookieless navigation") {
    val cookieless = Seq(ApplicationName.Backoffice, ApplicationName.ConcertationBeta, ApplicationName.Widget)
    for (app <- cookieless) {
      Scenario(s"cookieless $app") {
        Get("/test").withHeaders(`X-Make-App-Name`(app.value)) ~> route ~> check {
          status should be(StatusCodes.OK)
          headers.count(_.is(`Set-Cookie`.lowercaseName)) shouldBe 0
        }
      }
    }
    for (app <- ApplicationName.values.toSet -- cookieless) {
      Scenario(s"cookiefull $app") {
        Get("/test").withHeaders(`X-Make-App-Name`(app.value)) ~> route ~> check {
          status should be(StatusCodes.OK)
          headers.count(_.is(`Set-Cookie`.lowercaseName)) should be > 0
        }
      }
    }
    Scenario("cookiefull unknown application") {
      Get("/test") ~> route ~> check {
        status should be(StatusCodes.OK)
        headers.count(_.is(`Set-Cookie`.lowercaseName)) should be > 0
      }
    }
  }
}
