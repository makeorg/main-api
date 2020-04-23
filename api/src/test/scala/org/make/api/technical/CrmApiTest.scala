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

package org.make.api.technical

import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.testkit.TestDuration
import io.circe._
import org.make.api.MakeApiTestBase
import org.make.api.extensions.{MailJetConfiguration, MailJetConfigurationComponent}
import org.make.api.technical.auth._
import org.make.api.technical.crm._
import org.make.api.technical.job.JobActor.Protocol.Response.JobAcceptance
import org.make.core.session.VisitorId
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class CrmApiTest
    extends MakeApiTestBase
    with DefaultCrmApiComponent
    with CrmServiceComponent
    with MailJetConfigurationComponent
    with ShortenedNames
    with MakeAuthentication {

  override val mailJetConfiguration: MailJetConfiguration = mock[MailJetConfiguration]
  override val crmService: CrmService = mock[CrmService]

  when(mailJetConfiguration.basicAuthLogin).thenReturn("login")
  when(mailJetConfiguration.basicAuthPassword).thenReturn("password")
  when(mailJetConfiguration.campaignApiKey).thenReturn("campaignapikey")
  when(mailJetConfiguration.campaignSecretKey).thenReturn("campaignsecretkey")
  when(mailJetConfiguration.hardBounceListId).thenReturn("hardbouncelistid")
  when(mailJetConfiguration.unsubscribeListId).thenReturn("unsubscribelistid")
  when(mailJetConfiguration.optInListId).thenReturn("optinlistid")
  when(mailJetConfiguration.userListBatchSize).thenReturn(100)
  when(mailJetConfiguration.url).thenReturn("http://fakeurl.com")
  when(idGenerator.nextId()).thenReturn("some-id")
  when(idGenerator.nextVisitorId()).thenReturn(VisitorId("some-visitor-id"))

  val routes: Route = sealRoute(crmApi.routes)

  val requestMultipleEvents: String =
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

  val requestSingleEvent: String =
    """
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
      |   }
      |
      """.stripMargin

  feature("callback requests") {
    scenario("json decoding") {
      val maybeJson = jawn.parse(requestMultipleEvents)

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
        MailJetBaseEvent(
          event = "sent",
          time = Some(1433333949L),
          messageId = Some(19421777835146490L),
          email = "api@mailjet.com",
          campaignId = Some(7257),
          contactId = Some(4),
          customCampaign = Some(""),
          customId = Some("helloworld"),
          payload = Some("")
        )
      )

    }
  }

  feature("callback api") {
    scenario("should refuse service if no credentials are supplied with a 401 return code") {
      Post("/technical/mailjet") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
    scenario("should refuse service if bad credentials are supplied with a 401 return code") {
      Post("/technical/mailjet").withHeaders(Authorization(BasicHttpCredentials("fake", "fake"))) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
    scenario("should refuse service if credentials are supplied but no content with a 400 return code") {
      Post("/technical/mailjet").withHeaders(Authorization(BasicHttpCredentials("login", "password"))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
    scenario("should send parsed events in event bus with multiple events") {
      Post("/technical/mailjet", HttpEntity(ContentTypes.`application/json`, requestMultipleEvents))
        .withHeaders(Authorization(BasicHttpCredentials("login", "password"))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        verify(eventBusService, times(2)).publish(any[AnyRef])
      }
    }
    scenario("should send parsed events in event bus with single event") {
      Post("/technical/mailjet", HttpEntity(ContentTypes.`application/json`, requestSingleEvent))
        .withHeaders(Authorization(BasicHttpCredentials("login", "password"))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        verify(eventBusService, times(3)).publish(any[AnyRef])
      }
    }
  }

  feature("crm synchro") {
    when(crmService.synchronizeContactsWithCrm()).thenReturn(Future.successful(JobAcceptance(true)))
    scenario("admin triggers sync") {
      implicit val timeout: RouteTestTimeout = RouteTestTimeout(15.seconds.dilated)

      Post("/technical/crm/synchronize", HttpEntity(ContentTypes.`application/json`, requestSingleEvent))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.Accepted)
        verify(crmService, times(1)).synchronizeContactsWithCrm()
      }
    }
    scenario("moderator triggers sync") {
      Post("/technical/crm/synchronize", HttpEntity(ContentTypes.`application/json`, requestSingleEvent))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    scenario("user triggers sync") {
      Post("/technical/crm/synchronize", HttpEntity(ContentTypes.`application/json`, requestSingleEvent))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    scenario("non connected triggers sync") {
      Post("/technical/crm/synchronize", HttpEntity(ContentTypes.`application/json`, requestSingleEvent)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
  }
}
