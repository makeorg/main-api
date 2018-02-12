package org.make.api.technical.tracking

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestUtils
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}

import scala.concurrent.duration.Duration

class TrackingApiTest
    extends MakeApiTestUtils
    with TrackingApi
    with EventBusServiceComponent
    with IdGeneratorComponent
    with ShortenedNames
    with MakeSettingsComponent {

  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val eventBusService: EventBusService = mock[EventBusService]
  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  private val oauthConfiguration = mock[makeSettings.Oauth.type]

  when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  when(sessionCookieConfiguration.isSecure).thenReturn(false)
  when(sessionCookieConfiguration.lifetime).thenReturn(Duration("20 minutes"))
  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(makeSettings.Oauth).thenReturn(oauthConfiguration)
  when(idGenerator.nextId()).thenReturn("next-id")

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
