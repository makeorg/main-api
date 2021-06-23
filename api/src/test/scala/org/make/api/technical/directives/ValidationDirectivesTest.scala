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

package org.make.api.technical.directives

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import org.make.api.{MakeApi, MakeUnitTest}
import org.make.core.{ValidationError, Validator}

class ValidationDirectivesTest
    extends MakeUnitTest
    with ErrorAccumulatingCirceSupport
    with ScalatestRouteTest
    with ValidationDirectives
    with Directives {

  val route: Route = Route.seal(handleRejections(MakeApi.rejectionHandler) {
    handleExceptions(MakeApi.exceptionHandler("test", "test")) {
      get {
        path("invalid") {
          validate(
            "invalid",
            Validator[String](_ => Invalid(NonEmptyList.one(ValidationError("invalid", "invalid", None))))
          ) {
            complete(StatusCodes.OK)
          }
        } ~
          path("valid") {
            validate("valid", Validator[String](Valid(_))) {
              complete(StatusCodes.OK)
            }
          }
      }
    }
  })

  Feature("validation") {
    Scenario("valid input") {
      Get("/valid") ~> route ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("invalid input") {
      Get("/invalid") ~> route ~> check {
        status should be(StatusCodes.BadRequest)
        responseAs[Seq[ValidationError]] should be(Seq(ValidationError("invalid", "invalid", None)))
      }
    }
  }
}
