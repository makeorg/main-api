package org.make.api.technical

import akka.http.javadsl.model.headers.Origin
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.make.api.MakeApiTestBase
import org.make.api.technical.auth._
import org.make.core.RequestContext
import org.mockito.Mockito.when

import scala.concurrent.Future

class MakeDirectivesTest
    extends MakeApiTestBase
    with ScalatestRouteTest
    with OauthTokenGeneratorComponent
    with ShortenedNames
    with MakeAuthentication {

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
        reject(MalformedRequestContentRejection("http", new Exception("fake exception")))
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
      makeOperation("test Make trace!") { requestContext: RequestContext =>
        complete(StatusCodes.OK)
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

  feature("session id management") {

    scenario("new session id if no cookie is sent") {
      Get("/test") ~> route ~> check {
        val cookiesHttpHeaders: Seq[HttpHeader] = headers.filter(_.is("set-cookie"))
        val cookiesHeaders: Seq[HttpCookie] = cookiesHttpHeaders.map(_.asInstanceOf[`Set-Cookie`].cookie)
        val maybeSessionCookie: Option[HttpCookie] = cookiesHeaders.find(_.name == "make-session-id")

        status should be(StatusCodes.OK)

        maybeSessionCookie.isEmpty shouldBe false
        maybeSessionCookie.get.secure shouldBe false
        maybeSessionCookie.get.httpOnly shouldBe true
      }
    }

    scenario("no cookie if session id is sent") {
      Get("/test").withHeaders(Cookie("make-session-id" -> "123")) ~> route ~> check {
        val cookiesHttpHeaders: Seq[HttpHeader] = headers.filter(_.is("set-cookie"))
        val cookiesHeaders: Seq[HttpCookie] = cookiesHttpHeaders.map(_.asInstanceOf[`Set-Cookie`].cookie)
        status should be(StatusCodes.OK)
        !cookiesHeaders.exists(_.name == "make-session-id") shouldBe true
      }
    }

  }

  feature("visitor id management") {

    scenario("new visitor id if no cookie is sent") {
      Get("/test") ~> route ~> check {
        val cookiesHttpHeaders: Seq[HttpHeader] = headers.filter(_.is("set-cookie"))
        val cookiesHeaders: Seq[HttpCookie] = cookiesHttpHeaders.map(_.asInstanceOf[`Set-Cookie`].cookie)
        val maybeSessionCookie: Option[HttpCookie] = cookiesHeaders.find(_.name == "make-visitor-id")

        status should be(StatusCodes.OK)

        maybeSessionCookie.isEmpty shouldBe false
        maybeSessionCookie.get.secure shouldBe false
        maybeSessionCookie.get.httpOnly shouldBe true
      }
    }

    scenario("no cookie if session id is sent") {
      Get("/test").withHeaders(Cookie("make-session-id" -> "123")) ~> route ~> check {
        val cookiesHttpHeaders: Seq[HttpHeader] = headers.filter(_.is("set-cookie"))
        val cookiesHeaders: Seq[HttpCookie] = cookiesHttpHeaders.map(_.asInstanceOf[`Set-Cookie`].cookie)
        status should be(StatusCodes.OK)
        !cookiesHeaders.exists(_.name == "make-visitor-id") shouldBe false
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

  feature("get parameters management") {
    scenario("return get parameters if provided") {
      Get("/testWithParameter").addHeader(GetParametersHeader("foo=bar&baz=bibi")) ~> routeWithParameters ~> check {
        status should be(StatusCodes.OK)
        responseAs[Map[String, String]] should be(Map("foo" -> "bar", "baz" -> "bibi"))
      }
    }

    scenario("return get parameters when value not provided") {
      Get("/testWithParameter").addHeader(GetParametersHeader("foo")) ~> routeWithParameters ~> check {
        status should be(StatusCodes.OK)
        responseAs[Map[String, String]] should be(Map("foo" -> ""))
      }
    }
  }

  feature("make trace parameter") {
    scenario("slugify the makeTrace parameter") {
      Get("/testMakeTrace") ~> routeMakeTrace ~> check {
        status should be(StatusCodes.OK)
        header[RouteNameHeader].map(_.value) shouldBe Some("test-make-trace")
      }
    }
  }

}
