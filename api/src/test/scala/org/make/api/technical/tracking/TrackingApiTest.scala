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

package org.make.api.technical.tracking

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import grizzled.slf4j.Logger
import org.make.api.MakeApiTestBase
import org.make.api.technical._
import org.make.api.technical.monitoring.{MonitoringService, MonitoringServiceComponent}
import org.mockito.Mockito.{clearInvocations, verifyNoInteractions}
import org.slf4j.{Logger => Underlying}

class TrackingApiTest
    extends MakeApiTestBase
    with DefaultTrackingApiComponent
    with ShortenedNames
    with MakeAuthenticationDirectives
    with MonitoringServiceComponent {

  override val monitoringService: MonitoringService = mock[MonitoringService]

  private val underlying = mock[Underlying]
  when(underlying.isWarnEnabled).thenReturn(true)
  doNothing.when(underlying).warn(any)
  override val logger: Logger = new Logger(underlying)

  val routes: Route = sealRoute(trackingApi.routes)

  val backofficeLog: String =
    """
      |{
      |  "level": "warn",
      |  "message": "something happened"
      |}
      |""".stripMargin

  Feature("backoffice logging") {

    def testBackofficeLogs(as: String, token: String, expected: StatusCode, accepted: Boolean): Unit = {
      Scenario(s"as $as") {
        Post("/tracking/backoffice/logs")
          .withEntity(HttpEntity(ContentTypes.`application/json`, backofficeLog))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(expected)
          if (accepted) {
            verify(underlying).warn("something happened")
            clearInvocations(underlying)
          } else {
            verifyNoInteractions(underlying)
          }
        }
      }
    }

    testBackofficeLogs("user", tokenCitizen, StatusCodes.Forbidden, false)
    testBackofficeLogs("moderator", tokenModerator, StatusCodes.NoContent, true)
    testBackofficeLogs("admin", tokenAdmin, StatusCodes.NoContent, true)
    testBackofficeLogs("superadmin", tokenSuperAdmin, StatusCodes.NoContent, true)

  }

  val frontRequest: String =
    """
      |{
      |  "eventType": "test1-evenType",
      |  "eventName": "test1-eventName",
      |  "eventParameters": {
      |    "test1-key1": "test1-value1",
      |    "test1-key2": "test1-value2",
      |    "test1-key3": "test1-value3"
      |  }
      |}
      """.stripMargin

  val failedFrontRequest: String =
    """
      |{
      |  "badParam": "string"
      |}
      """.stripMargin

  Feature("generate front event") {
    Scenario("valid request") {
      Post("/tracking/front", HttpEntity(ContentTypes.`application/json`, frontRequest)) ~>
        routes ~> check {
        status should be(StatusCodes.NoContent)
        verify(eventBusService).publish(any[TrackingEventWrapper])
      }
    }

    Scenario("failed request") {
      Post("/tracking/front", HttpEntity(ContentTypes.`application/json`, failedFrontRequest)) ~>
        routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }
}
