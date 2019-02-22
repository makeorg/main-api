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

package org.make.api.technical.security

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.HttpCodes
import org.make.core.auth.UserRights
import scalaoauth2.provider.AuthInfo

@Api(value = "Security")
@Path(value = "/")
trait SecurityApi extends Directives {

  @Path(value = "/admin/security/secure-hash")
  @ApiOperation(
    value = "create-secure-hash",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SecureHashResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.technical.security.CreateSecureHashRequest"
      )
    )
  )
  def adminCreateSecureHash: Route

  @Path(value = "/security/secure-hash")
  @ApiOperation(value = "validate-secure-hash", httpMethod = "POST", code = HttpCodes.NoContent)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "")))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.technical.security.ValidateSecureHashRequest"
      )
    )
  )
  def validateSecureHash: Route

  def routes: Route = adminCreateSecureHash ~ validateSecureHash
}

trait SecurityApiComponent {
  def securityApi: SecurityApi
}

trait DefaultSecurityApiComponent extends SecurityApiComponent with MakeAuthenticationDirectives {
  this: SecurityConfigurationComponent
    with ActorSystemComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SessionHistoryCoordinatorServiceComponent
    with MakeSettingsComponent =>

  override def securityApi: SecurityApi = new SecurityApi {
    override def adminCreateSecureHash: Route =
      post {
        path("admin" / "security" / "secure-hash") {
          makeOperation("CreateSecureHash") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                decodeRequest {
                  entity(as[CreateSecureHashRequest]) { request: CreateSecureHashRequest =>
                    complete(
                      SecureHashResponse(
                        hash = SecurityHelper.createSecureHash(request.value, securityConfiguration.secureHashSalt)
                      )
                    )
                  }
                }
              }
            }
          }
        }
      }

    override def validateSecureHash: Route =
      post {
        path("security" / "secure-hash") {
          makeOperation("ValidateSecureHash") { _ =>
            decodeRequest {
              entity(as[ValidateSecureHashRequest]) { request: ValidateSecureHashRequest =>
                if (SecurityHelper
                      .validateSecureHash(request.hash, request.value, securityConfiguration.secureHashSalt)) {
                  complete(StatusCodes.NoContent)
                } else {
                  complete(StatusCodes.BadRequest)
                }
              }
            }
          }
        }
      }
  }
}

final case class CreateSecureHashRequest(value: String)

object CreateSecureHashRequest {
  implicit val decoder: Decoder[CreateSecureHashRequest] = deriveDecoder[CreateSecureHashRequest]
}

final case class ValidateSecureHashRequest(hash: String, value: String)

object ValidateSecureHashRequest {
  implicit val decoder: Decoder[ValidateSecureHashRequest] = deriveDecoder[ValidateSecureHashRequest]
}

final case class SecureHashResponse(hash: String)

object SecureHashResponse {
  implicit val encoder: Encoder[SecureHashResponse] = deriveEncoder[SecureHashResponse]
  implicit val decoder: Decoder[SecureHashResponse] = deriveDecoder[SecureHashResponse]
}
