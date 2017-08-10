package org.make.api.technical.auth

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.{MakeApiTestUtils, MakeUnitTest}
import org.mockito.{ArgumentMatchers, Mockito}

import scala.concurrent.Future
import scalaoauth2.provider.TokenEndpoint

class AuthenticationApiTest
    extends MakeUnitTest
    with MakeApiTestUtils
    with MakeAuthenticationDirectives
    with MakeDataHandlerComponent
    with AuthenticationApi
    with MakeSettingsComponent
    with IdGeneratorComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val tokenEndpoint: TokenEndpoint = mock[TokenEndpoint]
  override lazy val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override def makeSettings: MakeSettings = mock[MakeSettings]

  Mockito
    .when(oauth2DataHandler.removeTokenByAccessToken(ArgumentMatchers.any[String]))
    .thenReturn(Future.successful(1))
  Mockito
    .when(oauth2DataHandler.removeTokenByAccessToken(ArgumentMatchers.eq("FAULTY_TOKEN")))
    .thenReturn(Future.successful(0))

  val routes: Route = sealRoute(authenticationRoutes)

  feature("logout user by deleting its token") {
    scenario("best case") {
      Given("a valid access token")
      val token: String =
        """
          |{
          |  "access_token": "VALID"
          |}
        """.stripMargin
      val tokenEntity = HttpEntity(ContentTypes.`application/json`, token)

      When("logout is called")
      val logoutRoute: RouteTestResult = Post("/logout").withEntity(tokenEntity) ~> routes

      Then("the service must delete at least one row")
      logoutRoute ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("do not fail on faulty token") {
      Given("a invalid access token")
      val token: String =
        """
          |{
          |  "access_token": "FAULTY_TOKEN"
          |}
        """.stripMargin
      val tokenEntity = HttpEntity(ContentTypes.`application/json`, token)

      When("logout is called")
      val logoutRoute: RouteTestResult = Post("/logout").withEntity(tokenEntity) ~> routes

      Then("the service must delete at least one row")
      logoutRoute ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }
}
