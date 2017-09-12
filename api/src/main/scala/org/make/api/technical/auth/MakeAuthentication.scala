package org.make.api.technical.auth

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.directives.{AuthenticationDirective, Credentials}
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.{IdGeneratorComponent, MakeDirectives, ShortenedNames}
import org.make.core.user.User

import scala.concurrent.Future
import scalaoauth2.provider._

/**
  * Mostly taken from https://github.com/nulab/akka-http-oauth2-provider with added support for redirect
  */
trait MakeAuthentication extends ShortenedNames with MakeDirectives {
  self: MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  private def defaultMakeOAuth2: AuthenticationDirective[AuthInfo[User]] =
    authenticateOAuth2Async[AuthInfo[User]]("make.org API", oauth2Authenticator)

  def makeOAuth2: Directive1[AuthInfo[User]] = {
    defaultMakeOAuth2
  }

  def optionalMakeOAuth2: Directive1[Option[AuthInfo[User]]] = {
    defaultMakeOAuth2.optional
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
