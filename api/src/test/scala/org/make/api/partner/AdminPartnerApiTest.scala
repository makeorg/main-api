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

package org.make.api.partner

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.core.partner.{Partner, PartnerId, PartnerKind}
import org.make.core.question.QuestionId

import scala.concurrent.Future
import org.make.core.technical.Pagination.Start

class AdminPartnerApiTest extends MakeApiTestBase with DefaultAdminPartnerApiComponent with PartnerServiceComponent {

  override val partnerService: PartnerService = mock[PartnerService]

  val routes: Route = sealRoute(adminPartnerApi.routes)

  val partner: Partner = Partner(
    partnerId = PartnerId("partner-id"),
    name = "partner name",
    logo = Some("partner logo"),
    link = None,
    organisationId = None,
    partnerKind = PartnerKind.Founder,
    questionId = QuestionId("question-id"),
    weight = 20f
  )

  Feature("post partner") {
    Scenario("post partner unauthenticated") {
      Post("/admin/partners").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("post partner without admin rights") {
      Post("/admin/partners")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("post partner with admin rights") {

      when(partnerService.createPartner(any[CreatePartnerRequest])).thenReturn(Future.successful(partner))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/partners")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
            | "name": "partner name",
            | "logo": "partner logo",
            | "partnerKind": "FOUNDER",
            | "link": "http://link.com",
            | "questionId": "question-id",
            | "weight": "20.0"
            |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.Created
        }
      }
    }

    Scenario("post scenario without logo nor link") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/partners")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
          | "name": "partner name",
          | "partnerKind": "FOUNDER",
          | "questionId": "question-id",
          | "weight": "20.0"
          |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.Created
        }
      }
    }
  }

  Feature("put partner") {
    Scenario("put partner unauthenticated") {
      Put("/admin/partners/partner-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("put partner without admin rights") {
      Put("/admin/partners/partner-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("put partner with admin rights") {

      when(partnerService.updatePartner(eqTo(PartnerId("partner-id")), any[UpdatePartnerRequest]))
        .thenReturn(Future.successful(Some(partner)))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/partners/partner-id")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
            | "name": "update name",
            | "logo": "logo",
            | "partnerKind": "FOUNDER",
            | "link": "http://link.com",
            | "weight": "20.0"
            |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }

    Scenario("put partner without logo nor link") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/partners/partner-id")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
          | "name": "update name",
          | "partnerKind": "FOUNDER",
          | "weight": "20.0"
          |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }

    Scenario("put non existent partner") {
      when(partnerService.updatePartner(eqTo(PartnerId("not-found")), any[UpdatePartnerRequest]))
        .thenReturn(Future.successful(None))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/partners/not-found")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
          | "name": "update name",
          | "logo": "logo",
          | "partnerKind": "FOUNDER",
          | "link": "http://link.com",
          | "weight": "20.0"
          |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }
  }

  Feature("get partners") {
    Scenario("get partners unauthenticated") {
      Get("/admin/partners") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("get partners without admin rights") {
      Get("/admin/partners")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("get partners with admin rights") {

      when(
        partnerService.find(
          questionId = None,
          organisationId = None,
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          partnerKind = None
        )
      ).thenReturn(Future.successful(Seq(partner)))
      when(partnerService.count(questionId = None, organisationId = None, partnerKind = None))
        .thenReturn(Future.successful(1))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/partners")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }
  }

  Feature("get partner") {
    Scenario("get partner unauthenticated") {
      Get("/admin/partners/partner-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("get partners without admin rights") {
      Get("/admin/partners/partner-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("get partner with admin rights") {

      when(partnerService.getPartner(eqTo(PartnerId("partner-id"))))
        .thenReturn(Future.successful(Some(partner)))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/partners/partner-id")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }

    Scenario("get non existent partner") {

      when(partnerService.getPartner(eqTo(PartnerId("not-found"))))
        .thenReturn(Future.successful(None))

      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/partners/not-found")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }
  }

}
