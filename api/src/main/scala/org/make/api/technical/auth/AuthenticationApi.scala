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

import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes.{Found, Unauthorized}
import akka.http.scaladsl.model.headers.{`Set-Cookie`, HttpCookie}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import io.circe.ObjectEncoder
import io.circe.generic.semiauto.deriveEncoder
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical._
import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.core.HttpCodes
import org.make.core.auth.UserRights

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scalaoauth2.provider.{AuthorizationRequest, GrantHandlerResult, OAuthError, TokenEndpoint}

@Api(value = "Authentication")
@Path(value = "/")
trait AuthenticationApi extends MakeDirectives with MakeAuthenticationDirectives with StrictLogging {
  self: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent =>

  val tokenEndpoint: TokenEndpoint

  @ApiOperation(value = "oauth-access_token", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TokenResponse])))
  @Path(value = "/oauth/access_token")
  def accessTokenRoute: Route =
    pathPrefix("oauth") {
      path("access_token") {
        makeOperation("OauthAccessToken") { _ =>
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
                case Failure(ex) => failWith(ex)
              }
            }
          }
        }
      }
    }

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
  def makeAccessTokenRoute: Route =
    pathPrefix("oauth") {
      path("make_access_token") {
        makeOperation("OauthMakeAccessToken") { requestContext =>
          post {
            formFieldMap { fields =>
              val allFields = fields ++ Map(
                "client_id" -> makeSettings.Authentication.defaultClientId,
                "client_secret" -> makeSettings.Authentication.defaultClientSecret
              )

              val future: Future[Either[OAuthError, GrantHandlerResult[UserRights]]] = tokenEndpoint
                .handleRequest(
                  new AuthorizationRequest(Map(), allFields.map { case (k, v) => k -> Seq(v) }),
                  oauth2DataHandler
                )
                .flatMap[Either[OAuthError, GrantHandlerResult[UserRights]]] {
                  case Left(e) => Future.successful(Left(e))
                  case Right(result) =>
                    sessionHistoryCoordinatorService
                      .convertSession(requestContext.sessionId, result.authInfo.user.userId)
                      .map(_ => Right(result))
                }

              onComplete(future) {
                case Success(Right(result)) => handleGrantResult(fields, result)
                case Success(Left(_))       => complete(Unauthorized)
                case Failure(ex)            => failWith(ex)
              }
            }
          }
        }
      }
    }

  private def handleGrantResult(fields: Map[String, String], grantResult: GrantHandlerResult[UserRights]): Route = {
    fields.get("redirect_uri") match {
      case Some(redirectUri) =>
        redirect(s"$redirectUri#access_token=${grantResult.accessToken}&state=${fields.getOrElse("state", "")}", Found)
      case None =>
        mapResponseHeaders(
          _ ++ Seq(
            `Set-Cookie`(
              HttpCookie(
                name = makeSettings.SessionCookie.name,
                value = grantResult.accessToken,
                secure = makeSettings.SessionCookie.isSecure,
                httpOnly = true,
                maxAge = Some(makeSettings.SessionCookie.lifetime.toSeconds),
                path = Some("/")
              )
            )
          )
        ) {
          complete(AuthenticationApi.grantResultToTokenResponse(grantResult))
        }
    }
  }

  @ApiOperation(
    value = "logout",
    httpMethod = "POST",
    code = HttpCodes.OK,
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
  def logoutRoute: Route =
    post {
      path("logout") {
        makeOperation("OauthLogout") { _ =>
          makeOAuth2 { userAuth =>
            val futureRowsDeletedCount: Future[Int] = oauth2DataHandler.removeTokenByUserId(userAuth.user.userId)
            onComplete(futureRowsDeletedCount) {
              case Success(_) =>
                mapResponseHeaders(
                  _ ++ Seq(
                    `Set-Cookie`(
                      HttpCookie(
                        name = sessionIdKey,
                        value = idGenerator.nextId(),
                        secure = makeSettings.SessionCookie.isSecure,
                        httpOnly = true,
                        maxAge = Some(makeSettings.SessionCookie.lifetime.toSeconds),
                        path = Some("/")
                      )
                    ),
                    `Set-Cookie`(
                      HttpCookie(
                        name = makeSettings.SessionCookie.name,
                        value = "",
                        secure = makeSettings.SessionCookie.isSecure,
                        httpOnly = true,
                        maxAge = Some(0),
                        path = Some("/")
                      )
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

  val authenticationRoutes: Route = accessTokenRoute ~ logoutRoute ~ makeAccessTokenRoute
}

object AuthenticationApi {
  def grantResultToTokenResponse(grantResult: GrantHandlerResult[UserRights]): TokenResponse =
    TokenResponse(
      grantResult.tokenType,
      grantResult.accessToken,
      grantResult.expiresIn.getOrElse(1L),
      grantResult.refreshToken.getOrElse("")
    )

  case class TokenResponse(token_type: String, access_token: String, expires_in: Long, refresh_token: String)

  object TokenResponse {
    implicit val encoder: ObjectEncoder[TokenResponse] = deriveEncoder[TokenResponse]
  }

}
