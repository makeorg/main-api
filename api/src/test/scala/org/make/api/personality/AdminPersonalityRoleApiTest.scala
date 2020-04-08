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
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.when

import scala.concurrent.Future

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

  feature("post personality role") {
    scenario("post personality role unauthenticated") {
      Post("/admin/personality-roles").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("post personality without admin rights") {
      Post("/admin/personality-roles")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("post personality with admin rights") {

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

    scenario("post scenario with wrong request") {
      Post("/admin/personality-roles")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "firstName": "false"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  feature("put personality role") {
    scenario("put personality role unauthenticated") {
      Put("/admin/personality-roles/personality-role-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("put personality role without admin rights") {
      Put("/admin/personality-roles/personality-role-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("put personality role with admin rights") {

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

    scenario("put non existent personality role") {
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

  feature("get personality roles") {
    scenario("get personality roles unauthenticated") {
      Get("/admin/personality-roles") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("get personality roles without admin rights") {
      Get("/admin/personality-roles")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("get personality roles with admin rights") {

      when(personalityRoleService.find(start = 0, end = None, sort = None, order = None, roleIds = None, name = None))
        .thenReturn(Future.successful(Seq(personalityRole)))
      when(personalityRoleService.count(roleIds = None, name = None)).thenReturn(Future.successful(1))

      Get("/admin/personality-roles")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  feature("get personality role") {
    scenario("get personality role unauthenticated") {
      Get("/admin/personality-roles/personality-role-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("get personality role without admin rights") {
      Get("/admin/personality-roles/personality-role-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("get personality role with admin rights") {

      when(personalityRoleService.getPersonalityRole(any[PersonalityRoleId]))
        .thenReturn(Future.successful(Some(personalityRole)))

      Get("/admin/personality-roles/personality-role-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    scenario("get non existent personality role") {

      when(personalityRoleService.getPersonalityRole(any[PersonalityRoleId]))
        .thenReturn(Future.successful(None))

      Get("/admin/personality-roles/not-found")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  feature("delete personality role") {
    scenario("delete personality role unauthenticated") {
      Delete("/admin/personality-roles/personality-role-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("delete personality role without admin rights") {
      Delete("/admin/personality-roles/personality-role-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("delete personality role with admin rights") {

      when(personalityRoleService.deletePersonalityRole(any[PersonalityRoleId]))
        .thenReturn(Future.successful {})

      Delete("/admin/personality-roles/personality-role-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  feature("post personality role field") {
    scenario("post personality role field unauthenticated") {
      Post("/admin/personality-roles/personality-role-id/fields").withEntity(
        HttpEntity(ContentTypes.`application/json`, "")
      ) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("post personality field without admin rights") {
      Post("/admin/personality-roles/personality-role-id/fields")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("post personality field with admin rights") {

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

    scenario("post scenario with wrong request") {
      Post("/admin/personality-roles/personality-role-id/fields")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "name": "false"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  feature("put personality role field") {
    scenario("put personality role field unauthenticated") {
      Put("/admin/personality-roles/personality-role-id/fields/personality-role-field-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("put personality role field without admin rights") {
      Put("/admin/personality-roles/personality-role-id/fields/personality-role-field-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("put personality role field with admin rights") {

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

    scenario("put non existent personality role field") {
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

  feature("get personality role fields") {
    scenario("get personality role fields unauthenticated") {
      Get("/admin/personality-roles/personality-role-id/fields") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("get personality role fields without admin rights") {
      Get("/admin/personality-roles/personality-role-id/fields")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("get personality role fields with admin rights") {

      when(
        personalityRoleFieldService
          .find(
            start = 0,
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

  feature("get personality role field") {
    scenario("get personality role field nauthenticated") {
      Get("/admin/personality-roles/personality-role-id/fields/personality-role-field-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("get personality role field without admin rights") {
      Get("/admin/personality-roles/personality-role-id/fields/personality-role-field-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("get personality role fieldwith admin rights") {

      when(personalityRoleFieldService.getPersonalityRoleField(any[PersonalityRoleFieldId], any[PersonalityRoleId]))
        .thenReturn(Future.successful(Some(personalityRoleField)))

      Get("/admin/personality-roles/personality-role-id/fields/personality-role-field-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    scenario("get non existent personality role field") {

      when(personalityRoleFieldService.getPersonalityRoleField(any[PersonalityRoleFieldId], any[PersonalityRoleId]))
        .thenReturn(Future.successful(None))

      Get("/admin/personality-roles/personality-role-id/fields/not-found")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  feature("delete personality role field") {
    scenario("delete personality role field unauthenticated") {
      Delete("/admin/personality-roles/personality-role-id/fields/personality-role-field-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("delete personality role field without admin rights") {
      Delete("/admin/personality-roles/personality-role-id/fields/personality-role-field-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("delete personality role field with admin rights") {

      when(personalityRoleFieldService.deletePersonalityRoleField(any[PersonalityRoleFieldId]))
        .thenReturn(Future.successful {})

      Delete("/admin/personality-roles/personality-role-id/fields/personality-role-field-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

}
