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

package org.make.api.user.social

import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.user.social.models.UserInfo
import org.make.api.user.{social, UserServiceComponent}
import org.make.core.RequestContext
import org.make.core.auth.UserRights
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalaoauth2.provider.AuthInfo

trait SocialServiceComponent extends GoogleApiComponent with FacebookApiComponent {
  def socialService: SocialService
}

trait SocialService {

  def login(provider: String,
            token: String,
            country: String,
            language: String,
            clientIp: Option[String],
            requestContext: RequestContext): Future[UserIdAndToken]
}

case class UserIdAndToken(userId: UserId, token: TokenResponse)

trait DefaultSocialServiceComponent extends SocialServiceComponent {
  self: UserServiceComponent with MakeDataHandlerComponent =>

  override lazy val socialService: social.SocialService = new SocialService {

    private val GOOGLE_PROVIDER = "google"
    private val FACEBOOK_PROVIDER = "facebook"

    /**
      *
      * @param provider string
      * @param token  string
      *
      * @return Future[UserInfo]
      */
    def login(provider: String,
              token: String,
              country: String,
              language: String,
              clientIp: Option[String],
              requestContext: RequestContext): Future[UserIdAndToken] = {

      val futureUserInfo: Future[UserInfo] = provider match {
        case GOOGLE_PROVIDER =>
          for {
            googleUserInfo <- googleApi.getUserInfo(token)
          } yield
            UserInfo(
              email = googleUserInfo.email,
              firstName = googleUserInfo.givenName,
              lastName = googleUserInfo.familyName,
              country = country,
              language = language,
              googleId = googleUserInfo.iat,
              picture = Option(googleUserInfo.picture),
              domain = googleUserInfo.hd
            )
        case FACEBOOK_PROVIDER =>
          for {
            facebookUserInfo <- facebookApi.getUserInfo(token)
          } yield
            UserInfo(
              email = facebookUserInfo.email,
              firstName = facebookUserInfo.firstName,
              lastName = facebookUserInfo.lastName,
              country = country,
              language = language,
              gender = facebookUserInfo.gender,
              facebookId = Some(facebookUserInfo.id),
              picture = Option(facebookUserInfo.picture.data.url)
            )
        case _ => Future.failed(new Exception(s"Social login failed: undefined provider $provider"))
      }

      for {
        userInfo <- futureUserInfo
        user     <- userService.createOrUpdateUserFromSocial(userInfo, clientIp, requestContext)
        accessToken <- oauth2DataHandler.createAccessToken(
          authInfo =
            AuthInfo(user = UserRights(user.userId, user.roles), clientId = None, scope = None, redirectUri = None)
        )
      } yield {
        UserIdAndToken(
          userId = user.userId,
          token = TokenResponse(
            "Bearer",
            accessToken.token,
            accessToken.expiresIn.getOrElse(1L),
            accessToken.refreshToken.getOrElse("")
          )
        )
      }
    }

  }
}
