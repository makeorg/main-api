package org.make.api.technical

import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.circe._
import org.make.api.extensions.{MailJetConfiguration, MailJetConfigurationComponent}
import org.make.api.technical.auth._
import org.make.api.technical.mailjet.{MailJetApi, MailJetEvent}
import org.make.api.user.PersistentUserServiceComponent
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FeatureSpec, Matchers}

class MailJetApiTest
    extends FeatureSpec
    with Matchers
    with ScalatestRouteTest
    with MockitoSugar
    with MailJetApi
    with EventBusServiceComponent
    with MailJetConfigurationComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with PersistentUserServiceComponent
    with PersistentTokenServiceComponent
    with PersistentClientServiceComponent
    with OauthTokenGeneratorComponent
    with ShortenedNames {

  override val eventBusService: EventBusService = mock[EventBusService]
  override val mailJetConfiguration: MailJetConfiguration = mock[MailJetConfiguration]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val persistentTokenService: PersistentTokenService = mock[PersistentTokenService]
  override val persistentClientService: PersistentClientService = mock[PersistentClientService]
  override val readExecutionContext: EC = ECGlobal
  override val writeExecutionContext: EC = ECGlobal
  override val oauthTokenGenerator: OauthTokenGenerator = mock[OauthTokenGenerator]

  when(mailJetConfiguration.basicAuthLogin).thenReturn("login")
  when(mailJetConfiguration.basicAuthPassword).thenReturn("password")
  when(idGenerator.nextId()).thenReturn("some-id")

  val routes: Route = Route.seal(mailJetRoutes)

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
