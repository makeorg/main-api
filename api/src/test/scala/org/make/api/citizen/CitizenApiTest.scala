package org.make.api.citizen

import java.time.LocalDate

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Route
import org.make.api.IdGeneratorComponent
import org.make.core.citizen.{Citizen, CitizenId}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Future

class CitizenApiTest extends FlatSpec with Matchers with MockitoSugar
  with ScalatestRouteTest
  with PersistentCitizenServiceComponent
  with IdGeneratorComponent
  with CitizenApi {

  override val citizenService: PersistentCitizenService = mock[PersistentCitizenService]
  override val idGenerator: IdGenerator = mock[IdGenerator]

  // seal routes so that error management gets called in tests
  val allRoutes: Route = Route.seal(citizenRoutes)

  val citizenInTheFuture: Future[Option[Citizen]] = Future.successful(Option(
    Citizen(
      citizenId = CitizenId("1234"),
      email = "test@test.com",
      dateOfBirth = LocalDate.parse("1970-01-01"),
      firstName = "testFirstName",
      lastName = "testLastName"
    )
  ))


  "get citizen" should "return a json citizen if citizen exists" in {

    when(citizenService.getCitizen(ArgumentMatchers.eq(CitizenId("1234"))))
      .thenReturn(
        citizenInTheFuture
      )

    Get("/citizen/1234") ~> allRoutes ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[Citizen].citizenId.value should be("1234")
    }
  }


  it should "return a 404 if citizen doesn't exist" in {
    when(citizenService.getCitizen(any(classOf[CitizenId])))
      .thenReturn(Future.successful(None))

    Get("/citizen/1234") ~> allRoutes ~> check {
      status shouldEqual StatusCodes.NotFound
    }

  }

  "register citizen" should "fail with a status code 400 if date is invalid" in {

    Post("/citizen",
      HttpEntity(ContentTypes.`application/json`,
        """
          |{
          |  "email": "youppy@yopmail.com",
          |  "dateOfBirth": "something difference from a date",
          |  "firstName": "aaaa",
          |  "lastName": "bbbb"
          |}
        """.stripMargin)) ~> allRoutes ~> check {
      status shouldEqual StatusCodes.BadRequest
    }

  }

  it should "succeed if everything is valid" in {

    when(citizenService.register(any(classOf[String]), any(classOf[LocalDate]), any(classOf[String]), any(classOf[String])))
      .thenReturn(citizenInTheFuture)

    Post("/citizen",
      HttpEntity(ContentTypes.`application/json`,
        """
          |{
          |  "email": "youppy@yopmail.com",
          |  "dateOfBirth": "1970-01-01",
          |  "firstName": "aaaa",
          |  "lastName": "bbbb"
          |}
        """.stripMargin)) ~> allRoutes ~> check {
      status shouldEqual StatusCodes.OK
    }

  }

}
