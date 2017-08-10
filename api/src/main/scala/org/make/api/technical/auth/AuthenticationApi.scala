package org.make.api.technical.auth

import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes.{Found, Unauthorized}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import io.circe.Decoder
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.AuthenticationApi.{LogoutRequest, TokenResponse}
import org.make.api.technical.{IdGeneratorComponent, MakeDirectives, ShortenedNames}
import org.make.core.HttpCodes
import org.make.core.user.User

import scala.util.{Failure, Success}
import scalaoauth2.provider.{AuthorizationRequest, GrantHandlerResult, TokenEndpoint}

trait AuthenticationApi extends MakeDirectives with ShortenedNames with StrictLogging {
  self: MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  val tokenEndpoint: TokenEndpoint

  @ApiOperation(value = "oauth-access_token", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TokenResponse])))
  @Path(value = "/oauth/access_token")
  def accessTokenRoute(implicit ctx: EC = ECGlobal): Route =
    pathPrefix("oauth") {
      path("access_token") {
        post {
          formFieldMap { fields =>
            onComplete(
              tokenEndpoint
                .handleRequest(
                  new AuthorizationRequest(Map(), fields.map { case (k, v) => k -> Seq(v) }),
                  oauth2DataHandler
                )
            ) {
              case Success(maybeGrantResponse) =>
                maybeGrantResponse.fold(_ => complete(Unauthorized), grantResult => {
                  complete(AuthenticationApi.grantResultToTokenResponse(grantResult))
                })
              case Failure(ex) => throw ex
            }
          }
        }
      }
    }

  @ApiOperation(value = "make-oauth-access_token", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TokenResponse])))
  @Path(value = "/oauth/make_access_token")
  def makeAccessTokenRoute(implicit ctx: EC = ECGlobal): Route =
    pathPrefix("oauth") {
      path("make_access_token") {
        post {
          formFieldMap { fields =>
            val allFields = fields ++ Map(
              "client_id" -> makeSettings.Authentication.defaultClientId,
              "client_secret" -> makeSettings.Authentication.defaultClientSecret
            )
            onComplete(
              tokenEndpoint
                .handleRequest(
                  new AuthorizationRequest(Map(), allFields.map { case (k, v) => k -> Seq(v) }),
                  oauth2DataHandler
                )
            ) {
              case Success(maybeGrantResponse) =>
                maybeGrantResponse.fold(
                  _ => complete(Unauthorized),
                  grantResult => {
                    val redirectUri = fields.getOrElse("redirect_uri", "")
                    if (redirectUri != "") {
                      redirect(
                        s"$redirectUri#access_token=${grantResult.accessToken}&state=${fields.getOrElse("state", "")}",
                        Found
                      )
                    } else {
                      complete(AuthenticationApi.grantResultToTokenResponse(grantResult))
                    }
                  }
                )
              case Failure(ex) => throw ex
            }
          }
        }
      }
    }

  @ApiOperation(
    value = "logout",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "/logout")
  def logoutRoute(implicit ctx: EC = ECGlobal): Route =
    post {
      path("logout") {
        decodeRequest {
          entity(as[LogoutRequest]) { accessToken =>
            onComplete(oauth2DataHandler.removeTokenByAccessToken(accessToken.accessToken)) {
              case Success(nbRowsDeleted) =>
                if (nbRowsDeleted == 0) {
                  logger.warn(s"Access token provided in the logout request was not found or invalid: $accessToken")
                }
                complete(HttpCodes.OK)
              case Failure(ex) => throw ex
            }
          }
        }
      }
    }

  val authenticationRoutes: Route = accessTokenRoute ~ logoutRoute ~ makeAccessTokenRoute
}

object AuthenticationApi {
  def grantResultToTokenResponse(grantResult: GrantHandlerResult[User]): TokenResponse =
    TokenResponse(
      grantResult.tokenType,
      grantResult.accessToken,
      grantResult.expiresIn.getOrElse(1L),
      grantResult.refreshToken.getOrElse("")
    )

  case class TokenResponse(token_type: String, access_token: String, expires_in: Long, refresh_token: String)

  case class LogoutRequest(accessToken: String)

  object LogoutRequest {
    implicit val logoutRequestDecoder: Decoder[LogoutRequest] =
      Decoder.forProduct1("access_token")(LogoutRequest.apply)
  }
}
