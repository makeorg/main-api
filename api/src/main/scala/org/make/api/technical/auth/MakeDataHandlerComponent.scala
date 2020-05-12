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

import java.util.Date
import java.util.concurrent.TimeUnit

import com.google.common.cache.{Cache, CacheBuilder}
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.{IdGeneratorComponent, ShortenedNames}
import org.make.api.user.PersistentUserServiceComponent
import org.make.core.DateHelper
import org.make.core.auth._
import org.make.core.user.{User, UserId}
import scalaoauth2.provider._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success

trait MakeDataHandlerComponent {
  def oauth2DataHandler: MakeDataHandler
}

trait MakeDataHandler extends DataHandler[UserRights] {
  def createAuthorizationCode(
    userId: UserId,
    clientId: ClientId,
    scope: Option[String],
    redirectUri: Option[String]
  ): Future[Option[AuthCode]]
  def removeTokenByUserId(userId: UserId): Future[Int]
  def removeToken(token: String): Future[Unit]
  def refreshIfTokenIsExpired(token: String): Future[Option[AccessToken]]
}

trait DefaultMakeDataHandlerComponent extends MakeDataHandlerComponent with StrictLogging with ShortenedNames {
  this: PersistentTokenServiceComponent
    with PersistentUserServiceComponent
    with PersistentClientServiceComponent
    with OauthTokenGeneratorComponent
    with IdGeneratorComponent
    with PersistentAuthCodeServiceComponent
    with MakeSettingsComponent =>

  override lazy val oauth2DataHandler = new DefaultMakeDataHandler

  class DefaultMakeDataHandler extends MakeDataHandler {

    lazy val validityDurationRefreshTokenSeconds: Int = makeSettings.Oauth.refreshTokenLifetime
    lazy val validityDurationReconnectTokenSeconds: Int = makeSettings.Oauth.reconnectTokenLifetime

    private val accessTokenCache: Cache[String, AccessToken] =
      CacheBuilder
        .newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build[String, AccessToken]()

    private val authInfoByAccessTokenCache: Cache[String, AuthInfo[UserRights]] =
      CacheBuilder
        .newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
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

    private def userIsRelatedToClient(client: Client)(user: User): Boolean =
      client.roles.isEmpty || user.roles.exists(client.roles.contains)

    override def validateClient(
      maybeCredential: Option[ClientCredential],
      request: AuthorizationRequest
    ): Future[Boolean] = {
      maybeCredential match {
        case Some(ClientCredential(clientId, secret)) =>
          persistentClientService.findByClientIdAndSecret(clientId, secret).map(_.isDefined)
        case _ => Future.successful(false)
      }

      // TODO: handle scope validation
    }

    private def findUser(client: Client, request: AuthorizationRequest): Future[Option[User]] =
      request match {
        case passwordRequest: PasswordRequest   => findUserForPasswordFlow(client, passwordRequest)
        case reconnectRequest: ReconnectRequest => findUserForReconnectFlow(client, reconnectRequest)
        case _: ClientCredentialsRequest        => findUserForClientCredentialsFlow(client)
        case _                                  => Future.successful(None)
      }

    private def findUserForClientCredentialsFlow(client: Client): Future[Option[User]] = {
      client.defaultUserId match {
        case Some(userId) =>
          persistentUserService
            .get(userId)
            .flatMap {
              case Some(user) if !userIsRelatedToClient(client)(user) =>
                Future.failed(ClientAccessUnauthorizedException(user, client))
              case other => Future.successful(other)
            }
        case None => Future.successful(None)
      }
    }

    private def findUserForReconnectFlow(client: Client, reconnectRequest: ReconnectRequest): Future[Option[User]] = {
      persistentUserService
        .findByReconnectTokenAndPassword(
          reconnectRequest.reconnectToken,
          reconnectRequest.password,
          validityDurationReconnectTokenSeconds
        )
        .flatMap {
          case Some(user) if !userIsRelatedToClient(client)(user) =>
            Future.failed(ClientAccessUnauthorizedException(user, client))
          case other => Future.successful(other)
        }
    }

