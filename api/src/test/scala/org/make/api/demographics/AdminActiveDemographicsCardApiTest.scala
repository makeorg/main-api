/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.demographics

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.core.ValidationError
import org.make.core.demographics._
import org.make.core.question.QuestionId

import scala.concurrent.Future

class AdminActiveDemographicsCardApiTest
    extends MakeApiTestBase
    with DefaultAdminActiveDemographicsCardApiComponent
    with ActiveDemographicsCardServiceComponent
    with DemographicsCardServiceComponent
    with QuestionServiceComponent {

  override val activeDemographicsCardService: ActiveDemographicsCardService = mock[ActiveDemographicsCardService]
  override val demographicsCardService: DemographicsCardService = mock[DemographicsCardService]
  override val questionService: QuestionService = mock[QuestionService]

  val routes: Route = sealRoute(adminActiveDemographicsCardApi.routes)

  when(activeDemographicsCardService.get(any)).thenAnswer { adcId: ActiveDemographicsCardId =>
    Future.successful(Some(ActiveDemographicsCard(adcId, DemographicsCardId("card-id"), QuestionId("question-id"))))
  }
  when(activeDemographicsCardService.get(eqTo(ActiveDemographicsCardId("fake"))))
    .thenReturn(Future.successful(None))
  Feature("create an activeDemographicsCard") {
    when(activeDemographicsCardService.create(any, any)).thenAnswer {
      (cardId: DemographicsCardId, questionId: QuestionId) =>
        Future.successful(ActiveDemographicsCard(ActiveDemographicsCardId("id"), cardId, questionId))
    }

    val validRequest = """{"demographicsCardId": "card", "questionId": "question"}"""

    Scenario("unauthorize unauthenticated") {
      Post("/admin/active-demographics-cards").withEntity(HttpEntity(ContentTypes.`application/json`, validRequest)) ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen & moderator") {
      for (token <- Seq(tokenCitizen, tokenModerator)) {
        Post("/admin/active-demographics-cards")
          .withEntity(HttpEntity(ContentTypes.`application/json`, validRequest))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.Forbidden)
        }
      }
    }

    Scenario("allow authenticated admin") {
      when(demographicsCardService.get(DemographicsCardId("card")))
        .thenReturn(Future.successful(Some(demographicsCard(DemographicsCardId("card")))))
      when(questionService.getQuestion(QuestionId("question")))
        .thenReturn(Future.successful(Some(question(QuestionId("question")))))
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/active-demographics-cards")
          .withEntity(HttpEntity(ContentTypes.`application/json`, validRequest))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.Created)
        }
      }
    }

    Scenario("bad requests") {
      when(demographicsCardService.get(DemographicsCardId("card-id")))
        .thenReturn(Future.successful(Some(demographicsCard(DemographicsCardId("card-id")))))
      when(demographicsCardService.get(DemographicsCardId("fake"))).thenReturn(Future.successful(None))
      when(questionService.getQuestion(QuestionId("fake"))).thenReturn(Future.successful(None))
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/active-demographics-cards")
          .withEntity(
            HttpEntity(
              ContentTypes.`application/json`,
              """{"demographicsCardId": "fake", "questionId": "questionId"}"""
            )
          )
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size shouldBe 1
          errors.head.field should be("demographicsCardId")
        }
      }
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/active-demographics-cards")
          .withEntity(
            HttpEntity(ContentTypes.`application/json`, """{"demographicsCardId": "card-id", "questionId": "fake"}""")
          )
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
          val errors = entityAs[Seq[ValidationError]]
          errors.size shouldBe 1
          errors.head.field should be("questionId")
        }
      }
    }
  }

  Feature("read an activeDemographicsCard") {
    val adcId = ActiveDemographicsCardId("hello-adc-id")

    Scenario("unauthorize unauthenticated") {
      Get("/admin/active-demographics-cards/hello-adc-id") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen & moderator") {
      for (token <- Seq(tokenCitizen, tokenModerator)) {
        Get("/admin/active-demographics-cards/hello-adc-id")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.Forbidden)
        }
      }
    }

    Scenario("allow authenticated admin on existing activeDemographicsCard") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/active-demographics-cards/hello-adc-id")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val activeDemographicsCard: ActiveDemographicsCardResponse = entityAs[ActiveDemographicsCardResponse]
          activeDemographicsCard.id should be(adcId)
        }
      }
    }

    Scenario("not found and allow authenticated admin on a non existing activeDemographicsCard") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/active-demographics-cards/fake")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }
  }

  Feature("delete a activeDemographicsCard") {
    val adcId = ActiveDemographicsCardId("delete-id")

    when(activeDemographicsCardService.delete(eqTo(adcId)))
      .thenReturn(Future.unit)

    Scenario("unauthorize unauthenticated") {
      Delete("/admin/active-demographics-cards/delete-id") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen & moderator") {
      for (token <- Seq(tokenCitizen, tokenModerator)) {
        Delete("/admin/active-demographics-cards/delete-id")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.Forbidden)
        }
      }
    }

    Scenario("allow authenticated admin on existing activeDemographicsCard") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Delete("/admin/active-demographics-cards/delete-id")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NoContent)
        }
      }
    }

    Scenario("not found and allow authenticated admin on a non existing activeDemographicsCard") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/active-demographics-cards/fake")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }
  }
}
