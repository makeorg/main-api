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
import org.make.core.operation.{FeaturedOperation, FeaturedOperationId}
import org.make.core.question.QuestionId
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito.when

import scala.concurrent.Future

class AdminFeaturedOperationApiTest
    extends MakeApiTestBase
    with DefaultAdminFeaturedOperationApiComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with FeaturedOperationServiceComponent {

  override val featuredOperationService: FeaturedOperationService = mock[FeaturedOperationService]

  val routes: Route = sealRoute(adminFeaturedOperationApi.routes)

  val featuredOperation = FeaturedOperation(
    featuredOperationId = FeaturedOperationId("featured-operation-id"),
    questionId = Some(QuestionId("question-id")),
    title = "featured operation",
    description = Some("description"),
    landscapePicture = "https://example.com/landscape-picture.png",
    portraitPicture = "https://example.com/portrait-picture.png",
    altPicture = "alt picture",
    label = "Grande cause",
    buttonLabel = "En savoir +",
    internalLink = Some("Consultation"),
    externalLink = None,
    slot = 1
  )

  feature("Unauthenticated / unauthorized user") {
    scenario("post featured operation") {
      Post("/admin/views/home/featured-operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }

      Post("/admin/views/home/featured-operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }

      Post("/admin/views/home/featured-operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("put featured operation") {
      Put("/admin/views/home/featured-operations/featured-operation-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }

      Put("/admin/views/home/featured-operations/featured-operation-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }

      Put("/admin/views/home/featured-operations/featured-operation-id")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("get featured operation") {
      Get("/admin/views/home/featured-operations/featured-operation-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }

      Get("/admin/views/home/featured-operations/featured-operation-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }

      Get("/admin/views/home/featured-operations/featured-operation-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("get featured operations") {
      Get("/admin/views/home/featured-operations") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }

      Get("/admin/views/home/featured-operations")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }

      Get("/admin/views/home/featured-operations")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("delete featured operation") {
      Delete("/admin/views/home/featured-operations/featured-operation-id") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }

      Delete("/admin/views/home/featured-operations/featured-operation-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }

      Delete("/admin/views/home/featured-operations/featured-operation-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }
  }

  feature("admin user") {
    scenario("post featured operation with admin rights") {

      when(featuredOperationService.create(any[CreateFeaturedOperationRequest]))
        .thenReturn(Future.successful(featuredOperation))

      Post("/admin/views/home/featured-operations")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{
            | "questionId": "question-id",
            | "title": "featured operation",
            | "description": "description",
            | "landscapePicture": "https://example.com/landscape-picture.png",
            | "portraitPicture": "https://example.com/portrait-picture.png",
            | "altPicture": "alt picture",
            | "label": "Grande cause",
            | "buttonLabel": "En savoir +",
            | "internalLink": "Consultation",
            | "slot": "1"
            |}""".stripMargin)
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.Created
      }
    }

    scenario("post featured operation - bad request: some mandatory field missing") {
      Post("/admin/views/home/featured-operations")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{
            | "questionId": "question-id",
            | "landscapePicture": "https://example.com/landscape-picture.png",
            | "label": "Grande cause",
            | "buttonLabel": "En savoir +",
            | "internalLink": "Consultation",
            | "slot": "1"
            |}""".stripMargin)
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    scenario("post featured operation - bad request: some field with length too long") {
      Post("/admin/views/home/featured-operations")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{
          | "questionId": "question-id",
          | "title": "featured operation with a title too looooooooooooooooooooooooooooooooooooooooooooooooooooooooong",
          | "description": "description",
          | "landscapePicture": "https://example.com/landscape-picture.png",
          | "portraitPicture": "https://example.com/portrait-picture.png",
          | "altPicture": "alt picture aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
          | "label": "Grande cause",
          | "buttonLabel": "En savoir +",
          | "internalLink": "Consultation",
          | "slot": "1"
          |}""".stripMargin
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    scenario("post featured operation - bad request: two link are defined") {
      Post("/admin/views/home/featured-operations")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{
          | "questionId": "question-id",
          | "title": "featured operation",
          | "description": "description",
          | "landscapePicture": "https://example.com/landscape-picture.png",
          | "portraitPicture": "https://example.com/portrait-picture.png",
          | "altPicture": "alt picture",
          | "label": "Grande cause",
          | "buttonLabel": "En savoir +",
          | "internalLink": "Consultation",
          | "externalLink": "link.com",
          | "slot": "1"
          |}""".stripMargin)
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    scenario("put featured operation with admin rights") {

      when(
        featuredOperationService
          .update(matches(FeaturedOperationId("featured-operation-id")), any[UpdateFeaturedOperationRequest])
      ).thenReturn(Future.successful(Some(featuredOperation)))

      Put("/admin/views/home/featured-operations/featured-operation-id")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{
          | "questionId": "question-id",
          | "title": "featured operation",
          | "description": "description",
          | "landscapePicture": "https://example.com/landscape-picture.png",
          | "portraitPicture": "https://example.com/portrait-picture.png",
          | "altPicture": "alt picture",
          | "label": "Grande cause",
          | "buttonLabel": "En savoir +",
          | "internalLink": "Consultation",
          | "slot": "1"
          |}""".stripMargin)
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    scenario("put featured operation - not found") {
      when(
        featuredOperationService
          .update(matches(FeaturedOperationId("not-found-id")), any[UpdateFeaturedOperationRequest])
      ).thenReturn(Future.successful(None))

      Put("/admin/views/home/featured-operations/not-found-id")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{
          | "questionId": "question-id",
          | "title": "featured operation",
          | "description": "description",
          | "landscapePicture": "https://example.com/landscape-picture.png",
          | "portraitPicture": "https://example.com/portrait-picture.png",
          | "altPicture": "alt picture",
          | "label": "Grande cause",
          | "buttonLabel": "En savoir +",
          | "internalLink": "Consultation",
          | "slot": "1"
          |}""".stripMargin)
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    scenario("put featured operation - bad request: some mandatory field are missing") {
      Put("/admin/views/home/featured-operations/featured-operation-id")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{
          | "questionId": "question-id",
          | "description": "description",
          | "landscapePicture": "https://example.com/landscape-picture.png",
          | "label": "Grande cause",
          | "buttonLabel": "En savoir +"
          |}""".stripMargin)
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    scenario("put featured operation - bad request: some field with length too long") {
      Put("/admin/views/home/featured-operations/featured-operation-id")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{
          | "questionId": "question-id",
          | "title": "featured operation with a title too looooooooooooooooooooooooooooooooooooooooooooooooooooooooong",
          | "description": "description",
          | "landscapePicture": "https://example.com/landscape-picture.png",
          | "portraitPicture": "https://example.com/portrait-picture.png",
          | "altPicture": "alt picture aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
          | "label": "Grande cause",
          | "buttonLabel": "En savoir +",
          | "internalLink": "Consultation",
          | "slot": "1"
          |}""".stripMargin
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    scenario("put featured operation - bad request: two link are defined") {
      Put("/admin/views/home/featured-operations/featured-operation-id")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{
          | "questionId": "question-id",
          | "title": "featured operation",
          | "description": "description",
          | "landscapePicture": "https://example.com/landscape-picture.png",
          | "portraitPicture": "https://example.com/portrait-picture.png",
          | "altPicture": "alt picture",
          | "label": "Grande cause",
          | "buttonLabel": "En savoir +",
          | "internalLink": "Consultation",
          | "externalLink": "link.com",
          | "slot": "1"
          |}""".stripMargin)
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    scenario("get featured operation with admin rights") {

      when(
        featuredOperationService
          .getFeaturedOperation(matches(FeaturedOperationId("featured-operation-id")))
      ).thenReturn(Future.successful(Some(featuredOperation)))

      Get("/admin/views/home/featured-operations/featured-operation-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    scenario("get featured operation - not found") {

      when(
        featuredOperationService
          .getFeaturedOperation(matches(FeaturedOperationId("not-found-id")))
      ).thenReturn(Future.successful(None))

      Get("/admin/views/home/featured-operations/not-found-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    scenario("get featured operations with admin rights") {

      when(featuredOperationService.getAll).thenReturn(Future.successful(Seq(featuredOperation)))

      Get("/admin/views/home/featured-operations")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    scenario("delete featured operation with admin rights") {

      when(
        featuredOperationService
          .getFeaturedOperation(matches(FeaturedOperationId("featured-operation-id")))
      ).thenReturn(Future.successful(Some(featuredOperation)))

      when(
        featuredOperationService
          .delete(matches(FeaturedOperationId("featured-operation-id")))
      ).thenReturn(Future.successful({}))

      Delete("/admin/views/home/featured-operations/featured-operation-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NoContent
      }
    }

    scenario("delete featured operation - not found") {

      when(
        featuredOperationService
          .getFeaturedOperation(matches(FeaturedOperationId("not-found-id")))
      ).thenReturn(Future.successful(None))

      Delete("/admin/views/home/featured-operations/not-found-id")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

}
