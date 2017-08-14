package org.make.api

import java.time.LocalDate

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.knutwalker.akka.http.support.CirceHttpSupport
import io.circe.generic.auto._
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.core.{CirceFormatters, ValidationError}

class RejectionsTest
    extends MakeUnitTest
    with ScalatestRouteTest
    with Directives
    with CirceHttpSupport
    with MakeApiTestUtils
    with IdGeneratorComponent
    with CirceFormatters {

  override val idGenerator: IdGenerator = mock[IdGenerator]

  val route: Route = sealRoute(post {
    path("test") {
      decodeRequest {
        entity(as[TestRequest]) { _ =>
          complete(StatusCodes.OK)
        }
      }
    }
  })

  feature("bad request rejections") {

    scenario("an invalid json should return validation errors") {
      val invalidJson = "not a json"
      Post("/test", HttpEntity(ContentTypes.`application/json`, invalidJson)) ~> route ~> check {
        status should be(StatusCodes.BadRequest)
        entityAs[Seq[ValidationError]].size should be(1)
      }
    }

    scenario("a missing field should be returned as a ValidationError") {
      val missingFields = "{}"
      Post("/test", HttpEntity(ContentTypes.`application/json`, missingFields)) ~> route ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(1)
        errors.head.field should be("field1")
      }
    }

    scenario("a type mismatch should be returned as a ValidationError") {
      val typeMismatch =
        """{
          |  "field1": "test",
          |  "field2": "test",
          |  "field3": "1970-01-01"
          |}""".stripMargin

      Post("/test", HttpEntity(ContentTypes.`application/json`, typeMismatch)) ~> route ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(1)
        errors.head.field should be("field2")
      }
    }

    scenario("a type mismatch with custom converter should be returned as a ValidationError") {
      val typeMismatch =
        """{
          |  "field1": "test",
          |  "field2": 1,
          |  "field3": "not a date"
          |}""".stripMargin

      Post("/test", HttpEntity(ContentTypes.`application/json`, typeMismatch)) ~> route ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(1)
        errors.head.field should be("field3")
      }
    }
  }
}

final case class TestRequest(field1: String, field2: Int, field3: LocalDate) {}
