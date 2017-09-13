package org.make.api.technical

import akka.http.javadsl.model.headers.Origin
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Origin`, `Set-Cookie`, Cookie, HttpOrigin}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.make.api.MakeApiTestUtils
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical.auth._
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FeatureSpec, Matchers}

import scala.concurrent.Future

class MakeDirectivesTest
    extends FeatureSpec
    with Matchers
    with ScalatestRouteTest
    with MockitoSugar
    with MakeDirectives
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with OauthTokenGeneratorComponent
    with ShortenedNames
    with MakeApiTestUtils
    with MakeSettingsComponent {

  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauthTokenGenerator: OauthTokenGenerator = mock[OauthTokenGenerator]
  override val makeSettings: MakeSettings = mock[MakeSettings]

  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  private val oauthConfiguration = mock[makeSettings.Oauth.type]

  when(makeSettings.frontUrl).thenReturn("http://make.org")
  when(makeSettings.authorizedCorsUri).thenReturn(Seq("http://make.org"))
  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(makeSettings.Oauth).thenReturn(oauthConfiguration)
  when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  when(sessionCookieConfiguration.isSecure).thenReturn(false)
  when(idGenerator.nextId()).thenReturn("some-id")

  val route: Route = sealRoute(get {
    path("test") {
      makeTrace("test") { _ =>
        complete(StatusCodes.OK)
      }
    }
  })

  val routeRejection: Route = sealRoute(get {
    path("test") {
      makeTrace("test") { _ =>
        reject(MalformedRequestContentRejection("http", new Exception("fake exception")))
      }
    }
  })

  val routeException: Route = sealRoute(get {
    path("test") {
      makeTrace("test") { _ =>
        throw new Exception("fake exception")
      }
    }
  })

  feature("session id management") {

    scenario("new session id if no cookie is sent") {
      Get("/test") ~> route ~> check {
        status should be(StatusCodes.OK)
        header[`Set-Cookie`] shouldBe defined
        header[`Set-Cookie`].get.cookie.name shouldBe "make-session-id"
        header[`Set-Cookie`].get.cookie.secure shouldBe true
        header[`Set-Cookie`].get.cookie.httpOnly shouldBe true
      }
    }

    scenario("no cookie if session id is sent") {
      Get("/test").withHeaders(Cookie("make-session-id" -> "123")) ~> route ~> check {
        status should be(StatusCodes.OK)
        header[`Set-Cookie`] shouldBe empty
      }
    }

  }

  feature("external id management") {
    scenario("return external request id if provided") {
      Get("/test").withHeaders(ExternalIdHeader("test-id")) ~> route ~> check {
        status should be(StatusCodes.OK)
        header[ExternalIdHeader].map(_.value) should be(Some("test-id"))
      }
    }

    scenario("provide a random external id if none is provided") {
      Get("/test") ~> route ~> check {
        status should be(StatusCodes.OK)
        header[ExternalIdHeader] shouldBe defined
      }
    }
  }

  feature("request id management") {
    scenario("return a request id") {
      Get("/test") ~> route ~> check {
        status should be(StatusCodes.OK)
        header[RequestIdHeader] shouldBe defined
      }
    }
  }

  feature("request time management") {
    scenario("return the request time as a long") {
      Get("/test") ~> route ~> check {
        status should be(StatusCodes.OK)
        header[RequestTimeHeader] shouldBe defined
        // Ensure value can be converted to long
        header[RequestTimeHeader].get.value.toLong
      }
    }
  }

  feature("route name management") {
    scenario("return the route name header") {
      Get("/test") ~> route ~> check {
        status should be(StatusCodes.OK)
        header[RouteNameHeader] shouldBe defined
        header[RouteNameHeader].map(_.value) shouldBe Some("test")
      }
    }
  }

  feature("value providers") {
    trait StringProvider {
      def provide: Future[String]
    }
    val provider: StringProvider = mock[StringProvider]

    val route = sealRoute(get {
      pathEndOrSingleSlash {
        provideAsync(provider.provide)(complete(_))
      }
    })

    scenario("normal providing") {
      when(provider.provide).thenReturn(Future.successful("oki doki"))

      Get("/") ~> route ~> check {
        status should be(StatusCodes.OK)
        responseAs[String] should be("oki doki")
      }
    }

    scenario("failed providers") {
      when(provider.provide).thenReturn(Future.failed(new IllegalArgumentException("fake")))

      Get("/") ~> route ~> check {
        status should be(StatusCodes.InternalServerError)
      }
    }

  }

  feature("not found value providers") {
    trait StringProvider {
      def provide: Future[Option[String]]
    }
    val provider: StringProvider = mock[StringProvider]

    val route = sealRoute(get {
      pathEndOrSingleSlash {
        provideAsyncOrNotFound(provider.provide)(complete(_))
      }
    })

    scenario("some providing") {
      when(provider.provide).thenReturn(Future.successful(Some("oki doki")))

      Get("/") ~> route ~> check {
        status should be(StatusCodes.OK)
        responseAs[String] should be("oki doki")
      }
    }

    scenario("none providing") {
      when(provider.provide).thenReturn(Future.successful(None))

      Get("/") ~> route ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("failed providers") {
      when(provider.provide).thenReturn(Future.failed(new IllegalArgumentException("fake")))

      Get("/") ~> route ~> check {
        status should be(StatusCodes.InternalServerError)
      }
    }

  }
  feature("access control header") {
    scenario("return header allow all origins") {
      Get("/test").addHeader(Origin.create(HttpOrigin("http://make.org"))) ~> route ~> check {
        status should be(StatusCodes.OK)
        info(headers.mkString("\n"))
        header[`Access-Control-Allow-Origin`] shouldBe defined
        header[`Access-Control-Allow-Origin`].map(_.value) shouldBe Some("http://make.org")
      }
    }

    scenario("rejection returns header allow all origins") {
      Get("/test").addHeader(Origin.create(HttpOrigin("http://make.org"))) ~> routeRejection ~> check {
        status should be(StatusCodes.BadRequest)
        header[`Access-Control-Allow-Origin`] shouldBe defined
        header[`Access-Control-Allow-Origin`].map(_.value) shouldBe Some("http://make.org")
      }
    }

    scenario("exception returns header allow all origins") {
      Get("/test").addHeader(Origin.create(HttpOrigin("http://make.org"))) ~> routeException ~> check {
        status should be(StatusCodes.InternalServerError)
        info(headers.mkString("\n"))
        header[`Access-Control-Allow-Origin`] shouldBe defined
        header[`Access-Control-Allow-Origin`].map(_.value) shouldBe Some("http://make.org")
      }
    }
  }

}
