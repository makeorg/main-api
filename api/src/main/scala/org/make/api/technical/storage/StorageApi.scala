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

package org.make.api.technical.storage

import java.nio.file.Files

import akka.http.scaladsl.server._
import com.typesafe.scalalogging.StrictLogging
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.api.technical.storage.Content.FileContent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, MakeDirectives}
import org.make.api.user.UserServiceComponent
import org.make.core.operation.OperationId
import org.make.core.profile.Profile
import org.make.core.user.Role.RoleAdmin
import org.make.core.user.UserId
import org.make.core.{HttpCodes, ValidationError, ValidationFailedError}

@Api(value = "Storage (experimental, may change)")
@Path(value = "/storage")
trait StorageApi extends Directives {

  @ApiOperation(
    value = "upload-operation-image",
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
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "operationId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "data", paramType = "formData", dataType = "file")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UploadResponse])))
  @Path(value = "/operations/{operationId}")
  def uploadOperationImage: Route

  @ApiOperation(
    value = "upload-avatar",
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
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "userId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "data", paramType = "formData", dataType = "file")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UploadResponse])))
  @Path(value = "/users/{userId}")
  def uploadAvatar: Route

  def routes: Route = uploadOperationImage ~ uploadAvatar
}

trait StorageApiComponent {
  def storageApi: StorageApi
}

trait DefaultStorageApiComponent
    extends StorageApiComponent
    with MakeDirectives
    with StrictLogging
    with MakeAuthenticationDirectives {
  self: StorageServiceComponent
    with MakeSettingsComponent
    with IdGeneratorComponent
    with MakeAuthentication
    with MakeDataHandlerComponent
    with OperationServiceComponent
    with UserServiceComponent
    with SessionHistoryCoordinatorServiceComponent =>

  override lazy val storageApi: StorageApi = new DefaultStorageApi

  class DefaultStorageApi extends StorageApi {

    val operationId: PathMatcher1[OperationId] = Segment.map(OperationId.apply)
    val userId: PathMatcher1[UserId] = Segment.map(UserId.apply)

    override def uploadOperationImage: Route = {
      post {
        path("storage" / "operations" / operationId) { operationId =>
          makeOperation("uploadOperationFile") { _ =>
            makeOAuth2 { user =>
              requireAdminRole(user.user) {
                provideAsyncOrNotFound(operationService.findOne(operationId)) { operation =>
                  storeUploadedFile("data", fileInfo => Files.createTempFile("makeapi", fileInfo.fileName).toFile) {
                    case (info, file) =>
                      file.deleteOnExit()
                      val contentType = info.contentType
                      if (!contentType.mediaType.isImage) {
                        throw ValidationFailedError(
                          Seq(ValidationError("data", "invalid_format", Some("File must be an image")))
                        )
                      } else {
                        val fileName = file.getName
                        val extension = fileName.substring(fileName.lastIndexOf("."))
                        onSuccess(
                          storageService
                            .uploadFile(
                              FileType.Operation,
                              s"${operation.slug}/${idGenerator.nextId()}$extension",
                              contentType.value,
                              FileContent(file)
                            )
                        ) { path =>
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
    }

    override def uploadAvatar: Route = {
      post {
        path("storage" / "users" / userId) { userId =>
          makeOperation("uploadOperationFile") { requestContext =>
            makeOAuth2 { user =>
              validate(
                user.user.userId == userId || user.user.roles.contains(RoleAdmin),
                "You can only change the avatar for yourself"
              ) {
                storeUploadedFile("data", fileInfo => Files.createTempFile("makeapi", fileInfo.fileName).toFile) {
                  case (info, file) =>
                    file.deleteOnExit()
                    val contentType = info.contentType
                    if (!contentType.mediaType.isImage) {
                      throw ValidationFailedError(
                        Seq(ValidationError("data", "invalid_format", Some("File must be an image")))
                      )
                    } else {
                      val fileName = file.getName
                      val extension = fileName.substring(fileName.lastIndexOf("."))
                      onSuccess(
                        storageService
                          .uploadFile(
                            FileType.Avatar,
                            s"${userId.value}/${idGenerator.nextId()}$extension",
                            contentType.value,
                            FileContent(file)
                          )
                      ) { path =>
                        file.delete()
                        provideAsyncOrNotFound(userService.getUser(userId)) { user =>
                          val modifiedProfile = user.profile match {
                            case Some(profile) => profile.copy(avatarUrl = Some(path))
                            case None          => Profile.default.copy(avatarUrl = Some(path))
                          }

                          onSuccess(userService.update(user.copy(profile = Some(modifiedProfile)), requestContext)) {
                            _ =>
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
      }
    }
  }
}

case class UploadResponse(path: String)

object UploadResponse {
  implicit val encoder: Encoder[UploadResponse] = deriveEncoder[UploadResponse]
}
