package org.make.api.technical.tracking

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.technical._
import org.make.api.technical.auth.MakeAuthentication
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify

class TrackingApiTest extends MakeApiTestBase with TrackingApi with ShortenedNames with MakeAuthentication {

  val routes: Route = sealRoute(trackingRoutes)

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

  feature("generate front event") {
    scenario("valid request") {
      Post("/tracking/front", HttpEntity(ContentTypes.`application/json`, frontRequest)) ~>
        routes ~> check {
        status should be(StatusCodes.NoContent)
        verify(eventBusService).publish(any[TrackingEventWrapper])
      }
    }

    scenario("failed request") {
      Post("/tracking/front", HttpEntity(ContentTypes.`application/json`, failedFrontRequest)) ~>
        routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }
}
