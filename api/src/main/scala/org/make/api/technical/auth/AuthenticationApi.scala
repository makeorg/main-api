package org.make.api.technical.auth

import akka.http.scaladsl.model.StatusCodes.{Found, Unauthorized}
import akka.http.scaladsl.server.Route
import io.circe.generic.auto._
import org.make.api.technical.{IdGeneratorComponent, MakeDirectives, ShortenedNames}
import org.make.core.user.User

import scala.util.{Failure, Success}
import scalaoauth2.provider.{AuthorizationRequest, GrantHandlerResult, TokenEndpoint}

trait AuthenticationApi extends MakeDirectives with ShortenedNames {
  self: MakeDataHandlerComponent with IdGeneratorComponent =>

  val tokenEndpoint: TokenEndpoint

  def accessTokenRoute(implicit ctx: EC = ECGlobal): Route =
    pathPrefix("oauth") {
      path("access_token") {
        post {
          formFieldMap { fields =>
            onComplete(
              tokenEndpoint
                .handleRequest(new AuthorizationRequest(Map(), fields.map(m => m._1 -> Seq(m._2))), oauth2DataHandler)
            ) {
              case Success(maybeGrantResponse) =>
                maybeGrantResponse.fold(_ => complete(Unauthorized), grantResult => {
                  val redirectUri = fields.getOrElse("redirect_uri", "")
                  if (redirectUri != "") {
                    redirect(redirectUri + "#access_token=" + grantResult.accessToken, Found)
                  } else {
                    complete(AuthenticationApi.grantResultToTokenResponse(grantResult))
                  }
                })
              case Failure(ex) => throw ex
            }
          }
        }
      }
    }

  val authenticationRoutes: Route = accessTokenRoute
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
