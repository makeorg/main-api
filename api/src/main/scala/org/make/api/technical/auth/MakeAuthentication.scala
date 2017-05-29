package org.make.api.technical.auth

import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.{Directives, Route}
import de.knutwalker.akka.http.support.CirceHttpSupport
import io.circe.generic.auto._
import org.make.api.technical.ShortenedNames
import org.make.api.technical.auth.OAuth2Provider.TokenResponse
import org.make.core.citizen.Citizen

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scalaoauth2.provider._

/**
  * Mostly taken from https://github.com/nulab/akka-http-oauth2-provider with added support for redirect
  */
trait MakeAuthentication extends ShortenedNames with Directives with CirceHttpSupport {
  self: MakeDataHandlerComponent =>

  def makeOAuth2(f: (AuthInfo[Citizen]) => server.Route)(implicit ctx: EC = ECGlobal): Route = {
    authenticateOAuth2Async[AuthInfo[Citizen]]("make.org API", oauth2Authenticator).tapply { (u) =>
      f(u._1)
    }
  }

  val oauth2DataHandler: DataHandler[Citizen]

  val tokenEndpoint: TokenEndpoint

  def grantResultToTokenResponse(grantResult: GrantHandlerResult[Citizen]): TokenResponse =
    TokenResponse(
      grantResult.tokenType,
      grantResult.accessToken,
      grantResult.expiresIn.getOrElse(1L),
      grantResult.refreshToken.getOrElse("")
    )

  def oauth2Authenticator(credentials: Credentials)(implicit ctx: EC = ECGlobal): Future[Option[AuthInfo[Citizen]]] =
    credentials match {
      case Credentials.Provided(token) =>
        oauth2DataHandler.findAccessToken(token).flatMap {
          case Some(t) => oauth2DataHandler.findAuthInfoByAccessToken(t)
          case None => Future.successful(None)
        }
      case _ => Future.successful(None)
    }

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
                    complete(grantResultToTokenResponse(grantResult))
                  }
                })
              case Failure(ex) => throw ex
            }
          }
        }
      }
    }

}

object OAuth2Provider {

  case class TokenResponse(token_type: String, access_token: String, expires_in: Long, refresh_token: String)

}
