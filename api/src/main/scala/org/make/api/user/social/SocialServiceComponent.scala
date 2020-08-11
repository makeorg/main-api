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
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.user.SocialProvider.{Facebook, Google, GooglePeople}
import org.make.api.user.social.models.UserInfo
import org.make.api.user.{SocialLoginResponse, SocialProvider, UserServiceComponent}
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

  def login(
    provider: SocialProvider,
    token: String,
    country: Country,
    language: Language,
    clientIp: Option[String],
    questionId: Option[QuestionId],
    requestContext: RequestContext,
    validatedClientId: ClientId
  ): Future[(UserId, SocialLoginResponse)]
}

trait DefaultSocialServiceComponent extends SocialServiceComponent {
  self: UserServiceComponent with MakeDataHandlerComponent with StrictLogging =>

  override lazy val socialService: SocialService = new DefaultSocialService

  class DefaultSocialService extends SocialService {

    def login(
      provider: SocialProvider,
      token: String,
      country: Country,
      language: Language,
      clientIp: Option[String],
      questionId: Option[QuestionId],
      requestContext: RequestContext,
      validatedClientId: ClientId
    ): Future[(UserId, SocialLoginResponse)] = {

      val futureUserInfo: Future[UserInfo] = provider match {
        case Google       => googleApi.getUserInfo(token).map(_.toUserInfo(country, language))
        case GooglePeople => googleApi.peopleInfo(token).map(_.toUserInfo(country, language))
        case Facebook     => facebookApi.getUserInfo(token).map(_.toUserInfo(country, language))
      }

      for {
        userInfo <- futureUserInfo
        (user, accountCreation) <- userService.createOrUpdateUserFromSocial(
          userInfo,
          clientIp,
          questionId,
          requestContext
        )
        accessToken <- oauth2DataHandler.createAccessToken(authInfo = AuthInfo(
          user = UserRights(user.userId, user.roles, user.availableQuestions, user.emailVerified),
          clientId = Some(validatedClientId.value),
          scope = None,
          redirectUri = None
        )
        )
      } yield {
        (
          user.userId,
          SocialLoginResponse(
            token = TokenResponse(
              "Bearer",
              accessToken.token,
              accessToken.expiresIn.getOrElse(1L),
              accessToken.refreshToken.getOrElse("")
            ),
            accountCreation = accountCreation
          )
        )
      }
    }

  }
}
