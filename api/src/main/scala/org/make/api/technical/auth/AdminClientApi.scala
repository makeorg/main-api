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
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical._
import org.make.core.auth.{Client, ClientId, UserRights}
import org.make.core.user.{CustomRole, Role, UserId}
import org.make.core.{HttpCodes, Validation}
import scalaoauth2.provider._

import scala.util.Try

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
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.technical.auth.AdminCreateClientRequest"
      ),
      new ApiImplicitParam(name = "clientId", paramType = "path", dataType = "string")
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
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "string"),
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
    with StrictLogging {
  self: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent
    with ClientServiceComponent =>

  val clientId: PathMatcher1[ClientId] = Segment.flatMap(id => Try(ClientId(id)).toOption)

  override lazy val adminClientApi: AdminClientApi = new AdminClientApi {

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
                        secret = Some(request.secret),
                        scope = request.scope,
                        redirectUri = request.redirectUri,
                        defaultUserId = request.defaultUserId,
                        roles = request.roles.map(CustomRole.apply)
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
                      secret = Some(request.secret),
                      scope = request.scope,
                      redirectUri = request.redirectUri,
                      defaultUserId = request.defaultUserId,
                      roles = request.roles.map(CustomRole.apply)
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
          parameters(('_start.as[Int].?, '_end.as[Int].?, 'name.?)) { (start, end, name) =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                provideAsync(clientService.count(name)) { count =>
                  onSuccess(clientService.search(start.getOrElse(0), end, name)) { clients =>
                    complete(
                      (StatusCodes.OK, List(TotalCountHeader(count.toString)), clients.map(ClientResponse.apply))
                    )
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

final case class ClientResponse(clientId: ClientId,
                                name: String,
                                allowedGrantTypes: Seq[String],
                                secret: Option[String],
                                scope: Option[String],
                                redirectUri: Option[String],
                                defaultUserId: Option[UserId],
                                roles: Seq[Role])

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
      roles = client.roles
    )
}

final case class AdminCreateClientRequest(name: String,
                                          secret: String,
                                          allowedGrantTypes: Seq[String],
                                          scope: Option[String],
                                          redirectUri: Option[String],
                                          defaultUserId: Option[UserId],
                                          roles: Seq[String])

object AdminCreateClientRequest {
  implicit val encoder: Encoder[AdminCreateClientRequest] = deriveEncoder[AdminCreateClientRequest]
  implicit val decoder: Decoder[AdminCreateClientRequest] = deriveDecoder[AdminCreateClientRequest]
}