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

package org.make.api.technical.auth

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, PathMatcher1, Route}
import grizzled.slf4j.Logging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._

import javax.ws.rs.Path
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical._
import org.make.core.auth.{Client, ClientId, UserRights}
import org.make.core.user.{CustomRole, Role, UserId}
import org.make.core.{HttpCodes, ParameterExtractors, Validation}
import scalaoauth2.provider._

import scala.annotation.meta.field
import org.make.core.technical.Pagination._

@Api(value = "Client OAuth")
@Path(value = "/admin/clients")
trait AdminClientApi extends Directives {
  @ApiOperation(
    value = "create-oauth-client",
    httpMethod = "POST",
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
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.technical.auth.AdminCreateClientRequest"
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ClientResponse])))
  @Path(value = "/")
  def createClient: Route

  @ApiOperation(
    value = "update-oauth-client",
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
      new ApiImplicitParam(name = "clientId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.technical.auth.AdminCreateClientRequest"
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ClientResponse])))
  @Path(value = "/{clientId}")
  def updateClient: Route

  @ApiOperation(
    value = "get-oauth-client",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ClientResponse])))
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "clientId", paramType = "path", dataType = "string")))
  @Path(value = "/{clientId}")
  def getClient: Route

  @ApiOperation(
    value = "list-oauth-client",
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
      new ApiImplicitParam(name = "name", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[ClientResponse]]))
  )
  @Path(value = "/")
  def listClients: Route

  def routes: Route = createClient ~ getClient ~ listClients ~ updateClient
}

trait AdminClientApiComponent {
  def adminClientApi: AdminClientApi
}