    private def findUserForPasswordFlow(client: Client, passwordRequest: PasswordRequest): Future[Option[User]] = {
      persistentUserService
        .findByEmailAndPassword(passwordRequest.username.toLowerCase(), passwordRequest.password)
        .flatMap {
          case Some(user) if !userIsRelatedToClient(client)(user) =>
            Future.failed(ClientAccessUnauthorizedException(user, client))
          case other => Future.successful(other)
        }
    }

    override def findUser(
      maybeCredential: Option[ClientCredential],
      request: AuthorizationRequest
    ): Future[Option[UserRights]] = {

      val findClient: Future[Option[Client]] = request match {
        // if client information is not provided in password flow, use the default ones
        case _: PasswordRequest =>
          maybeCredential match {
            case Some(ClientCredential(clientId, clientSecret)) =>
              persistentClientService.findByClientIdAndSecret(clientId, clientSecret)
            case None =>
              persistentClientService.get(ClientId(makeSettings.Authentication.defaultClientId))
          }
        case _: ReconnectRequest =>
          maybeCredential match {
            case Some(ClientCredential(clientId, clientSecret)) =>
              persistentClientService.findByClientIdAndSecret(clientId, clientSecret)
            case None =>
              persistentClientService.get(ClientId(makeSettings.Authentication.defaultClientId))
          }
        // For other flows going here, the client is retrieved normally
        // this means ClientCredentials and Implicit flows. Implicit will probably need more work
        case _ =>
          maybeCredential match {
            case Some(ClientCredential(clientId, clientSecret)) =>
              persistentClientService.findByClientIdAndSecret(clientId, clientSecret)
            case None => Future.successful(None)
          }
      }

      findClient.flatMap {
        case Some(client) =>
          findUser(client, request).map(
            _.map(user => UserRights(user.userId, user.roles, user.availableQuestions, user.emailVerified))
          )
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
        maybeClient       <- futureClient
      } yield {
        val client = maybeClient.getOrElse(throw new IllegalArgumentException(s"Client with id $clientId not found"))
        (
          Token(
            accessToken = accessToken,
            refreshToken = Some(refreshToken),
            scope = None,
            expiresIn = client.tokenExpirationSeconds,
            user = authInfo.user,
            client = client
          ),
          accessToken,
          refreshToken
        )
      }

      futureResult.flatMap { result =>
        val (token, accessToken, refreshToken) = result
        persistentTokenService
          .persist(token)
          .map(_.copy(accessToken = accessToken, refreshToken = Some(refreshToken)))
      }.map(toAccessToken)
    }

    override def getStoredAccessToken(authInfo: AuthInfo[UserRights]): Future[Option[AccessToken]] = {
      // Force to issue a fresh token every time a user connects.
      // This way, authentication will not be shared across devices
      Future.successful(None)
    }

    override def refreshAccessToken(authInfo: AuthInfo[UserRights], refreshToken: String): Future[AccessToken] = {
      def findByRefreshTokenOrFail(refreshToken: String): Future[Token] =
        persistentTokenService.findByRefreshToken(refreshToken).flatMap {
          case Some(token) => Future.successful(token)
          case None        => Future.failed(TokenAlreadyRefreshed(refreshToken))
        }

      for {
        token       <- findByRefreshTokenOrFail(refreshToken)
        _           <- persistentTokenService.deleteByAccessToken(token.accessToken)
        accessToken <- createAccessToken(authInfo)
      } yield accessToken
    }

