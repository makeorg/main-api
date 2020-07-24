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

package org.make.api.operation

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.IdGeneratorComponent
import org.make.core.operation.{CurrentOperation, CurrentOperationId}
import org.make.core.question.QuestionId

import scala.concurrent.Future

class AdminCurrentOperationApiTest
    extends MakeApiTestBase
    with DefaultAdminCurrentOperationApiComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with CurrentOperationServiceComponent {

  override val currentOperationService: CurrentOperationService = mock[CurrentOperationService]

  val routes: Route = sealRoute(adminCurrentOperationApi.routes)

  val currentOperation: CurrentOperation = CurrentOperation(
    currentOperationId = CurrentOperationId("current-operation-id"),
    questionId = QuestionId("question-id"),
    description = "description",
    label = "label",
    picture = "https://example.com/picture.png",
    altPicture = "alt picture",
    linkLabel = "Grande cause",
    internalLink = Some("Consultation"),
    externalLink = None
  )

  Feature("Unauthenticated / unauthorized user") {
    Scenario("post current operation") {
      Post("/admin/views/home/current-operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }

      Post("/admin/views/home/current-operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }

      Post("/admin/views/home/current-operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("put current operation") {
      Put("/admin/views/home/current-operations/current-operation-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }

      Put("/admin/views/home/current-operations/current-operation-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }

      Put("/admin/views/home/current-operations/current-operation-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("get current operation") {
      Get("/admin/views/home/current-operations/current-operation-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }

      Get("/admin/views/home/current-operations/current-operation-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }

      Get("/admin/views/home/current-operations/current-operation-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("get current operations") {
      Get("/admin/views/home/current-operations") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }

      Get("/admin/views/home/current-operations")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }

      Get("/admin/views/home/current-operations")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("delete current operation") {
      Delete("/admin/views/home/current-operations/current-operation-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }

      Delete("/admin/views/home/current-operations/current-operation-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }

      Delete("/admin/views/home/current-operations/current-operation-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }
  }

  Feature("admin user") {
    Scenario("post current operation with admin rights") {

      when(currentOperationService.create(any[CreateCurrentOperationRequest]))
        .thenReturn(Future.successful(currentOperation))

      Post("/admin/views/home/current-operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "questionId": "question-id",
                                                                  | "description": "description",
                                                                  | "label": "label",
                                                                  | "picture": "https://example.com/picture.png",
                                                                  | "altPicture": "alt picture",
                                                                  | "linkLabel": "Grande cause",
                                                                  | "internalLink": "Consultation"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.Created
      }
    }

    Scenario("post current operation - bad request: some mandatory field missing") {
      Post("/admin/views/home/current-operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "questionId": "question-id",
                                                                  | "picture": "landscape-picture.png",
                                                                  | "linkLabel": "Grande cause",
                                                                  | "internalLink": "Consultation"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    Scenario("post current operation - bad request: some field with length too long") {
      Post("/admin/views/home/current-operations")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{
              | "questionId": "question-id",
              | "description": "description is way too loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong",
              | "label": "label",
              | "picture": "https://example.com/picture.png",
              | "altPicture": "alt picture aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              | "linkLabel": "Grande cause",
              | "internalLink": "Consultation"
              |}""".stripMargin
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    Scenario("post current operation - bad request: two link are defined") {
      Post("/admin/views/home/current-operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "questionId": "question-id",
                                                                  | "description": "description",
                                                                  | "label": "label",
                                                                  | "picture": "https://example.com/picture.png",
                                                                  | "altPicture": "alt picture",
                                                                  | "linkLabel": "Grande cause",
                                                                  | "internalLink": "Consultation",
                                                                  | "externalLink": "link.com"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    Scenario("put current operation with admin rights") {

      when(
        currentOperationService
          .update(eqTo(CurrentOperationId("current-operation-id")), any[UpdateCurrentOperationRequest])
      ).thenReturn(Future.successful(Some(currentOperation)))

      Put("/admin/views/home/current-operations/current-operation-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "questionId": "question-id",
                                                                  | "description": "description",
                                                                  | "label": "label",
                                                                  | "picture": "https://example.com/picture.png",
                                                                  | "altPicture": "alt picture",
                                                                  | "linkLabel": "Grande cause",
                                                                  | "internalLink": "Consultation"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    Scenario("put current operation - not found") {
      when(
        currentOperationService
          .update(eqTo(CurrentOperationId("not-found-id")), any[UpdateCurrentOperationRequest])
      ).thenReturn(Future.successful(None))

      Put("/admin/views/home/current-operations/not-found-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "questionId": "question-id",
                                                                  | "description": "description",
                                                                  | "label": "label",
                                                                  | "picture": "https://example.com/picture.png",
                                                                  | "altPicture": "alt picture",
                                                                  | "linkLabel": "Grande cause",
                                                                  | "internalLink": "Consultation"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    Scenario("put current operation - bad request: some mandatory field are missing") {
      Put("/admin/views/home/current-operations/current-operation-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "questionId": "question-id",
                                                                  | "picture": "https://example.com/picture.png"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    Scenario("put current operation - bad request: some field with length too long") {
      Put("/admin/views/home/current-operations/current-operation-id")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{
              | "questionId": "question-id",
              | "description": "description is way too loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong",
              | "label": "label",
              | "picture": "https://example.com/picture.png",
              | "altPicture": "alt picture aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              | "linkLabel": "Grande cause",
              | "internalLink": "Consultation"
              |}""".stripMargin
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    Scenario("put current operation - bad request: two link are defined") {
      Put("/admin/views/home/current-operations/current-operation-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                                                                  | "questionId": "question-id",
                                                                  | "description": "description",
                                                                  | "label": "label",
                                                                  | "picture": "https://example.com/picture.png",
                                                                  | "altPicture": "alt picture",
                                                                  | "linkLabel": "Grande cause",
                                                                  | "internalLink": "Consultation",
                                                                  | "externalLink": "link.com"
                                                                  |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    Scenario("get current operation with admin rights") {

      when(
        currentOperationService
          .getCurrentOperation(eqTo(CurrentOperationId("current-operation-id")))
      ).thenReturn(Future.successful(Some(currentOperation)))

      Get("/admin/views/home/current-operations/current-operation-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    Scenario("get current operation - not found") {

      when(
        currentOperationService
          .getCurrentOperation(eqTo(CurrentOperationId("not-found-id")))
      ).thenReturn(Future.successful(None))

      Get("/admin/views/home/current-operations/not-found-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    Scenario("get current operations with admin rights") {

      when(currentOperationService.getAll).thenReturn(Future.successful(Seq(currentOperation)))

      Get("/admin/views/home/current-operations")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    Scenario("delete current operation with admin rights") {

      when(
        currentOperationService
          .getCurrentOperation(eqTo(CurrentOperationId("current-operation-id")))
      ).thenReturn(Future.successful(Some(currentOperation)))

      when(
        currentOperationService
          .delete(eqTo(CurrentOperationId("current-operation-id")))
      ).thenReturn(Future.successful({}))

      Delete("/admin/views/home/current-operations/current-operation-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    Scenario("delete current operation - not found") {

      when(
        currentOperationService
          .getCurrentOperation(eqTo(CurrentOperationId("not-found-id")))
      ).thenReturn(Future.successful(None))

      Delete("/admin/views/home/current-operations/not-found-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

}
