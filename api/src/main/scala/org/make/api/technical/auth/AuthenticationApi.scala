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
import akka.http.scaladsl.model.headers.HttpCookie
import akka.http.scaladsl.server.{Directives, Route}
import grizzled.slf4j.Logging
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical._
import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.api.technical.auth.MakeDataHandler.{CreatedAtParameter, RefreshTokenExpirationParameter}
import org.make.core.auth.{AuthCode, ClientId, UserRights}
import org.make.core.{DateHelper, HttpCodes, RequestContext}
import scalaoauth2.provider._

import scala.annotation.meta.field
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Api(value = "Authentication")
@Path(value = "/")
trait AuthenticationApi extends Directives {

  @ApiOperation(value = "oauth-access_token", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TokenResponse])))
  @Path(value = "/oauth/access_token")
  def accessTokenRoute: Route

  @ApiOperation(
    value = "make-oauth-access_token",
    httpMethod = "POST",
    code = HttpCodes.OK,
    consumes = "application/x-www-form-urlencoded"
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TokenResponse])))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "username", paramType = "form", dataType = "string"),
      new ApiImplicitParam(name = "password", paramType = "form", dataType = "string"),
      new ApiImplicitParam(name = "grant_type", paramType = "form", dataType = "string", defaultValue = "password")
    )
  )
  @Path(value = "/oauth/make_access_token")
  def makeAccessTokenRoute: Route

  @ApiOperation(
    value = "oauth-get_access_token",
    httpMethod = "GET",
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
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TokenResponse])))
  @Path(value = "/oauth/access_token")
  def getAccessTokenRoute: Route

  @ApiOperation(
    value = "logout",
    httpMethod = "POST",
    code = HttpCodes.NoContent,
    consumes = "text/plain",
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
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No content")))
  @Path(value = "/logout")
  def logoutRoute: Route

  @ApiOperation(
    value = "OAuth-Code",
    httpMethod = "POST",
    code = HttpCodes.OK,
    consumes = "application/json",
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
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(paramType = "body", dataType = "org.make.api.technical.auth.CreateAuthorizationCodeRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[AuthCode])))
  @Path(value = "/oauth/code")
  def createAuthorizationCode: Route

  def form: Route

  @ApiOperation(
    value = "reset-cookies",
    httpMethod = "POST",
    code = HttpCodes.NoContent,
    consumes = "text/plain",
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
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No content")))
  @Path(value = "/resetCookies")
  def resetCookies: Route

  def routes: Route =
    getAccessTokenRoute ~
      accessTokenRoute ~
      logoutRoute ~
      makeAccessTokenRoute ~
      form ~
      createAuthorizationCode ~
      resetCookies
}

object AuthenticationApi {

  def grantResultToTokenResponse(grantResult: GrantHandlerResult[UserRights]): TokenResponse =
    TokenResponse(
      grantResult.tokenType,
      grantResult.accessToken,
      grantResult.expiresIn.getOrElse(1L),
      grantResult.refreshToken,
      grantResult.params.get(RefreshTokenExpirationParameter).map(_.toLong),
      grantResult.params.getOrElse(CreatedAtParameter, DateHelper.format(DateHelper.now()))
    )

  final case class TokenResponse(
    @(ApiModelProperty @field)(name = "token_type")
    tokenType: String,
    @(ApiModelProperty @field)(name = "access_token")
    accessToken: String,
    @(ApiModelProperty @field)(name = "expires_in")
    expiresIn: Long,
    @(ApiModelProperty @field)(name = "refresh_token")
    refreshToken: Option[String],
    @(ApiModelProperty @field)(name = "refresh_expires_in", dataType = "int")
    refreshExpiresIn: Option[Long],
    @(ApiModelProperty @field)(name = "created_at")
    createdAt: String
  )

  object TokenResponse {
    def fromAccessToken(tokenResult: AccessToken): TokenResponse = {
      TokenResponse(
        "Bearer",
        tokenResult.token,
        tokenResult.expiresIn.getOrElse(1L),
        tokenResult.refreshToken,
        tokenResult.params.get(RefreshTokenExpirationParameter).map(_.toLong),
        tokenResult.params.getOrElse(CreatedAtParameter, "")
      )
    }

    implicit val encoder: Encoder[TokenResponse] = {
      Encoder.forProduct6(
        "token_type",
        "access_token",
        "expires_in",
        "refresh_token",
        "refresh_expires_in",
        "created_at"
      ) { response =>
        (
          response.tokenType,
          response.accessToken,
          response.expiresIn,
          response.refreshToken,
          response.refreshExpiresIn,
          response.createdAt
        )
      }
    }
  }

}

trait AuthenticationApiComponent {
  def authenticationApi: AuthenticationApi
}

trait DefaultAuthenticationApiComponent
    extends AuthenticationApiComponent
    with MakeDirectives
    with MakeAuthenticationDirectives
    with Logging {
  self: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent =>

  def tokenEndpoint: TokenEndpoint

  override lazy val authenticationApi: AuthenticationApi = new DefaultAuthenticationApi

  class DefaultAuthenticationApi extends AuthenticationApi {

    override def createAuthorizationCode: Route = {
      post {
        path("oauth" / "code") {
          makeOperation("CreateAuthCode", EndpointType.Public) { _ =>
            makeOAuth2 { user =>
              decodeRequest {
                entity(as[CreateAuthorizationCodeRequest]) { request =>
                  provideAsyncOrNotFound(
                    oauth2DataHandler
                      .createAuthorizationCode(user.user.userId, request.clientId, request.scope, request.redirectUri)
                  ) { code =>
                    complete(StatusCodes.OK -> code)
                  }
                }
              }
            }
          }
        }
      }
    }

    private def issueAccessToken(requestContext: RequestContext): Route = {
      formFieldMap { fields =>
        extractRequest { request =>
          val headers: Map[String, Seq[String]] =
            request.headers.groupMap(_.name())(_.value())
          onComplete(
            tokenEndpoint
              .handleRequest(
                new AuthorizationRequest(headers, fields.map { case (k, v) => k -> Seq(v) }),
                oauth2DataHandler
              )
              .flatMap[Either[OAuthError, GrantHandlerResult[UserRights]]] {
                case Left(e) => Future.successful(Left(e))
                case Right(result) =>
                  sessionHistoryCoordinatorService
                    .convertSession(requestContext.sessionId, result.authInfo.user.userId, requestContext)
                    .map(_ => Right(result))
              }
          ) {
            case Success(Right(grantResult)) =>
              val tokenResponse = AuthenticationApi.grantResultToTokenResponse(grantResult)
              setMakeSecure(requestContext.applicationName, tokenResponse, grantResult.authInfo.user.userId) {
                complete(tokenResponse)
              }
            case Success(Left(e)) => failWith(e)
            case Failure(ex)      => failWith(ex)
          }
        }
      }
    }

    override def accessTokenRoute: Route =
      post {
        path("oauth" / "access_token") {
          makeOperation("OauthAccessToken", EndpointType.Public) { requestContext =>
            issueAccessToken(requestContext)
          }
        }
      }

    override def makeAccessTokenRoute: Route =
      post {
        path("oauth" / "make_access_token") {
          makeOperation("OauthMakeAccessToken", EndpointType.Public) { requestContext =>
            issueAccessToken(requestContext)
          }
        }
      }

    override def getAccessTokenRoute: Route = pathPrefix("oauth") {
      get {
        path("access_token") {
          makeOperation("OauthGetAccessToken") { _ =>
            makeOAuth2 { _ =>
              requireToken { token =>
                provideAsyncOrNotFound(oauth2DataHandler.findAccessToken(token)) { tokenResult =>
                  complete(TokenResponse.fromAccessToken(tokenResult))
                }
              }
            }
          }
        }
      }
    }

    override def form: Route = get {
      path("oauth" / "authenticate") {
        makeOperation("AuthenticationForm") { _ =>
          getFromResource("authentication/index.html")
        }
      }
    }

    private def logoutCookies: Seq[HttpCookie] = Seq(
      HttpCookie(
        name = makeSettings.SessionCookie.name,
        value = idGenerator.nextId(),
        secure = makeSettings.SessionCookie.isSecure,
        httpOnly = true,
        maxAge = Some(makeSettings.SessionCookie.lifetime.toSeconds),
        path = Some("/"),
        domain = Some(makeSettings.SessionCookie.domain)
      ),
      HttpCookie(
        name = makeSettings.SessionCookie.expirationName,
        value = DateHelper
          .format(DateHelper.now().plusSeconds(makeSettings.SessionCookie.lifetime.toSeconds)),
        secure = makeSettings.SessionCookie.isSecure,
        httpOnly = false,
        maxAge = None,
        path = Some("/"),
        domain = Some(makeSettings.SessionCookie.domain)
      ),
      HttpCookie(
        name = makeSettings.SecureCookie.name,
        value = "",
        secure = makeSettings.SecureCookie.isSecure,
        httpOnly = true,
        maxAge = Some(0),
        path = Some("/"),
        domain = Some(makeSettings.SecureCookie.domain)
      ),
      HttpCookie(
        name = makeSettings.SecureCookie.expirationName,
        value = "",
        secure = makeSettings.SecureCookie.isSecure,
        httpOnly = false,
        maxAge = Some(0),
        path = Some("/"),
        domain = Some(makeSettings.SecureCookie.domain)
      )
    )

    override def logoutRoute: Route = post {
      path("logout") {
        makeOperation("OauthLogout") { requestContext =>
          makeOAuth2 { _ =>
            requireToken { token =>
              onComplete(oauth2DataHandler.removeToken(token)) {
                case Success(_) =>
                  addCookies(requestContext.applicationName, logoutCookies) { complete(StatusCodes.NoContent) }
                case Failure(ex) => failWith(ex)
              }
            }
          }
        }
      }
    }

    override def resetCookies: Route = post {
      path("resetCookies") {
        makeOperation("ResetCookies") { requestContext =>
          extractToken { token =>
            onComplete(token.map(oauth2DataHandler.removeToken).getOrElse(Future.unit)) {
              case Success(_) =>
                addCookies(
                  requestContext.applicationName,
                  logoutCookies ++
                    Seq(
                      HttpCookie(
                        name = makeSettings.UserIdCookie.name,
                        value = "",
                        secure = makeSettings.UserIdCookie.isSecure,
                        httpOnly = true,
                        maxAge = Some(0),
                        path = Some("/"),
                        domain = Some(makeSettings.UserIdCookie.domain)
                      ),
                      HttpCookie(
                        name = makeSettings.VisitorCookie.name,
                        value = "",
                        secure = makeSettings.VisitorCookie.isSecure,
                        httpOnly = true,
                        maxAge = Some(0),
                        path = Some("/"),
                        domain = Some(makeSettings.VisitorCookie.domain)
                      ),
                      HttpCookie(
                        name = makeSettings.VisitorCookie.createdAtName,
                        value = "",
                        secure = makeSettings.VisitorCookie.isSecure,
                        httpOnly = true,
                        maxAge = Some(0),
                        path = Some("/"),
                        domain = Some(makeSettings.VisitorCookie.domain)
                      )
                    )
                ) {
                  complete(StatusCodes.NoContent)
                }
              case Failure(ex) => failWith(ex)
            }
          }
        }
      }
    }

  }
}

final case class CreateAuthorizationCodeRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "7951a086-fa88-4cd0-815a-d76f514b2f1d")
  clientId: ClientId,
  scope: Option[String],
  redirectUri: Option[String]
)

object CreateAuthorizationCodeRequest {
  implicit val decoder: Decoder[CreateAuthorizationCodeRequest] = deriveDecoder[CreateAuthorizationCodeRequest]
}
