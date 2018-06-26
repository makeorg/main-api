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