trait DefaultAdminClientApiComponent
    extends AdminClientApiComponent
    with MakeDirectives
    with MakeAuthenticationDirectives
    with Logging
    with ParameterExtractors {
  self: MakeDirectivesDependencies with ClientServiceComponent =>

  override lazy val adminClientApi: AdminClientApi = new DefaultAdminClientApi

  class DefaultAdminClientApi extends AdminClientApi {

    val clientId: PathMatcher1[ClientId] = Segment.map(id => ClientId(id))

    override def createClient: Route =
      post {
        path("admin" / "clients") {
          makeOperation("AdminCreateOauthClient") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[AdminCreateClientRequest]) { request: AdminCreateClientRequest =>
                    Validation.validate(Validation.validateUserInput("name", request.name, None))
                    onSuccess(
                      clientService.createClient(
                        name = request.name,
                        allowedGrantTypes = request.allowedGrantTypes,
                        secret = request.secret,
                        scope = request.scope,
                        redirectUri = request.redirectUri,
                        defaultUserId = request.defaultUserId,
                        roles = request.roles.map(CustomRole.apply),
                        tokenExpirationSeconds = request.tokenExpirationSeconds,
                        refreshExpirationSeconds = request.refreshExpirationSeconds,
                        reconnectExpirationSeconds = request.reconnectExpirationSeconds
                      )
                    ) { client =>
                      complete(StatusCodes.Created -> ClientResponse(client))
                    }
                  }
                }
              }
            }
          }
        }
      }

    override def getClient: Route = get {
      path("admin" / "clients" / clientId) { clientId =>
        makeOperation("AdminGetOauthClient") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              provideAsyncOrNotFound(clientService.getClient(clientId)) { client =>
                complete(ClientResponse(client))
              }
            }
          }
        }
      }
    }

    override def updateClient: Route = put {
      path("admin" / "clients" / clientId) { clientId =>
        makeOperation("AdminUpdateOauthClient") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[AdminCreateClientRequest]) { request: AdminCreateClientRequest =>
                  Validation.validate(Validation.validateUserInput("name", request.name, None))
                  provideAsyncOrNotFound(
                    clientService.updateClient(
                      clientId = clientId,
                      name = request.name,
                      allowedGrantTypes = request.allowedGrantTypes,
                      secret = request.secret,
                      scope = request.scope,
                      redirectUri = request.redirectUri,
                      defaultUserId = request.defaultUserId,
                      roles = request.roles.map(CustomRole.apply),
                      tokenExpirationSeconds = request.tokenExpirationSeconds,
                      refreshExpirationSeconds = request.refreshExpirationSeconds,
                      reconnectExpirationSeconds = request.reconnectExpirationSeconds
                    )
                  ) { client =>
                    complete(ClientResponse(client))
                  }
                }
              }
            }
          }
        }
      }
    }

    override def listClients: Route = get {
      path("admin" / "clients") {
        makeOperation("AdminListOauthClient") { _ =>
          parameters("_start".as[Start].?, "_end".as[End].?, "name".?) { (start, end, name) =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                provideAsync(clientService.count(name)) { count =>
                  onSuccess(clientService.search(start.orZero, end, name)) { clients =>
                    complete((StatusCodes.OK, List(`X-Total-Count`(count.toString)), clients.map(ClientResponse.apply)))
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

final case class ClientResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "331ec138-1a68-4432-99a1-983a4200e1d1")
  clientId: ClientId,
  name: String,
  allowedGrantTypes: Seq[String],
  @(ApiModelProperty @field)(dataType = "string", example = "ebe271b8-236f-46da-94ca-fec0b83534ca")
  secret: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "3ffd4b4a-c603-4fbb-aada-639edd169836")
  scope: Option[String],
  redirectUri: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "59043bc6-d540-4c8e-9c66-fe8601c2c67d")
  defaultUserId: Option[UserId],
  @(ApiModelProperty @field)(dataType = "list[string]")
  roles: Seq[Role],
  @(ApiModelProperty @field)(dataType = "int", example = "300")
  tokenExpirationSeconds: Int,
  refreshExpirationSeconds: Int,
  reconnectExpirationSeconds: Int
)

object ClientResponse {
  implicit val encoder: Encoder[ClientResponse] = deriveEncoder[ClientResponse]
  implicit val decoder: Decoder[ClientResponse] = deriveDecoder[ClientResponse]
  def apply(client: Client): ClientResponse =
    ClientResponse(
      clientId = client.clientId,
      name = client.name,
      allowedGrantTypes = client.allowedGrantTypes,
      secret = client.secret,
      scope = client.scope,
      redirectUri = client.redirectUri,
      defaultUserId = client.defaultUserId,
      roles = client.roles,
      tokenExpirationSeconds = client.tokenExpirationSeconds,
      refreshExpirationSeconds = client.refreshExpirationSeconds,
      reconnectExpirationSeconds = client.reconnectExpirationSeconds
    )
}

final case class AdminCreateClientRequest(
  name: String,
  @(ApiModelProperty @field)(dataType = "string", example = "ebe271b8-236f-46da-94ca-fec0b83534ca")
  secret: Option[String],
  allowedGrantTypes: Seq[String],
  @(ApiModelProperty @field)(dataType = "string", example = "3ffd4b4a-c603-4fbb-aada-639edd169836")
  scope: Option[String],
  redirectUri: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "59043bc6-d540-4c8e-9c66-fe8601c2c67d")
  defaultUserId: Option[UserId],
  @(ApiModelProperty @field)(
    dataType = "list[string]",
    allowableValues = "ROLE_ADMIN,ROLE_MODERATOR,ROLE_POLITICAL,ROLE_CITIZEN,ROLE_ACTOR"
  )
  roles: Seq[String],
  @(ApiModelProperty @field)(dataType = "int", example = "300")
  tokenExpirationSeconds: Int,
  refreshExpirationSeconds: Int,
  reconnectExpirationSeconds: Int
)

object AdminCreateClientRequest {
  implicit val encoder: Encoder[AdminCreateClientRequest] = deriveEncoder[AdminCreateClientRequest]
  implicit val decoder: Decoder[AdminCreateClientRequest] = deriveDecoder[AdminCreateClientRequest]
}
