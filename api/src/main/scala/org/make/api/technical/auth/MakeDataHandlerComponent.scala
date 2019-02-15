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

import java.util.concurrent.TimeUnit
import java.util.{Date, NoSuchElementException}

import com.google.common.cache.{Cache, CacheBuilder}
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.ShortenedNames
import org.make.api.user.PersistentUserServiceComponent
import org.make.core.DateHelper
import org.make.core.auth.{Client, ClientId, Token, UserRights}
import org.make.core.user.{User, UserId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success
import scalaoauth2.provider._

trait MakeDataHandlerComponent {
  def oauth2DataHandler: MakeDataHandler
}

trait MakeDataHandler extends DataHandler[UserRights] {
  def removeTokenByUserId(userId: UserId): Future[Int]
}

trait DefaultMakeDataHandlerComponent extends MakeDataHandlerComponent with StrictLogging with ShortenedNames {
  this: PersistentTokenServiceComponent
    with PersistentUserServiceComponent
    with PersistentClientServiceComponent
    with OauthTokenGeneratorComponent
    with MakeSettingsComponent =>

  val oauth2DataHandler = new DefaultMakeDataHandler

  class DefaultMakeDataHandler extends MakeDataHandler {

    lazy val validityDurationAccessTokenSeconds: Int = makeSettings.Oauth.accessTokenLifetime
    lazy val validityDurationRefreshTokenSeconds: Int = makeSettings.Oauth.refreshTokenLifetime

    private val accessTokenCache: Cache[String, AccessToken] =
      CacheBuilder
        .newBuilder()
        .expireAfterWrite(20, TimeUnit.MINUTES)
        .build[String, AccessToken]()

    private val authInfoByAccessTokenCache: Cache[String, AuthInfo[UserRights]] =
      CacheBuilder
        .newBuilder()
        .expireAfterWrite(20, TimeUnit.MINUTES)
        .build[String, AuthInfo[UserRights]]()

    private def toAccessToken(token: Token): AccessToken = {
      AccessToken(
        token = token.accessToken,
        refreshToken = token.refreshToken,
        scope = token.scope,
        lifeSeconds = Some(token.expiresIn.toLong),
        createdAt = Date.from(token.createdAt.getOrElse(DateHelper.now()).toInstant)
      )
    }

    override def validateClient(maybeCredential: Option[ClientCredential],
                                request: AuthorizationRequest): Future[Boolean] = {
      maybeCredential match {
        case Some(ClientCredential(clientId, secret)) =>
          persistentClientService.findByClientIdAndSecret(clientId, secret).map(_.isDefined)
        case _ => Future.successful(false)
      }

      // TODO: handle scope validation
    }

    override def findUser(maybeCredential: Option[ClientCredential],
                          request: AuthorizationRequest): Future[Option[UserRights]] = {
      //TODO: client.scope must be considered in the user serialization
      maybeCredential match {
        case Some(ClientCredential(clientId, secret)) =>
          val futureClient: Future[Option[Client]] = persistentClientService.findByClientIdAndSecret(clientId, secret)
          def futureFoundUser: Future[Option[User]] =
            persistentUserService.findByEmailAndPassword(
              request.requireParam("username").toLowerCase(),
              request.requireParam("password")
            )
          futureClient.flatMap {
            case Some(_) =>
              futureFoundUser.map(_.map(user => UserRights(user.userId, user.roles, user.availableQuestions)))
            case _ => Future.successful(None)
          }
        case _ => Future.successful(None)
      }
    }

    override def createAccessToken(authInfo: AuthInfo[UserRights]): Future[AccessToken] = {
      val futureAccessTokens = oauthTokenGenerator.generateAccessToken()
      val futureRefreshTokens = oauthTokenGenerator.generateRefreshToken()

      val clientId: String = authInfo.clientId.getOrElse(makeSettings.Authentication.defaultClientId)

      val futureClient = persistentClientService.get(ClientId(clientId))
      val futureResult: Future[(Token, String, String)] = for {
        (accessToken, _)  <- futureAccessTokens
        (refreshToken, _) <- futureRefreshTokens
        client            <- futureClient
      } yield
        (
          Token(
            accessToken = accessToken,
            refreshToken = Some(refreshToken),
            scope = None,
            expiresIn = validityDurationAccessTokenSeconds,
            user = authInfo.user,
            client = client.getOrElse(throw new IllegalArgumentException(s"Client with id $clientId not found"))
          ),
          accessToken,
          refreshToken
        )

      futureResult.flatMap { result =>
        val (token, accessToken, refreshToken) = result
        persistentTokenService
          .persist(token)
          .map(_.copy(accessToken = accessToken, refreshToken = Some(refreshToken)))
      }.map(toAccessToken)
    }

    override def getStoredAccessToken(authInfo: AuthInfo[UserRights]): Future[Option[AccessToken]] = {
      persistentTokenService.findByUserId(authInfo.user.userId).map(_.map(toAccessToken))
    }

    override def refreshAccessToken(authInfo: AuthInfo[UserRights], refreshToken: String): Future[AccessToken] = {
      def findByRefreshTokenOrFail(refreshToken: String): Future[Token] =
        persistentTokenService.findByRefreshToken(refreshToken).flatMap {
          case Some(token) => Future.successful(token)
          case None        => Future.failed(new NoSuchElementException(s"Refresh token $refreshToken not found"))
        }

      for {
        token       <- findByRefreshTokenOrFail(refreshToken)
        _           <- persistentTokenService.deleteByAccessToken(token.accessToken)
        accessToken <- createAccessToken(authInfo)
      } yield accessToken
    }

    override def findAuthInfoByCode(code: String): Future[Option[AuthInfo[UserRights]]] = {
      // TODO: implement when needed Authorization Code Grant
      ???
    }

    override def deleteAuthCode(code: String): Future[Unit] = {
      // TODO: implement when needed Authorization Code Grant
      ???
    }

    override def findAuthInfoByRefreshToken(refreshToken: String): Future[Option[AuthInfo[UserRights]]] = {
      persistentTokenService.findByRefreshToken(refreshToken).flatMap {
        case Some(token) =>
          Future.successful(
            Some(
              AuthInfo(
                user = token.user,
                clientId = Some(token.client.clientId.value),
                scope = token.scope,
                redirectUri = None
              )
            )
          )
        case None => Future.successful(None)
      }
    }

    override def findAuthInfoByAccessToken(accessToken: AccessToken): Future[Option[AuthInfo[UserRights]]] = {
      Option(authInfoByAccessTokenCache.getIfPresent(accessToken.token))
        .map(authInfo => Future.successful(Some(authInfo)))
        .getOrElse {
          persistentTokenService.findByAccessToken(accessToken.token).flatMap[Option[AuthInfo[UserRights]]] {
            case Some(token) =>
              val authInfo = AuthInfo(
                user = token.user,
                clientId = Some(token.client.clientId.value),
                scope = token.scope,
                redirectUri = None
              )
              authInfoByAccessTokenCache.put(accessToken.token, authInfo)
              Future.successful(Some(authInfo))
            case None => Future.successful(None)
          }
        }
    }

    override def findAccessToken(token: String): Future[Option[AccessToken]] = {
      Option(accessTokenCache.getIfPresent(token)).map(token => Future.successful(Some(token))).getOrElse {
        val future = persistentTokenService.findByAccessToken(token).map(_.map(toAccessToken))
        future.onComplete {
          case Success(Some(userToken)) => accessTokenCache.put(token, userToken)
          case _                        =>
        }
        future
      }
    }

    override def removeTokenByUserId(userId: UserId): Future[Int] = {
      persistentTokenService.deleteByUserId(userId)
    }
  }
}
