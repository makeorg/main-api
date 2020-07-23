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

import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.storage.{Content, FileType, StorageServiceComponent, UploadResponse}
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.{HttpCodes, ParameterExtractors}

import scala.concurrent.Future

@Api(value = "Admin View")
@Path(value = "/admin/views/home")
trait AdminViewApi extends Directives {

  @ApiOperation(
    value = "upload-home-image",
    httpMethod = "POST",
    code = HttpCodes.OK,
    consumes = "multipart/form-data",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "data", paramType = "formData", dataType = "file")))
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UploadResponse])))
  @Path(value = "/images")
  def uploadHomeImage: Route

  def routes: Route = uploadHomeImage

}

trait AdminViewApiComponent {
  def adminViewApi: AdminViewApi
}

trait DefaultAdminViewApiComponent
    extends AdminViewApiComponent
    with MakeAuthenticationDirectives
    with StrictLogging
    with ParameterExtractors {
  this: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent
    with StorageServiceComponent =>

  override lazy val adminViewApi: AdminViewApi = new DefaultAdminViewApi

  class DefaultAdminViewApi extends AdminViewApi {
    override def uploadHomeImage: Route = {
      post {
        path("admin" / "views" / "home" / "images") {
          makeOperation("uploadHomeImage") { _ =>
            makeOAuth2 { user =>
              requireAdminRole(user.user) {
                def uploadFile(extension: String, contentType: String, fileContent: Content): Future[String] = {
                  storageService
                    .uploadFile(FileType.Home, s"${idGenerator.nextId()}$extension", contentType, fileContent)
                }
                uploadImageAsync("data", uploadFile, sizeLimit = None) { (path, file) =>
                  file.delete()
                  complete(UploadResponse(path))
                }
              }
            }
          }
        }
      }
    }
  }
}
