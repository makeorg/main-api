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

import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.auth.UserRights
import org.make.core.partner.{Partner, PartnerId, PartnerKind}
import org.make.core.question.QuestionId
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito.when
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class AdminPartnerApiTest
    extends MakeApiTestBase
    with DefaultAdminPartnerApiComponent
    with PartnerServiceComponent
    with MakeDataHandlerComponent {

  override val partnerService: PartnerService = mock[PartnerService]

  val routes: Route = sealRoute(moderationPartnerApi.routes)

  val validAccessToken = "my-valid-access-token"
  val adminToken = "my-admin-access-token"
  val moderatorToken = "my-moderator-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)
  private val adminAccessToken = AccessToken(adminToken, None, None, Some(1234567890L), tokenCreationDate)
  private val moderatorAccessToken = AccessToken(moderatorToken, None, None, Some(1234567890L), tokenCreationDate)

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))
  when(oauth2DataHandler.findAccessToken(adminToken)).thenReturn(Future.successful(Some(adminAccessToken)))
  when(oauth2DataHandler.findAccessToken(moderatorToken)).thenReturn(Future.successful(Some(moderatorAccessToken)))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(accessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(
              userId = UserId("user-citizen"),
              roles = Seq(RoleCitizen),
              availableQuestions = Seq.empty,
              emailVerified = true
            ),
            None,
            Some("user"),
            None
          )
        )
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(
              UserId("user-admin"),
              roles = Seq(RoleAdmin),
              availableQuestions = Seq.empty,
              emailVerified = true
            ),
            None,
            None,
            None
          )
        )
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(
      Future
        .successful(
          Some(
            AuthInfo(
              UserRights(
                UserId("user-moderator"),
                roles = Seq(RoleModerator),
                availableQuestions = Seq.empty,
                emailVerified = true
              ),
              None,
              None,
              None
            )
          )
        )
    )

  val partner: Partner = Partner(
    partnerId = PartnerId("partner-id"),
    name = "partner name",
    logo = Some("partner logo"),
    link = None,
    organisationId = None,
    partnerKind = PartnerKind.Founder,
    questionId = QuestionId("question-id"),
    weight = 20F
  )

  feature("post partner") {
    scenario("post partner unauthenticated") {
      Post("/admin/partners").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("post partner without admin rights") {
      Post("/admin/partners")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("post partner with admin rights") {

      when(partnerService.createPartner(any[CreatePartnerRequest])).thenReturn(Future.successful(partner))

      Post("/admin/partners")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
            | "name": "partner name",
            | "logo": "partner logo",
            | "partnerKind": "FOUNDER",
            | "link": "http://link.com",
            | "questionId": "question-id",
            | "weight": "20.0"
            |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Created
      }
    }

    scenario("post scenario with wrong request") {
      Post("/admin/partners")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
          | "name": "partner name",
          | "partnerKind": "FOUNDER",
          | "questionId": "question-id",
          | "weight": "20.0"
          |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  feature("put partner") {
    scenario("put partner unauthenticated") {
      Put("/admin/partners/partner-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("put partner without admin rights") {
      Put("/admin/partners/partner-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("put partner with admin rights") {

      when(partnerService.updatePartner(matches(PartnerId("partner-id")), any[UpdatePartnerRequest]))
        .thenReturn(Future.successful(Some(partner)))

      Put("/admin/partners/partner-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
            | "name": "update name",
            | "logo": "logo",
            | "partnerKind": "FOUNDER",
            | "link": "http://link.com",
            | "weight": "20.0"
            |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    scenario("put partner with wrong request") {
      Put("/admin/partners/partner-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
          | "name": "update name",
          | "partnerKind": "FOUNDER",
          | "weight": "20.0"
          |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    scenario("put non existent partner") {
      when(partnerService.updatePartner(matches(PartnerId("not-found")), any[UpdatePartnerRequest]))
        .thenReturn(Future.successful(None))

      Put("/admin/partners/not-found")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
          | "name": "update name",
          | "logo": "logo",
          | "partnerKind": "FOUNDER",
          | "link": "http://link.com",
          | "weight": "20.0"
          |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  feature("get partners") {
    scenario("get partners unauthenticated") {
      Get("/admin/partners") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("get partners without admin rights") {
      Get("/admin/partners")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("get partners with admin rights") {

      when(
        partnerService.find(
          questionId = None,
          organisationId = None,
          start = 0,
          end = None,
          sort = None,
          order = None,
          partnerKind = None
        )
      ).thenReturn(Future.successful(Seq(partner)))
      when(partnerService.count(questionId = None, organisationId = None, partnerKind = None))
        .thenReturn(Future.successful(1))

      Get("/admin/partners")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  feature("get partner") {
    scenario("get partner unauthenticated") {
      Get("/admin/partners/partner-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("get partners without admin rights") {
      Get("/admin/partners/partner-id")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("get partner with admin rights") {

      when(partnerService.getPartner(matches(PartnerId("partner-id"))))
        .thenReturn(Future.successful(Some(partner)))

      Get("/admin/partners/partner-id")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    scenario("get non existent partner") {

      when(partnerService.getPartner(matches(PartnerId("not-found"))))
        .thenReturn(Future.successful(None))

      Get("/admin/partners/not-found")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

}
