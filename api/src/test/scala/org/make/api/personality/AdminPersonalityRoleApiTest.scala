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

package org.make.api.personality

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.personality.{
  FieldType,
  PersonalityRole,
  PersonalityRoleField,
  PersonalityRoleFieldId,
  PersonalityRoleId
}

import scala.concurrent.Future
import org.make.core.technical.Pagination.Start

class AdminPersonalityRoleApiTest
    extends MakeApiTestBase
    with DefaultAdminPersonalityRoleApiComponent
    with MakeDataHandlerComponent
    with PersonalityRoleServiceComponent
    with PersonalityRoleFieldServiceComponent {

  override val personalityRoleService: PersonalityRoleService = mock[PersonalityRoleService]
  override val personalityRoleFieldService: PersonalityRoleFieldService = mock[PersonalityRoleFieldService]

  val routes: Route = sealRoute(adminPersonalityRoleApi.routes)

  val personalityRole = PersonalityRole(PersonalityRoleId("personality-role-id"), "CANDIDATE")
  val personalityRoleField = PersonalityRoleField(
    PersonalityRoleFieldId("personality-role-field-id"),
    PersonalityRoleId("personality-role-id"),
    name = "Activity",
    fieldType = FieldType.StringType,
    required = true
  )

  Feature("post personality role") {
    Scenario("post personality role unauthenticated") {
      Post("/admin/personality-roles").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("post personality without admin rights") {
      Post("/admin/personality-roles")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("post personality with admin rights") {

      when(
        personalityRoleService
          .createPersonalityRole(any[CreatePersonalityRoleRequest])
      ).thenReturn(Future.successful(personalityRole))

      Post("/admin/personality-roles")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "name": "CANDIDATE"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.Created
      }
    }

    Scenario("post scenario with wrong request") {
      Post("/admin/personality-roles")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "firstName": "false"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  Feature("put personality role") {
    Scenario("put personality role unauthenticated") {
      Put("/admin/personality-roles/personality-role-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("put personality role without admin rights") {
      Put("/admin/personality-roles/personality-role-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("put personality role with admin rights") {

      when(personalityRoleService.updatePersonalityRole(any[PersonalityRoleId], any[UpdatePersonalityRoleRequest]))
        .thenReturn(Future.successful(Some(personalityRole)))

      Put("/admin/personality-roles/personality-role-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "name": "updated"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    Scenario("put non existent personality role") {
      when(personalityRoleService.updatePersonalityRole(any[PersonalityRoleId], any[UpdatePersonalityRoleRequest]))
        .thenReturn(Future.successful(None))

      Put("/admin/personality-roles/not-found")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                   "name": "updated"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  Feature("get personality roles") {
    Scenario("get personality roles unauthenticated") {
      Get("/admin/personality-roles") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("get personality roles without admin rights") {
      Get("/admin/personality-roles")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("get personality roles with admin rights") {

      when(
        personalityRoleService
          .find(start = Start.zero, end = None, sort = None, order = None, roleIds = None, name = None)
      ).thenReturn(Future.successful(Seq(personalityRole)))
      when(personalityRoleService.count(roleIds = None, name = None)).thenReturn(Future.successful(1))

      Get("/admin/personality-roles")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  Feature("get personality role") {
    Scenario("get personality role unauthenticated") {
      Get("/admin/personality-roles/personality-role-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("get personality role without admin rights") {
      Get("/admin/personality-roles/personality-role-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("get personality role with admin rights") {

      when(personalityRoleService.getPersonalityRole(any[PersonalityRoleId]))
        .thenReturn(Future.successful(Some(personalityRole)))

      Get("/admin/personality-roles/personality-role-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    Scenario("get non existent personality role") {

      when(personalityRoleService.getPersonalityRole(any[PersonalityRoleId]))
        .thenReturn(Future.successful(None))

      Get("/admin/personality-roles/not-found")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  Feature("delete personality role") {
    Scenario("delete personality role unauthenticated") {
      Delete("/admin/personality-roles/personality-role-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("delete personality role without admin rights") {
      Delete("/admin/personality-roles/personality-role-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("delete personality role with admin rights") {

      when(personalityRoleService.deletePersonalityRole(any[PersonalityRoleId]))
        .thenReturn(Future.successful {})

      Delete("/admin/personality-roles/personality-role-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  Feature("post personality role field") {
    Scenario("post personality role field unauthenticated") {
      Post("/admin/personality-roles/personality-role-id/fields").withEntity(
        HttpEntity(ContentTypes.`application/json`, "")
      ) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("post personality field without admin rights") {
      Post("/admin/personality-roles/personality-role-id/fields")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("post personality field with admin rights") {

      when(
        personalityRoleFieldService
          .createPersonalityRoleField(any[PersonalityRoleId], any[CreatePersonalityRoleFieldRequest])
      ).thenReturn(Future.successful(personalityRoleField))

      Post("/admin/personality-roles/personality-role-id/fields")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "name": "Activity",
                                                                  | "fieldType": "STRING",
                                                                  | "required": true
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.Created
      }
    }

    Scenario("post scenario with wrong request") {
      Post("/admin/personality-roles/personality-role-id/fields")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "name": "false"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  Feature("put personality role field") {
    Scenario("put personality role field unauthenticated") {
      Put("/admin/personality-roles/personality-role-id/fields/personality-role-field-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("put personality role field without admin rights") {
      Put("/admin/personality-roles/personality-role-id/fields/personality-role-field-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("put personality role field with admin rights") {

      when(
        personalityRoleFieldService
          .updatePersonalityRoleField(
            any[PersonalityRoleFieldId],
            any[PersonalityRoleId],
            any[UpdatePersonalityRoleFieldRequest]
          )
      ).thenReturn(Future.successful(Some(personalityRoleField)))

      Put("/admin/personality-roles/personality-role-id/fields/personality-role-field-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "name": "updated",
                                                                  | "fieldType": "STRING",
                                                                  | "required": true
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    Scenario("put non existent personality role field") {
      when(
        personalityRoleFieldService.updatePersonalityRoleField(
          any[PersonalityRoleFieldId],
          any[PersonalityRoleId],
          any[UpdatePersonalityRoleFieldRequest]
        )
      ).thenReturn(Future.successful(None))

      Put("/admin/personality-roles/personality-role-id/fields/not-found")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                   "name": "updated",
                                                                  | "fieldType": "STRING",
                                                                  | "required": true
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  Feature("get personality role fields") {
    Scenario("get personality role fields unauthenticated") {
      Get("/admin/personality-roles/personality-role-id/fields") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("get personality role fields without admin rights") {
      Get("/admin/personality-roles/personality-role-id/fields")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("get personality role fields with admin rights") {

      when(
        personalityRoleFieldService
          .find(
            start = Start.zero,
            end = None,
            sort = None,
            order = None,
            personalityRoleId = Some(PersonalityRoleId("personality-role-id")),
            name = None,
            fieldType = None,
            required = None
          )
      ).thenReturn(Future.successful(Seq(personalityRoleField)))
      when(
        personalityRoleFieldService.count(
          personalityRoleId = Some(PersonalityRoleId("personality-role-id")),
          name = None,
          fieldType = None,
          required = None
        )
      ).thenReturn(Future.successful(1))

      Get("/admin/personality-roles/personality-role-id/fields")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  Feature("get personality role field") {
    Scenario("get personality role field nauthenticated") {
      Get("/admin/personality-roles/personality-role-id/fields/personality-role-field-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("get personality role field without admin rights") {
      Get("/admin/personality-roles/personality-role-id/fields/personality-role-field-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("get personality role fieldwith admin rights") {

      when(personalityRoleFieldService.getPersonalityRoleField(any[PersonalityRoleFieldId], any[PersonalityRoleId]))
        .thenReturn(Future.successful(Some(personalityRoleField)))

      Get("/admin/personality-roles/personality-role-id/fields/personality-role-field-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    Scenario("get non existent personality role field") {

      when(personalityRoleFieldService.getPersonalityRoleField(any[PersonalityRoleFieldId], any[PersonalityRoleId]))
        .thenReturn(Future.successful(None))

      Get("/admin/personality-roles/personality-role-id/fields/not-found")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  Feature("delete personality role field") {
    Scenario("delete personality role field unauthenticated") {
      Delete("/admin/personality-roles/personality-role-id/fields/personality-role-field-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("delete personality role field without admin rights") {
      Delete("/admin/personality-roles/personality-role-id/fields/personality-role-field-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("delete personality role field with admin rights") {

      when(personalityRoleFieldService.deletePersonalityRoleField(any[PersonalityRoleFieldId]))
        .thenReturn(Future.successful {})

      Delete("/admin/personality-roles/personality-role-id/fields/personality-role-field-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

}
