package org.make.api.technical.security

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
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
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "value", paramType = "body", dataType = "string")))
  def adminCreateSecureHash: Route

  @Path(value = "/security/secure-hash")
  @ApiOperation(value = "validate-secure-hash", httpMethod = "GET", code = HttpCodes.NoContent)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "")))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "hash", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "value", paramType = "path", dataType = "string")
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
    with MakeSettingsComponent =>

  override def securityApi: SecurityApi = new SecurityApi {
    override def adminCreateSecureHash: Route =
      post {
        path("admin" / "security" / "secure-hash") {
          makeOperation("CreateSecureHash") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                decodeRequest {
                  entity(as[SecureHashRequest]) { request: SecureHashRequest =>
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
      get {
        path("security" / "secure-hash") {
          makeOperation("ValidateSecureHash") { _ =>
            parameters(('hash, 'value)) { (hash, value) =>
              if (SecurityHelper.validateSecureHash(hash, value, securityConfiguration.secureHashSalt)) {
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

final case class SecureHashRequest(value: String)

object SecureHashRequest {
  implicit val decoder: Decoder[SecureHashRequest] = deriveDecoder[SecureHashRequest]
}

final case class SecureHashResponse(hash: String)

object SecureHashResponse {
  implicit val encoder: Encoder[SecureHashResponse] = deriveEncoder[SecureHashResponse]
  implicit val decoder: Decoder[SecureHashResponse] = deriveDecoder[SecureHashResponse]
}
