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

package org.make.api

import java.time.LocalDate

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.make.api.technical.auth.MakeAuthentication
import org.make.core.{CirceFormatters, ValidationError}

class RejectionsTest
    extends MakeApiTestBase
    with ScalatestRouteTest
    with Directives
    with ErrorAccumulatingCirceSupport
    with CirceFormatters
    with MakeAuthentication {

  val route: Route = sealRoute(handleExceptions(MakeApi.exceptionHandler("test", "123")) {
    post {
      path("test") {
        decodeRequest {
          entity(as[TestRequest]) { _ =>
            complete(StatusCodes.OK)
          }
        }
      }
    }
  })

  Feature("bad request rejections") {

    Scenario("an invalid json should return validation errors") {
      val invalidJson = "not a json"
      Post("/test", HttpEntity(ContentTypes.`application/json`, invalidJson)) ~> route ~> check {
        status should be(StatusCodes.BadRequest)
        logger.debug(responseEntity.toString)
        entityAs[Seq[ValidationError]].size should be(1)
      }
    }

    Scenario("a missing field should be returned as a ValidationError") {
      val missingFields = "{}"
      Post("/test", HttpEntity(ContentTypes.`application/json`, missingFields)) ~> route ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(3)
        errors.head.field should be("field1")
      }
    }

    Scenario("a type mismatch should be returned as a ValidationError") {
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

    Scenario("a type mismatch with custom converter should be returned as a ValidationError") {
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

object TestRequest extends CirceFormatters {
  implicit val encoder: Encoder[TestRequest] = deriveEncoder[TestRequest]
  implicit val decoder: Decoder[TestRequest] = deriveDecoder[TestRequest]
}
