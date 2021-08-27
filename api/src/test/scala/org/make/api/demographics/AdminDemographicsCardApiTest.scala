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
import org.make.core.demographics.DemographicsCard.Layout
import org.make.core.demographics.{DemographicsCard, DemographicsCardId}
import org.make.core.reference.Language
import org.make.core.{DateHelper, ValidationError}

import java.time.ZonedDateTime
import scala.collection.immutable.Seq
import scala.concurrent.Future

class AdminDemographicsCardApiTest
    extends MakeApiTestBase
    with DefaultAdminDemographicsCardApiComponent
    with DemographicsCardServiceComponent {

  override val demographicsCardService: DemographicsCardService = mock[DemographicsCardService]

  val routes: Route = sealRoute(adminDemographicsCardApi.routes)

  private def demographicsCard(
    id: DemographicsCardId,
    name: String = "Demo name",
    layout: Layout = Layout.Select,
    dataType: String = "demo",
    language: Language = Language("fr"),
    title: String = "Demo title",
    parameters: String = """[{"label":"option1", "value":"value1"}]""",
    createdAt: ZonedDateTime = DateHelper.now(),
    updatedAt: ZonedDateTime = DateHelper.now()
  ): DemographicsCard =
    DemographicsCard(
      id = id,
      name = name,
      layout = layout,
      dataType = dataType,
      language = language,
      title = title,
      parameters = parameters,
      createdAt = createdAt,
      updatedAt = updatedAt
    )

  Feature("list demographicsCards") {

    val card = demographicsCard(DemographicsCardId("id-age-fr"))
    when(demographicsCardService.list(any, any, any, any, eqTo(Some(Language("fr"))), eqTo(Some("age"))))
      .thenReturn(Future.successful(Seq(card)))

    when(demographicsCardService.count(eqTo(Some(Language("fr"))), eqTo(Some("age")))).thenReturn(Future.successful(1))

    Scenario("unauthorize unauthenticated") {
      Get("/admin/demographics-cards") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Get("/admin/demographics-cards").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Get("/admin/demographics-cards").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/demographics-cards?language=fr&dataType=age")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val demographicsCards = entityAs[Seq[AdminDemographicsCardResponse]]
          demographicsCards should be(Seq(AdminDemographicsCardResponse(card)))
        }
      }
    }
  }

  Feature("get demographicsCard by id") {

    val card = demographicsCard(DemographicsCardId("demo-id"))

    when(demographicsCardService.get(card.id)).thenReturn(Future.successful(Some(card)))
    when(demographicsCardService.get(DemographicsCardId("fake"))).thenReturn(Future.successful(None))

    Scenario("unauthorize unauthenticated") {
      Get("/admin/demographics-cards/demo-id") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Get("/admin/demographics-cards/demo-id").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Get("/admin/demographics-cards/demo-id").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin on existing demographicsCard") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/demographics-cards/demo-id")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val demographicsCard = entityAs[AdminDemographicsCardResponse]
          demographicsCard should be(AdminDemographicsCardResponse(card))
        }
      }
    }

    Scenario("not found and allow authenticated admin on a non existing demographicsCard") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/demographics-cards/fake")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }

  }

  Feature("create a demographicsCard") {
    val created = demographicsCard(
      id = DemographicsCardId("id-create"),
      name = "Demo name create",
      layout = Layout.ThreeColumnsRadio,
      dataType = "create",
      language = Language("fr"),
      title = "Demo title create",
      parameters = """[{"label":"Option create", "value":"value-create"}]"""
    )

    when(
      demographicsCardService.create(
        eqTo("Demo name create"),
        eqTo(Layout.ThreeColumnsRadio),
        eqTo("create"),
        eqTo(Language("fr")),
        eqTo("Demo title create"),
        eqTo("""[{"label":"Option create","value":"value-create"}]""")
      )
    ).thenReturn(Future.successful(created))

    Scenario("unauthorize unauthenticated") {
      Post("/admin/demographics-cards") ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Post("/admin/demographics-cards")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Post("/admin/demographics-cards")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated admin") {
      Post("/admin/demographics-cards")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated super admin") {
      Post("/admin/demographics-cards")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{
                                                                    |  "name": "Demo name create",
                                                                    |  "layout": "ThreeColumnsRadio",
                                                                    |  "dataType": "create",
                                                                    |  "language": "fr",
                                                                    |  "title": "Demo title create",
                                                                    |  "parameters": [{"label":"Option create", "value":"value-create"}]
                                                                    |}""".stripMargin
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenSuperAdmin))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }

    Scenario("bad requests") {
      Post("/admin/demographics-cards")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{
                                                                    |  "name": "Demo name create above 256 characters. 1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890",
                                                                    |  "layout": "OneColumnRadio",
                                                                    |  "dataType": "not a slug",
                                                                    |  "language": "fr",
                                                                    |  "title": "Demo title create above 64 characters. 123456789012345678901234567890",
                                                                    |  "parameters": [
                                                                    |    {"label":"Option create above 64 characters. 123456789012345678901234567890", "value":"value-create"},
                                                                    |    {"label":"too much", "value":"value-create"},
                                                                    |    {"label":"options", "value":"value-create"},
                                                                    |    {"label":"for this", "value":"value-create"},
                                                                    |    {"label":"type of layout", "value":"value-create"}
                                                                    |  ]
                                                                    |}""".stripMargin
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenSuperAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(5)
        errors.map(_.field) should contain theSameElementsAs Seq(
          "name",
          "dataType",
          "title",
          "parameters",
          "parameters"
        )
      }

      Post("/admin/demographics-cards")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{
                                                                    |  "name": "Demo name",
                                                                    |  "layout": "fake",
                                                                    |  "dataType": "data-type",
                                                                    |  "language": "fr",
                                                                    |  "title": "Demo title",
                                                                    |  "parameters": [{"label":"Option create", "value":"value-create"}]
                                                                    |}""".stripMargin
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenSuperAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(1)
        errors.head.field shouldBe "layout"
      }

    }
  }

  Feature("update a demographicsCard") {
    val updated = demographicsCard(
      id = DemographicsCardId("id-update"),
      name = "Demo name update",
      layout = Layout.ThreeColumnsRadio,
      dataType = "update",
      language = Language("fr"),
      title = "Demo title update",
      parameters = """[{"label":"Option update", "value":"value-update"}]"""
    )

    when(
      demographicsCardService.update(
        eqTo(DemographicsCardId("id-update")),
        eqTo("Demo name update"),
        eqTo(Layout.ThreeColumnsRadio),
        eqTo("update"),
        eqTo(Language("fr")),
        eqTo("Demo title update"),
        eqTo("""[{"label":"Option update","value":"value-update"}]""")
      )
    ).thenReturn(Future.successful(Some(updated)))

    Scenario("unauthorize unauthenticated") {
      Put("/admin/demographics-cards/id-update") ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Put("/admin/demographics-cards/id-update")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Put("/admin/demographics-cards/id-update")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated admin") {
      Put("/admin/demographics-cards/id-update")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin") {
      Put("/admin/demographics-cards/id-update")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{
                                                                    |  "name": "Demo name update",
                                                                    |  "layout": "ThreeColumnsRadio",
                                                                    |  "dataType": "update",
                                                                    |  "language": "fr",
                                                                    |  "title": "Demo title update",
                                                                    |  "parameters": [{"label":"Option update", "value":"value-update"}]
                                                                    |}""".stripMargin
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenSuperAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("bad requests") {
      Put("/admin/demographics-cards/id-update")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{
                                                                    |  "name": "Demo name update above 256 characters. 1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890",
                                                                    |  "layout": "OneColumnRadio",
                                                                    |  "dataType": "not a slug",
                                                                    |  "language": "fr",
                                                                    |  "title": "Demo title update above 64 characters. 123456789012345678901234567890",
                                                                    |  "parameters": [
                                                                    |    {"label":"Option update above 64 characters. 123456789012345678901234567890", "value":"value-update"},
                                                                    |    {"label":"too much", "value":"value-update"},
                                                                    |    {"label":"options", "value":"value-update"},
                                                                    |    {"label":"for this", "value":"value-update"},
                                                                    |    {"label":"type of layout", "value":"value-update"}
                                                                    |  ]
                                                                    |}""".stripMargin
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenSuperAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(5)
        errors.map(_.field) should contain theSameElementsAs Seq(
          "name",
          "dataType",
          "title",
          "parameters",
          "parameters"
        )
      }

      Put("/admin/demographics-cards/id-update")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                    |  "name": "Demo name",
                                                                    |  "layout": "fake",
                                                                    |  "dataType": "data-type",
                                                                    |  "language": "fr",
                                                                    |  "title": "Demo title",
                                                                    |  "parameters": [{"label":"Option update"}]
                                                                    |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenSuperAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(1)
        errors.head.field shouldBe "layout"
      }

      Put("/admin/demographics-cards/id-update")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                    |  "name": "Demo name",
                                                                    |  "layout": "Select",
                                                                    |  "dataType": "data-type",
                                                                    |  "language": "fr",
                                                                    |  "title": "Demo title",
                                                                    |  "parameters": [{"label":"Option update"}]
                                                                    |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenSuperAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size should be(1)
        errors.head.field shouldBe "parameters"
      }

    }
  }

}
