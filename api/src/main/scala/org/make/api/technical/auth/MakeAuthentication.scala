package org.make.api.technical.auth

import akka.http.scaladsl._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import org.make.api.technical.{IdGeneratorComponent, MakeDirectives, ShortenedNames}
import org.make.core.user.User

import scala.concurrent.Future
import scalaoauth2.provider._

/**
  * Mostly taken from https://github.com/nulab/akka-http-oauth2-provider with added support for redirect
  */
trait MakeAuthentication extends ShortenedNames with MakeDirectives {
  self: MakeDataHandlerComponent with IdGeneratorComponent =>

  def makeOAuth2(f: (AuthInfo[User]) => server.Route)(implicit ctx: EC = ECGlobal): Route = {
    authenticateOAuth2Async[AuthInfo[User]]("make.org API", oauth2Authenticator).tapply { (u) =>
      f(u._1)
    }
  }

  def oauth2Authenticator(credentials: Credentials)(implicit ctx: EC = ECGlobal): Future[Option[AuthInfo[User]]] =
    credentials match {
      case Credentials.Provided(token) =>
        oauth2DataHandler.findAccessToken(token).flatMap {
          case Some(t) => oauth2DataHandler.findAuthInfoByAccessToken(t)
          case None    => Future.successful(None)
        }
      case _ => Future.successful(None)
    }
}
