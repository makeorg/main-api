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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.swagger.annotations.{
  Api,
  ApiImplicitParam,
  ApiImplicitParams,
  ApiModelProperty,
  ApiOperation,
  ApiResponse,
  ApiResponses,
  Authorization,
  AuthorizationScope
}
import org.make.api.technical.{`X-Total-Count`, MakeAuthenticationDirectives}
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.core.{HttpCodes, Order, ParameterExtractors, ValidationError}
import org.make.core.auth.UserRights
import org.make.core.technical.Pagination.{End, Start}
import org.make.core.widget.{Source, SourceId}
import scalaoauth2.provider.AuthInfo

import javax.ws.rs.Path
import scala.annotation.meta.field

@Api(value = "Admin Sources")
@Path(value = "/admin/sources")
trait AdminSourceApi extends Directives {

  @ApiOperation(
    value = "list-sources",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "name", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "source", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[AdminSourceResponse]]))
  )
  @Path(value = "/")
  def list: Route

  @ApiOperation(
    value = "get-source-by-id",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "id", paramType = "path", dataType = "string")))
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[AdminSourceResponse]))
  )
  @Path(value = "/{id}")
  def getById: Route

  @ApiOperation(
    value = "create-source",
    httpMethod = "POST",
    code = HttpCodes.Created,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.widget.AdminSourceRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.Created, message = "Ok", response = classOf[AdminSourceResponse]))
  )
  @Path(value = "/")
  def create: Route

  @ApiOperation(
    value = "update-source",
    httpMethod = "PUT",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "id", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.widget.AdminSourceRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[AdminSourceResponse]))
  )
  @Path(value = "/{id}")
  def update: Route

  def routes: Route = list ~ getById ~ create ~ update

}

trait AdminSourceApiComponent {
  def adminSourceApi: AdminSourceApi
}

trait DefaultAdminSourceApiComponent
    extends AdminSourceApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  self: MakeDirectivesDependencies with SourceServiceComponent =>

  override val adminSourceApi: AdminSourceApi = new AdminSourceApi {

    private val id: PathMatcher1[SourceId] = Segment.map(SourceId.apply)

    override def list: Route = get {
      path("admin" / "sources") {
        parameters("_start".as[Start].?, "_end".as[End].?, "_sort".?, "_order".as[Order].?, "name".?, "source".?) {
          (
            start: Option[Start],
            end: Option[End],
            sort: Option[String],
            order: Option[Order],
            name: Option[String],
            source: Option[String]
          ) =>
            makeOperation("AdminSourcesList") { _ =>
              makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                requireAdminRole(userAuth.user) {
                  provideAsync(sourceService.list(start, end, sort, order, name, source)) { sources =>
                    provideAsync(sourceService.count(name, source)) { count =>
                      complete(
                        (StatusCodes.OK, List(`X-Total-Count`(count.toString)), sources.map(AdminSourceResponse.apply))
                      )
                    }
                  }
                }
              }
            }
        }
      }
    }

    override def getById: Route = get {
      path("admin" / "sources" / id) { id =>
        makeOperation("AdminSourcesGetById") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              provideAsyncOrNotFound(sourceService.get(id)) { source =>
                complete(AdminSourceResponse(source))
              }
            }
          }
        }
      }
    }

    override def create: Route = post {
      path("admin" / "sources") {
        makeOperation("AdminSourcesCreate") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[AdminSourceRequest]) { request: AdminSourceRequest =>
                  provideAsync(sourceService.findBySource(request.source)) {
                    case Some(_) =>
                      complete(
                        StatusCodes.BadRequest -> ValidationError(
                          "source",
                          "already_defined",
                          Some(s"Source ${request.source} already exists")
                        )
                      )
                    case _ =>
                      provideAsync(
                        sourceService
                          .create(name = request.name, source = request.source, userId = userAuth.user.userId)
                      ) { source =>
                        complete(StatusCodes.Created, AdminSourceResponse(source))
                      }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def update: Route = put {
      path("admin" / "sources" / id) { id =>
        makeOperation("AdminSourcesUpdate") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[AdminSourceRequest]) { request: AdminSourceRequest =>
                  provideAsync(sourceService.findBySource(request.source)) {
                    case Some(source) if source.id != id =>
                      complete(
                        StatusCodes.BadRequest -> ValidationError(
                          "source",
                          "already_defined",
                          Some(s"Source ${request.source} already exists")
                        )
                      )
                    case _ =>
                      provideAsyncOrNotFound(
                        sourceService
                          .update(id = id, name = request.name, source = request.source, userId = userAuth.user.userId)
                      ) { source =>
                        complete(AdminSourceResponse(source))
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

final case class AdminSourceRequest(name: String, source: String)

object AdminSourceRequest {
  implicit val codec: Codec[AdminSourceRequest] = deriveCodec
}

final case class AdminSourceResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "331ec138-1a68-4432-99a1-983a4200e1d1")
  id: SourceId,
  name: String,
  source: String
)

object AdminSourceResponse {

  def apply(source: Source): AdminSourceResponse =
    AdminSourceResponse(id = source.id, name = source.name, source = source.source)

  implicit val codec: Codec[AdminSourceResponse] = deriveCodec

}
