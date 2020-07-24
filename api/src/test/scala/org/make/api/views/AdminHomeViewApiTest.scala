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

package org.make.api.views

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.util.ByteString
import org.make.api.MakeApiTestBase
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.storage.Content.FileContent
import org.make.api.technical.storage.{FileType, StorageService, StorageServiceComponent, UploadResponse}

import scala.concurrent.Future
import scala.concurrent.duration._

class AdminHomeViewApiTest
    extends MakeApiTestBase
    with DefaultAdminViewApiComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent
    with StorageServiceComponent {

  override lazy val storageService: StorageService = mock[StorageService]

  val routes: Route = sealRoute(adminViewApi.routes)

  Feature("upload image") {
    implicit val timeout: RouteTestTimeout = RouteTestTimeout(300.seconds)
    def uri = "/admin/views/home/images"

    Scenario("unauthorized not connected") {
      Post(uri) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbidden citizen") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbidden moderator") {
      Post(uri)
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("incorrect file type") {
      val request: Multipart = Multipart.FormData(fields = Map(
        "data" -> HttpEntity
          .Strict(ContentTypes.`application/x-www-form-urlencoded`, ByteString("incorrect file type"))
      )
      )

      Post(uri, request)
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("storage unavailable") {
      when(storageService.uploadFile(eqTo(FileType.Home), any[String], any[String], any[FileContent]))
        .thenReturn(Future.failed(new Exception("swift client error")))
      val request: Multipart =
        Multipart.FormData(
          Multipart.FormData.BodyPart
            .Strict(
              "data",
              HttpEntity.Strict(ContentType(MediaTypes.`image/jpeg`), ByteString("image")),
              Map("filename" -> "image.jpeg")
            )
        )

      Post(uri, request)
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.InternalServerError)
      }
    }

    Scenario("large file successfully uploaded and returned by admin") {
      when(storageService.uploadFile(eqTo(FileType.Home), any[String], any[String], any[FileContent]))
        .thenReturn(Future.successful("path/to/uploaded/image.jpeg"))

      def entityOfSize(size: Int): Multipart = Multipart.FormData(
        Multipart.FormData.BodyPart
          .Strict(
            "data",
            HttpEntity.Strict(ContentType(MediaTypes.`image/jpeg`), ByteString("0" * size)),
            Map("filename" -> "image.jpeg")
          )
      )
      Post(uri, entityOfSize(256000 + 1))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)

        val path: UploadResponse = entityAs[UploadResponse]
        path.path shouldBe "path/to/uploaded/image.jpeg"
      }
    }
  }
}
