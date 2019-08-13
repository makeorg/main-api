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

import com.typesafe.scalalogging.StrictLogging
import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.api.technical.auth.{ClientServiceComponent, MakeDataHandlerComponent}
import org.make.api.user.UserServiceComponent
import org.make.api.user.social.models.UserInfo
import org.make.core.RequestContext
import org.make.core.auth.{ClientId, UserRights}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.user.UserId
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SocialServiceComponent extends GoogleApiComponent with FacebookApiComponent {
  def socialService: SocialService
}

trait SocialService {

  def login(provider: String,
            token: String,
            country: Country,
            language: Language,
            clientIp: Option[String],
            questionId: Option[QuestionId],
            requestContext: RequestContext,
            clientId: ClientId): Future[UserIdAndToken]
}

case class UserIdAndToken(userId: UserId, token: TokenResponse)

trait DefaultSocialServiceComponent extends SocialServiceComponent {
  self: UserServiceComponent with MakeDataHandlerComponent with ClientServiceComponent with StrictLogging =>

  override lazy val socialService: SocialService = new DefaultSocialService

  class DefaultSocialService extends SocialService {

    private val GOOGLE_PROVIDER = "google"
    private val FACEBOOK_PROVIDER = "facebook"

    def login(provider: String,
              token: String,
              country: Country,
              language: Language,
              clientIp: Option[String],
              questionId: Option[QuestionId],
              requestContext: RequestContext,
              clientId: ClientId): Future[UserIdAndToken] = {

      val futureUserInfo: Future[UserInfo] = provider match {
        case GOOGLE_PROVIDER =>
          for {
            googleUserInfo <- googleApi.getUserInfo(token)
          } yield
            UserInfo(
              email = googleUserInfo.email,
              firstName = googleUserInfo.givenName,
              lastName = googleUserInfo.familyName,
              country = country.value,
              language = language.value,
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
              country = country.value,
              language = language.value,
              facebookId = Some(facebookUserInfo.id),
              picture = Option(facebookUserInfo.picture)
            )
        case _ => Future.failed(new Exception(s"Social login failed: undefined provider $provider"))
      }

      def futureClient(userId: UserId): Future[Option[ClientId]] = {
        clientService.getClient(clientId).map {
          case Some(foundClient) => Some(foundClient.clientId)
          case None =>
            logger.warn(s"Social login with an invalid client: $clientId. No client is defined for user $userId.")
            None
        }
      }

      for {
        userInfo <- futureUserInfo
        user     <- userService.createOrUpdateUserFromSocial(userInfo, clientIp, questionId, requestContext)
        client   <- futureClient(user.userId)
        accessToken <- oauth2DataHandler.createAccessToken(
          authInfo = AuthInfo(
            user = UserRights(user.userId, user.roles, user.availableQuestions),
            clientId = client.map(_.value),
            scope = None,
            redirectUri = None
          )
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
