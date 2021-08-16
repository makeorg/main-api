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

package org.make.api.widget

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.core.DateHelper
import org.make.core.widget.{Source, SourceId}

import scala.collection.immutable.Seq
import scala.concurrent.Future

class AdminSourceApiTest extends MakeApiTestBase with DefaultAdminSourceApiComponent with SourceServiceComponent {

  override val sourceService: SourceService = mock[SourceService]

  val routes: Route = sealRoute(adminSourceApi.routes)

  private def source(id: SourceId, name: String, source: String) =
    Source(id, name, source, DateHelper.now(), DateHelper.now(), defaultAdminUser.userId)

  Feature("list sources") {

    when(sourceService.list(any, any, any, any, eqTo(Some("le canard"))))
      .thenReturn(Future.successful(Seq(source(SourceId("id"), "Le Canard Enchaîné", "le_canard"))))

    Scenario("unauthorize unauthenticated") {
      Get("/admin/sources") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Get("/admin/sources").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Get("/admin/sources").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/sources?name=le%20canard")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val sources = entityAs[Seq[AdminSourceResponse]]
          sources should be(Seq(AdminSourceResponse(SourceId("id"), "Le Canard Enchaîné", "le_canard")))
        }
      }
    }
  }

  Feature("get source by id") {

    val existing = source(SourceId("existing"), "L'Humanité'", "huma")

    when(sourceService.get(existing.id)).thenReturn(Future.successful(Some(existing)))
    when(sourceService.get(SourceId("nonexisting"))).thenReturn(Future.successful(None))

    Scenario("unauthorize unauthenticated") {
      Get("/admin/sources/existing") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Get("/admin/sources/existing").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Get("/admin/sources/existing").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin on existing source") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/sources/existing")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val source = entityAs[AdminSourceResponse]
          source should be(AdminSourceResponse(SourceId("existing"), "L'Humanité'", "huma"))
        }
      }
    }

    Scenario("not found and allow authenticated admin on a non existing source") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/sources/nonexisting")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }

  }

  Feature("create a source") {
    val created = source(id = SourceId("id"), name = "name", source = "source")

    when(sourceService.findBySource(any)).thenReturn(Future.successful(None))
    when(sourceService.findBySource("existing")).thenReturn(Future.successful(Some(created.copy(source = "existing"))))
    when(sourceService.create(any, any, any)).thenReturn(Future.successful(created))

    Scenario("unauthorize unauthenticated") {
      Post("/admin/sources") ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Post("/admin/sources")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Post("/admin/sources")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/sources")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                    |  "name" : "name",
                                                                    |  "source" : "source"
                                                                    |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.Created)
        }
      }
    }

    Scenario("forbid duplicate sources") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/sources")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                    |  "name" : "name",
                                                                    |  "source" : "existing"
                                                                    |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
        }
      }
    }
  }

  Feature("update a source") {
    val sourceBeforeUpdate = source(id = SourceId("id"), name = "name", source = "source")
    val updatedSource = source(id = SourceId("id"), name = "updated name", source = "updated source")

    when(sourceService.get(eqTo(sourceBeforeUpdate.id))).thenReturn(Future.successful(Some(sourceBeforeUpdate)))

    when(sourceService.findBySource("existing"))
      .thenReturn(Future.successful(Some(source(id = SourceId("other"), name = "name", source = "existing"))))

    when(sourceService.update(eqTo(sourceBeforeUpdate.id), eqTo("updated name"), eqTo("updated source"), any))
      .thenReturn(Future.successful(Some(updatedSource)))
    when(sourceService.update(eqTo(SourceId("fake-source")), any, any, any)).thenReturn(Future.successful(None))

    Scenario("unauthorize unauthenticated") {
      Put("/admin/sources/id") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Put("/admin/sources/id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Put("/admin/sources/id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin on existing source") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/sources/id")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                | "name" : "updated name",
                |  "source" : "updated source"
                |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val source = entityAs[AdminSourceResponse]
          source should be(AdminSourceResponse(updatedSource.id, updatedSource.name, updatedSource.source))
        }
      }
    }

    Scenario("forbid duplicates") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/sources/id")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                | "name" : "updated name",
                |  "source" : "existing"
                |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.BadRequest)
        }
      }
    }

    Scenario("not found and allow authenticated admin on a non existing source") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/sources/fake-source")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                |  "name" : "name",
                |  "source" : "updated"
                |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }

  }
}
