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
import io.circe.Decoder
import io.swagger.annotations._
import org.make.api.technical.Futures.RichFutures
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical._
import org.make.api.technical.auth.MakeDataHandler.{CreatedAtParameter, RefreshTokenExpirationParameter}
import org.make.api.user.UserServiceComponent
import org.make.core.auth.{AuthCode, ClientId, UserRights}
import org.make.core.{DateHelper, HttpCodes, RequestContext}
import scalaoauth2.provider._

import javax.ws.rs.Path
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
      new ApiImplicitParam(name = "grant_type", paramType = "form", dataType = "string", defaultValue = "password"),
      new ApiImplicitParam(
        name = "approvePrivacyPolicy",
        paramType = "form",
        dataType = "boolean",
        defaultValue = "true"
      )
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

}

trait AuthenticationApiComponent {
  def authenticationApi: AuthenticationApi
}

trait DefaultAuthenticationApiComponent
    extends AuthenticationApiComponent
    with MakeDirectives
    with MakeAuthenticationDirectives
    with Logging {
  self: MakeDirectivesDependencies with UserServiceComponent =>

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
              val updatePrivacyPolicy: Future[Unit] = fields.get("approvePrivacyPolicy") match {
                case Some("true") =>
                  userService.getUser(grantResult.authInfo.user.userId).flatMap {
                    case Some(user) =>
                      userService
                        .update(user.copy(privacyPolicyApprovalDate = Some(DateHelper.now())), requestContext)
                        .toUnit
                    case _ => Future.unit
                  }
                case _ => Future.unit
              }
              provideAsync(updatePrivacyPolicy) { _ =>
                val tokenResponse = AuthenticationApi.grantResultToTokenResponse(grantResult)
                setMakeSecure(requestContext.applicationName, tokenResponse, grantResult.authInfo.user.userId) {
                  complete(tokenResponse)
                }
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
          makeOperation("OauthGetAccessToken") { requestContext =>
            makeOAuth2 { _ =>
              requireToken(requestContext.applicationName) { token =>
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

    override def logoutRoute: Route = post {
      path("logout") {
        makeOperation("OauthLogout") { requestContext =>
          makeOAuth2 { _ =>
            requireToken(requestContext.applicationName) { token =>
              onComplete(oauth2DataHandler.removeToken(token)) {
                case Success(_) =>
                  addCookies(requestContext.applicationName, logoutCookies()) { complete(StatusCodes.NoContent) }
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
          extractToken(requestContext.applicationName) { token =>
            onComplete(token.map(oauth2DataHandler.removeToken).getOrElse(Future.unit)) {
              case Success(_) =>
                addCookies(
                  requestContext.applicationName,
                  logoutCookies() ++
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
