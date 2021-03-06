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

import akka.http.scaladsl.model.headers.{Authorization, HttpChallenges}
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsMissing
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive1}
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.{MakeDirectives, ShortenedNames}
import org.make.core.ApplicationName
import org.make.core.auth.UserRights
import scalaoauth2.provider._

import scala.concurrent.Future

/**
  * Mostly taken from https://github.com/nulab/akka-http-oauth2-provider with added support for redirect
  */
trait MakeAuthentication extends ShortenedNames with MakeDirectives {
  self: MakeDirectivesDependencies =>

  val realm = "make.org API"

  def makeOAuth2: Directive1[AuthInfo[UserRights]] = {
    authenticateOAuth2Async[AuthInfo[UserRights]](realm, oauth2Authenticator)
  }

  def optionalMakeOAuth2: Directive1[Option[AuthInfo[UserRights]]] = {
    makeOAuth2.map(Some(_): Option[AuthInfo[UserRights]]).recover {
      case AuthenticationFailedRejection(_, _) +: _ => provide(None)
      case rejections                               => reject(rejections: _*)
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

  def secureCookieValue(applicationName: Option[ApplicationName]): Directive1[Option[String]] = {
    optionalCookieValue(makeSettings.SecureCookie.name, applicationName)
  }

  def extractToken(applicationName: Option[ApplicationName]): Directive1[Option[String]] = {
    for {
      maybeCookie        <- secureCookieValue(applicationName)
      maybeAuthorization <- optionalHeaderValueByType(Authorization)
    } yield maybeCookie.orElse(maybeAuthorization.map(_.credentials.token()))
  }

  def requireToken(applicationName: Option[ApplicationName]): Directive1[String] = {
    extractToken(applicationName).flatMap {
      case Some(token) => provide(token)
      case None =>
        mapInnerRoute { _ =>
          reject(AuthenticationFailedRejection(cause = CredentialsMissing, challenge = HttpChallenges.oAuth2(realm)))
        }.tmap(_ => "")
    }
  }
}
