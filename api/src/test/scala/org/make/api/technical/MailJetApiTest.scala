package org.make.api.technical

import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import io.circe._
import org.make.api.MakeApiTestUtils
import org.make.api.extensions.{
  MailJetConfiguration,
  MailJetConfigurationComponent,
  MakeSettings,
  MakeSettingsComponent
}
import org.make.api.technical.auth._
import org.make.api.technical.mailjet.{MailJetApi, MailJetEvent}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._

class MailJetApiTest
    extends MakeApiTestUtils
    with MailJetApi
    with MakeDataHandlerComponent
    with EventBusServiceComponent
    with MailJetConfigurationComponent
    with IdGeneratorComponent
    with ShortenedNames
    with MakeSettingsComponent {

  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val mailJetConfiguration: MailJetConfiguration = mock[MailJetConfiguration]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val eventBusService: EventBusService = mock[EventBusService]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]

  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  private val oauthConfiguration = mock[makeSettings.Oauth.type]

  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(makeSettings.Oauth).thenReturn(oauthConfiguration)
  when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  when(sessionCookieConfiguration.isSecure).thenReturn(false)

  when(mailJetConfiguration.basicAuthLogin).thenReturn("login")
  when(mailJetConfiguration.basicAuthPassword).thenReturn("password")
  when(makeSettings.frontUrl).thenReturn("http://make.org")
  when(idGenerator.nextId()).thenReturn("some-id")

  val routes: Route = sealRoute(mailJetRoutes)

  val request: String =
    """
      |[
      |   {
      |      "event": "sent",
      |      "time": 1433333949,
      |      "MessageID": 19421777835146490,
      |      "email": "api@mailjet.com",
      |      "mj_campaign_id": 7257,
      |      "mj_contact_id": 4,
      |      "customcampaign": "",
      |      "mj_message_id": "19421777835146490",
      |      "smtp_reply": "sent (250 2.0.0 OK 1433333948 fa5si855896wjc.199 - gsmtp)",
      |      "CustomID": "helloworld",
      |      "Payload": ""
      |   },
      |   {
      |      "event": "sent",
      |      "time": 1433333949,
      |      "MessageID": 19421777835146491,
      |      "email": "api@mailjet.com",
      |      "mj_campaign_id": 7257,
      |      "mj_contact_id": 4,
      |      "customcampaign": "",
      |      "mj_message_id": "19421777835146491",
      |      "smtp_reply": "sent (250 2.0.0 OK 1433333948 fa5si855896wjc.199 - gsmtp)",
      |      "CustomID": "helloworld",
      |      "Payload": ""
      |   }
      |]
      |
      """.stripMargin

  feature("callback requests") {
    scenario("json decoding") {
      val maybeJson = jawn.parse(request)

      val parseResult = maybeJson match {
        case Right(json) => json.as[Seq[MailJetEvent]]
        case Left(e)     => fail("unable to parse json", e)
      }

      val events = parseResult match {
        case Right(seq) => seq
        case Left(e)    => fail("unable to decode json", e)
      }

      events.size should be(2)

      events.head should be(
        MailJetEvent(
          event = "sent",
          time = Some(1433333949L),
          messageId = Some(19421777835146490L),
          email = "api@mailjet.com",
          campaignId = Some(7257),
          contactId = Some(4),
          customCampaign = Some(""),
          stringMessageId = Some("19421777835146490"),
          smtpReply = Some("sent (250 2.0.0 OK 1433333948 fa5si855896wjc.199 - gsmtp)"),
          customId = Some("helloworld"),
          payload = Some("")
        )
      )

    }
  }

  feature("callback api") {
    scenario("should refuse service if no credentials are supplied with a 401 return code") {
      Post("/mailjet") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
    scenario("should refuse service if bad credentials are supplied with a 401 return code") {
      Post("/mailjet").withHeaders(Authorization(BasicHttpCredentials("fake", "fake"))) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
    scenario("should refuse service if credentials are supplied but no content with a 400 return code") {
      Post("/mailjet").withHeaders(Authorization(BasicHttpCredentials("login", "password"))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
    scenario("should send parsed events in event bus") {
      Post("/mailjet", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(BasicHttpCredentials("login", "password"))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        verify(eventBusService, times(2)).publish(any[AnyRef])
      }
    }
  }

}
