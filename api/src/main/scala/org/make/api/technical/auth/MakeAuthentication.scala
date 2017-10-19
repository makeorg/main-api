package org.make.api.technical.auth

import akka.http.scaladsl.server.directives.{AuthenticationDirective, Credentials}
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive1}
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.{IdGeneratorComponent, MakeDirectives, ShortenedNames}
import org.make.core.auth.UserRights

import scala.concurrent.Future
import scalaoauth2.provider._

/**
  * Mostly taken from https://github.com/nulab/akka-http-oauth2-provider with added support for redirect
  */
trait MakeAuthentication extends ShortenedNames with MakeDirectives {
  self: MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  private def defaultMakeOAuth2: AuthenticationDirective[AuthInfo[UserRights]] =
    authenticateOAuth2Async[AuthInfo[UserRights]]("make.org API", oauth2Authenticator)

  def makeOAuth2: Directive1[AuthInfo[UserRights]] = {
    defaultMakeOAuth2
  }

  def optionalMakeOAuth2: Directive1[Option[AuthInfo[UserRights]]] = {
    defaultMakeOAuth2.map(Some(_): Option[AuthInfo[UserRights]]).recover {
      case AuthenticationFailedRejection(_, _) +: _ ⇒ provide(None)
      case rejs ⇒ reject(rejs: _*)
    }
  }

  def oauth2Authenticator(credentials: Credentials)(implicit ctx: EC = ECGlobal): Future[Option[AuthInfo[UserRights]]] =
    credentials match {
      case Credentials.Provided(token) =>
        oauth2DataHandler.findAccessToken(token).flatMap {
          case Some(t) => oauth2DataHandler.findAuthInfoByAccessToken(t)
          case None    => Future.successful(None)
        }
      case _ => Future.successful(None)
    }
}
