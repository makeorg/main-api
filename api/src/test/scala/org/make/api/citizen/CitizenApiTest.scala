package org.make.api.citizen

import java.time.{LocalDate, ZonedDateTime}

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.generic.auto._
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.{
  MakeDataHandlerComponent,
  TokenServiceComponent
}
import org.make.core.CirceFormatters
import org.make.core.citizen.{Citizen, CitizenId}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Future
import scalaoauth2.provider.TokenEndpoint

class CitizenApiTest
    extends FlatSpec
    with Matchers
    with MockitoSugar
    with CirceFormatters
    with ScalatestRouteTest
    with PersistentCitizenServiceComponent
    with CitizenServiceComponent
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with TokenServiceComponent
    with CitizenApi {

  override def testConfig: Config = {

    val config =
      """
        |akka.test.timefactor=5.0
      """.stripMargin

    ConfigFactory.parseString(config)
  }

  override val persistentCitizenService: PersistentCitizenService =
    mock[PersistentCitizenService]
  override val idGenerator: IdGenerator = new UUIDIdGenerator
  override val oauth2DataHandler: MakeDataHandler =
    new MakeDataHandler()(ECGlobal)
  override val tokenEndpoint: TokenEndpoint = TokenEndpoint

  override def readExecutionContext: EC = ECGlobal
  override def writeExecutionContext: EC = ECGlobal

  override val tokenService: TokenService = mock[TokenService]
  override val citizenService: CitizenService = mock[CitizenService]

  val token1 = Token(
    id = "user-1",
    refreshToken = "refresh-1",
    citizenId = CitizenId("citizen-1"),
    scope = "all",
    creationDate = ZonedDateTime.now(),
    validityDurationSeconds = 999999,
    parameters = ""
  )
  val token2 = Token(
    id = "user-2",
    refreshToken = "refresh-2",
    citizenId = CitizenId("citizen-2"),
    scope = "all",
    creationDate = ZonedDateTime.now(),
    validityDurationSeconds = 999999,
    parameters = ""
  )

  when(tokenService.getToken(ArgumentMatchers.eq("invalid-auth")))
    .thenReturn(Future.successful(None))
  when(tokenService.getToken(ArgumentMatchers.eq("user-1")))
    .thenReturn(Future.successful(Some(token1)))
  when(tokenService.getToken(ArgumentMatchers.eq("user-2")))
    .thenReturn(Future.successful(Some(token2)))

  // seal routes so that error management gets called in tests
  val allRoutes: Route = Route.seal(citizenRoutes)

  private val citizen = Citizen(
    citizenId = CitizenId("citizen-1"),
    email = "test@test.com",
    dateOfBirth = LocalDate.parse("1970-01-01"),
    firstName = "testFirstName",
    lastName = "testLastName"
  )

  val maybeCitizenInTheFuture: Future[Option[Citizen]] =
    Future.successful(Some(citizen))
  val citizenInTheFuture: Future[Citizen] = Future.successful(citizen)

  "get citizen" should "return a json citizen if citizen exists" in {

    when(
      persistentCitizenService.get(ArgumentMatchers.eq(CitizenId("citizen-1")))
    ).thenReturn(maybeCitizenInTheFuture)
    when(
      citizenService.getCitizen(ArgumentMatchers.eq(CitizenId("citizen-1")))
    ).thenReturn(maybeCitizenInTheFuture)

    Get("/citizen/citizen-1").withHeaders(
      Authorization(OAuth2BearerToken("user-1"))
    ) ~> allRoutes ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[Citizen].citizenId.value should be("citizen-1")
    }
  }

  it should "return a 403 if user doesn't have the right to view resource" in {

    Get("/citizen/1234").withHeaders(
      Authorization(OAuth2BearerToken("user-1"))
    ) ~> allRoutes ~> check {
      status shouldEqual StatusCodes.Forbidden
    }

  }

  "register citizen" should "fail with a status code 400 if date is invalid" in {

    Post(
      "/citizen",
      HttpEntity(
        ContentTypes.`application/json`,
        """
          |{
          |  "email": "youppy@yopmail.com",
          |  "password": "toto-fait-du-vélo",
          |  "dateOfBirth": "something difference from a date",
          |  "firstName": "aaaa",
          |  "lastName": "bbbb"
          |}
        """.stripMargin
      )
    ) ~> allRoutes ~> check {
      status shouldEqual StatusCodes.BadRequest
    }

  }

  it should "succeed if everything is valid" in {

    when(
      citizenService.register(
        ArgumentMatchers.eq("youppy@yopmail.com"),
        ArgumentMatchers.eq(LocalDate.parse("1970-01-01")),
        ArgumentMatchers.eq("aaaa"),
        ArgumentMatchers.eq("bbbb"),
        ArgumentMatchers.eq("toto-fait-du-vélo")
      )
    ).thenReturn(citizenInTheFuture)

    Post(
      "/citizen",
      HttpEntity(
        ContentTypes.`application/json`,
        """
          |{
          |  "email": "youppy@yopmail.com",
          |  "password": "toto-fait-du-vélo",
          |  "dateOfBirth": "1970-01-01",
          |  "firstName": "aaaa",
          |  "lastName": "bbbb"
          |}
        """.stripMargin
      )
    ) ~> allRoutes ~> check {
      status shouldEqual StatusCodes.OK
    }

  }

}
