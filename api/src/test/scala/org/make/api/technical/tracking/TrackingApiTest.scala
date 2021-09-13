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

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import grizzled.slf4j.Logger
import org.make.api.MakeApiTestBase
import org.make.api.demographics.{
  ActiveDemographicsCardService,
  ActiveDemographicsCardServiceComponent,
  DemographicsCardService,
  DemographicsCardServiceComponent
}
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.api.technical._
import org.make.api.technical.monitoring.{MonitoringService, MonitoringServiceComponent}
import org.make.core.ValidationError
import org.make.core.demographics.{ActiveDemographicsCard, ActiveDemographicsCardId, DemographicsCardId}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.mockito.Mockito.{clearInvocations, verifyNoInteractions}
import org.slf4j.{Logger => Underlying}

import scala.concurrent.Future

class TrackingApiTest
    extends MakeApiTestBase
    with DefaultTrackingApiComponent
    with ActiveDemographicsCardServiceComponent
    with DemographicsCardServiceComponent
    with ShortenedNames
    with MakeAuthenticationDirectives
    with MonitoringServiceComponent
    with QuestionServiceComponent {

  override val monitoringService: MonitoringService = mock[MonitoringService]
  override val questionService: QuestionService = mock[QuestionService]
  override val activeDemographicsCardService: ActiveDemographicsCardService = mock[ActiveDemographicsCardService]
  override val demographicsCardService: DemographicsCardService = mock[DemographicsCardService]

  private val underlying = mock[Underlying]
  when(underlying.isWarnEnabled).thenReturn(true)
  doNothing.when(underlying).warn(any)
  override val logger: Logger = new Logger(underlying)

  val questionId: QuestionId = QuestionId("valid")

  when(questionService.getQuestion(QuestionId("unknown"))).thenReturn(Future.successful(None))
  when(questionService.getQuestion(questionId))
    .thenReturn(
      Future.successful(
        Some(Question(questionId, "slug", NonEmptyList.one(Country("FR")), Language("fr"), "question", None, None))
      )
    )

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

    testBackofficeLogs("user", tokenCitizen, StatusCodes.Forbidden, accepted = false)
    testBackofficeLogs("moderator", tokenModerator, StatusCodes.NoContent, accepted = true)
    testBackofficeLogs("admin", tokenAdmin, StatusCodes.NoContent, accepted = true)
    testBackofficeLogs("superadmin", tokenSuperAdmin, StatusCodes.NoContent, accepted = true)

  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    clearInvocations(eventBusService)
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

  Feature("demographics") {
    Scenario("valid demographics") {
      DemographicsTrackingRequest.validValues.foreach {
        case (name, values) =>
          values.zipWithIndex.foreach {
            case (value, idx) =>
              val request =
                s"""
                |{
                |  "demographic": "$name",
                |  "value": "$value",
                |  "questionId": "${questionId.value}",
                |  "source": "core",
                |  "country": "FR",
                |  "parameters": {},
                |  "autoSubmit": ${idx % 2 == 0}
                |}
                |""".stripMargin
              val entity = HttpEntity(ContentTypes.`application/json`, request)
              Post("/tracking/demographics", entity) ~> routes ~> check {
                status should be(StatusCodes.NoContent)
              }
          }
      }
    }

    Scenario("skipped demographic") {
      DemographicsTrackingRequest.validValues.foreach {
        case (name, _) =>
          val request =
            s"""
               |{
               |  "demographic": "$name",
               |  "value": "${DemographicsTrackingRequest.skipped}",
               |  "questionId": "${questionId.value}",
               |  "source": "core",
               |  "country": "FR",
               |  "parameters": {},
               |  "autoSubmit": false
               |}
               |""".stripMargin
          val entity = HttpEntity(ContentTypes.`application/json`, request)
          Post("/tracking/demographics", entity) ~> routes ~> check {
            status should be(StatusCodes.NoContent)
          }
      }
    }

    Scenario("invalid question") {
      val request =
        """
          |{
          |  "demographic": "age",
          |  "value": "16-24",
          |  "questionId": "unknown",
          |  "source": "core",
          |  "country": "FR",
          |  "parameters": {}
          |}
          |""".stripMargin
      val entity = HttpEntity(ContentTypes.`application/json`, request)
      Post("/tracking/demographics", entity) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("invalid demographic") {
      val request =
        """
          |{
          |  "demographic": "unknown",
          |  "value": "16-24",
          |  "questionId": "valid",
          |  "source": "core",
          |  "country": "FR",
          |  "parameters": {}
          |}
          |""".stripMargin
      val entity = HttpEntity(ContentTypes.`application/json`, request)
      Post("/tracking/demographics", entity) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("demographics values should not be mixed") {
      val flattenedValues: Seq[(String, String)] = DemographicsTrackingRequest.validValues.toSeq.flatMap {
        case (name, values) => values.map((name, _))
      }
      DemographicsTrackingRequest.validValues.keys.foreach { demographic =>
        flattenedValues.filter(_._1 != demographic).map(_._2).foreach { value =>
          val request =
            s"""
              |{
              |  "demographic": "$demographic",
              |  "value": "$value",
              |  "questionId": "valid",
              |  "source": "core",
              |  "country": "FR",
              |  "parameters": {}
              |}
              |""".stripMargin
          val entity = HttpEntity(ContentTypes.`application/json`, request)
          Post("/tracking/demographics", entity) ~> routes ~> check {
            status should be(StatusCodes.BadRequest)
          }
        }
      }

    }
  }

  Feature("demographics-v2") {
    val demoCardParameters =
      """[{"label":"label1","value":"16-24"},{"label":"label2","value":"24-42"},{"label":"label3","value":"42-70"}]"""
    when(demographicsCardService.get(DemographicsCardId("id-age"))).thenReturn(
      Future.successful(
        Some(demographicsCard(DemographicsCardId("id-age"), parameters = demoCardParameters, dataType = "age"))
      )
    )
    Scenario("invalid question") {
      val request =
        """
          |{
          |  "demographicsCardId": "id-age",
          |  "value": "16-24",
          |  "questionId": "unknown",
          |  "source": "core",
          |  "country": "FR",
          |  "parameters": {},
          |  "token": "token"
          |}
          |""".stripMargin
      val entity = HttpEntity(ContentTypes.`application/json`, request)
      Post("/tracking/demographics-v2", entity) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size shouldBe 1
        errors.head.field shouldBe "questionId"
      }
    }

    Scenario("invalid demographics card") {
      when(demographicsCardService.get(DemographicsCardId("unknown"))).thenReturn(Future.successful(None))
      val request =
        """
          |{
          |  "demographicsCardId": "unknown",
          |  "value": "16-24",
          |  "questionId": "valid",
          |  "source": "core",
          |  "country": "FR",
          |  "parameters": {},
          |  "token": "token"
          |}
          |""".stripMargin
      val entity = HttpEntity(ContentTypes.`application/json`, request)
      Post("/tracking/demographics-v2", entity) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size shouldBe 1
        errors.head.field shouldBe "demographicsCardId"
      }
    }

    Scenario("bad requests") {
      when(questionService.getQuestion(QuestionId("no-active-cards")))
        .thenReturn(Future.successful(Some(question(QuestionId("no-active-cards")))))
      when(
        activeDemographicsCardService
          .list(
            eqTo(None),
            eqTo(None),
            eqTo(None),
            eqTo(None),
            eqTo(Some(QuestionId("no-active-cards"))),
            eqTo(Some(DemographicsCardId("id-age")))
          )
      ).thenReturn(Future.successful(Seq.empty))
      when(
        demographicsCardService
          .isTokenValid(eqTo("invalid-token"), eqTo(DemographicsCardId("id-age")), eqTo(QuestionId("no-active-cards")))
      ).thenReturn(false)
      val request =
        """
          |{
          |  "demographicsCardId": "id-age",
          |  "value": "invalid-value",
          |  "questionId": "no-active-cards",
          |  "source": "core",
          |  "country": "ZZ",
          |  "parameters": {},
          |  "token": "token"
          |}
          |""".stripMargin
      val entity = HttpEntity(ContentTypes.`application/json`, request)
      Post("/tracking/demographics-v2", entity) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size shouldBe 4
        errors.map(_.field) should contain theSameElementsAs Seq("token", "demographicsCardId", "country", "value")
      }
    }

    Scenario("successful tracking") {
      when(
        activeDemographicsCardService
          .list(
            eqTo(None),
            eqTo(None),
            eqTo(None),
            eqTo(None),
            eqTo(Some(questionId)),
            eqTo(Some(DemographicsCardId("id-age")))
          )
      ).thenReturn(
        Future.successful(
          Seq(ActiveDemographicsCard(ActiveDemographicsCardId("active-age"), DemographicsCardId("id-age"), questionId))
        )
      )
      when(demographicsCardService.isTokenValid(eqTo("token"), eqTo(DemographicsCardId("id-age")), eqTo(questionId)))
        .thenReturn(true)
      val request =
        """
          |{
          |  "demographicsCardId": "id-age",
          |  "value": "24-42",
          |  "questionId": "valid",
          |  "source": "core",
          |  "country": "FR",
          |  "parameters": {},
          |  "token": "token"
          |}
          |""".stripMargin
      val entity = HttpEntity(ContentTypes.`application/json`, request)
      Post("/tracking/demographics-v2", entity) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
        verify(eventBusService).publish(any[DemographicEvent])
      }
    }
  }

  Feature("concertation tracking") {
    Scenario("valid concertation") {
      val request =
        s"""
          |{
          |  "eventName": "homepage-display",
          |  "context": {
          |    "sessionId": "session-id",
          |    "concertationSlug": "slug",
          |    "language": "fr",
          |    "country": "FR",
          |    "location": "homepage",
          |    "getParameters": {}
          |  },
          |  "parameters": {}
          |}
          |""".stripMargin
      val entity = HttpEntity(ContentTypes.`application/json`, request)
      Post("/tracking/concertation", entity) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }

    Scenario("bad request") {
      val request =
        s"""
           |{
           |  "eventName": "homepage-display",
           |  "context": null,
           |  "parameters": {}
           |}
           |""".stripMargin
      val entity = HttpEntity(ContentTypes.`application/json`, request)
      Post("/tracking/concertation", entity) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }
}