    override def findAuthInfoByCode(code: String): Future[Option[AuthInfo[UserRights]]] = {
      persistentAuthCodeService.findByCode(code).flatMap {
        case None => Future.successful(None)
        case Some(authCode) =>
          persistentClientService.get(authCode.client).flatMap {
            case Some(client) =>
              persistentUserService
                .get(authCode.user)
                .flatMap {
                  case Some(user) if userIsRelatedToClient(client)(user) =>
                    Future.successful(
                      Some(
                        AuthInfo(
                          user = UserRights(user.userId, user.roles, user.availableQuestions, user.emailVerified),
                          clientId = Some(authCode.client.value),
                          scope = authCode.scope,
                          redirectUri = authCode.redirectUri
                        )
                      )
                    )
                  case None => Future.successful(None)
                  case Some(user) =>
                    Future.failed(ClientAccessUnauthorizedException(user, client))
                }
            case _ => Future.successful(None)
          }
      }
    }

    override def deleteAuthCode(code: String): Future[Unit] = {
      persistentAuthCodeService.deleteByCode(code)
    }

    override def findAuthInfoByRefreshToken(refreshToken: String): Future[Option[AuthInfo[UserRights]]] = {
      persistentTokenService.findByRefreshToken(refreshToken).flatMap {
        case Some(token) if toAccessToken(token.copy(expiresIn = validityDurationRefreshTokenSeconds)).isExpired =>
          Future.successful(None)
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
          persistentTokenService.get(accessToken.token).flatMap[Option[AuthInfo[UserRights]]] {
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
      Option(accessTokenCache.getIfPresent(token))
        .filter(!_.isExpired)
        .map(token => Future.successful(Some(token)))
        .getOrElse {
          val future = persistentTokenService.get(token).map(_.map(toAccessToken).filter(!_.isExpired))
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

    override def createAuthorizationCode(
      userId: UserId,
      clientId: ClientId,
      scope: Option[String],
      redirectUri: Option[String]
    ): Future[Option[AuthCode]] = {

      persistentClientService.get(clientId).flatMap {
        case None => Future.successful(None)
        case Some(client) =>
          persistentUserService.get(userId).flatMap {
            case None => Future.successful(None)
            case Some(user) if userIsRelatedToClient(client)(user) =>
              persistentAuthCodeService
                .persist(
                  AuthCode(
                    authorizationCode = idGenerator.nextId(),
                    scope = scope,
                    redirectUri = redirectUri,
                    createdAt = DateHelper.now(),
                    expiresIn = client.tokenExpirationSeconds,
                    user = userId,
                    client = clientId
                  )
                )
                .map(Some(_))
            case Some(user) => Future.failed(ClientAccessUnauthorizedException(user, client))
          }
      }
    }

    override def refreshIfTokenIsExpired(tokenValue: String): Future[Option[AccessToken]] = {
      Option(accessTokenCache.getIfPresent(tokenValue))
        .map(token => Future.successful(Some(token)))
        .getOrElse {
          persistentTokenService.get(tokenValue).map(_.map(toAccessToken))
        }
        .flatMap {
          case Some(token @ AccessToken(_, Some(refreshToken), _, _, _, _))
              if token.isExpired &&
                !token
                  .copy(lifeSeconds = Some(validityDurationRefreshTokenSeconds.toLong))
                  .isExpired =>
            findAuthInfoByAccessToken(token).flatMap {
              case Some(authInfo) => refreshAccessToken(authInfo, refreshToken).map(Some(_))
              case _              => Future.successful(None)
            }
          case _ => Future.successful(None)
        }
    }

    override def removeToken(token: String): Future[Unit] = {
      persistentTokenService.deleteByAccessToken(token).map(_ => accessTokenCache.invalidate(token))
    }
  }
}

case class TokenAlreadyRefreshed(refreshToken: String)
    extends Exception(s"Refresh token $refreshToken has already been refreshed")

case class ClientAccessUnauthorizedException(user: User, client: Client)
    extends Exception(
      s"User: ${user.userId} tried to connect to client ${client.clientId} with insufficient roles. " +
        s"Expected one of: ${client.roles.map(_.shortName).mkString(", ")}." +
        s"Actual: ${user.roles.map(_.shortName).mkString(", ")}"
    )
