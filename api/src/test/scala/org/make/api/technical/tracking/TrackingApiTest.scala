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

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.technical._
import org.make.api.technical.auth.MakeAuthentication
import org.make.api.technical.monitoring.{MonitoringService, MonitoringServiceComponent}

class TrackingApiTest
    extends MakeApiTestBase
    with DefaultTrackingApiComponent
    with ShortenedNames
    with MakeAuthentication
    with MonitoringServiceComponent {

  override val monitoringService: MonitoringService = mock[MonitoringService]

  val routes: Route = sealRoute(trackingApi.routes)

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
