package org.make.api.technical

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{`Set-Cookie`, Cookie}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.make.api.technical.auth._
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FeatureSpec, Matchers}

class MakeDirectivesTest
    extends FeatureSpec
    with Matchers
    with ScalatestRouteTest
    with MockitoSugar
    with MakeDirectives
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with OauthTokenGeneratorComponent
    with ShortenedNames {

  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauthTokenGenerator: OauthTokenGenerator = mock[OauthTokenGenerator]

  Mockito.when(idGenerator.nextId()).thenReturn("some-id")

  val route: Route = Route.seal(get {
    path("test") {
      makeTrace("test") {
        complete(StatusCodes.OK)
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

}
