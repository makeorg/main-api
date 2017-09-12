package org.make.api.technical.auth

import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes.{Found, Unauthorized}
import akka.http.scaladsl.model.headers.{`Set-Cookie`, HttpCookie}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, MakeDirectives, ShortenedNames}
import org.make.core.HttpCodes
import org.make.core.user.User

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scalaoauth2.provider.{AuthorizationRequest, GrantHandlerResult, TokenEndpoint}

@Api(value = "Authentication")
@Path(value = "/oauth")
trait AuthenticationApi
    extends MakeDirectives
    with MakeAuthenticationDirectives
    with ShortenedNames
    with StrictLogging {
  self: MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent with MakeSettingsComponent =>

  val tokenEndpoint: TokenEndpoint

  @ApiOperation(value = "oauth-access_token", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TokenResponse])))
  @Path(value = "/access_token")
  def accessTokenRoute(implicit ctx: EC = ECGlobal): Route =
    pathPrefix("oauth") {
      path("access_token") {
        makeTrace("OauthAccessToken") { _ =>
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
    }

  @ApiOperation(value = "make-oauth-access_token", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TokenResponse])))
  @Path(value = "/make_access_token")
  def makeAccessTokenRoute(implicit ctx: EC = ECGlobal): Route =
    pathPrefix("oauth") {
      path("make_access_token") {
        makeTrace("OauthMakeAccessToken") { _ =>
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
                  maybeGrantResponse.fold(_ => complete(Unauthorized), grantResult => {
                    handleGrantResult(fields, grantResult)
                  })
                case Failure(ex) => throw ex
              }
            }
          }
        }
      }
    }

  private def handleGrantResult(fields: Map[String, String], grantResult: GrantHandlerResult[User]) = {
    val redirectUri = fields.getOrElse("redirect_uri", "")
    if (redirectUri != "") {
      redirect(s"$redirectUri#access_token=${grantResult.accessToken}&state=${fields.getOrElse("state", "")}", Found)
    } else {
      mapResponseHeaders(
        _ ++ Seq(
          `Set-Cookie`(
            HttpCookie(
              name = makeSettings.SessionCookie.name,
              value = grantResult.accessToken,
              secure = makeSettings.SessionCookie.isSecure,
              httpOnly = true,
              maxAge = Some(makeSettings.SessionCookie.lifetime.toMillis),
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
  def logoutRoute(implicit ctx: EC = ECGlobal): Route =
    post {
      path("logout") {
        makeTrace("OauthLogout") { _ =>
          makeOAuth2 { userAuth =>
            val futureRowsDeletedCount: Future[Int] = oauth2DataHandler.removeTokenByUserId(userAuth.user.userId)
            onComplete(futureRowsDeletedCount) {
              case Success(_)  => complete(StatusCodes.NoContent)
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
}
