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
import org.make.api.technical.auth.ClientService.ClientError
import org.make.api.technical.auth.MakeDataHandler.{CreatedAtParameter, RefreshTokenExpirationParameter}
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
  def refreshIfTokenIsExpired(token: String): Future[Option[Token]]
}

object MakeDataHandler {
  val RefreshTokenExpirationParameter: String = "refresh_expires_in"
  val CreatedAtParameter: String = "created_at"
}

trait DefaultMakeDataHandlerComponent extends MakeDataHandlerComponent with StrictLogging with ShortenedNames {
  this: PersistentTokenServiceComponent
    with PersistentUserServiceComponent
    with ClientServiceComponent
    with OauthTokenGeneratorComponent
    with IdGeneratorComponent
    with PersistentAuthCodeServiceComponent
    with MakeSettingsComponent =>

  override lazy val oauth2DataHandler = new DefaultMakeDataHandler

  class DefaultMakeDataHandler extends MakeDataHandler {

    private val accessTokenCache: Cache[String, Token] =
      CacheBuilder
        .newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build[String, Token]()

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
        createdAt = Date.from(token.createdAt.getOrElse(DateHelper.now()).toInstant),
        params = Map(
          CreatedAtParameter -> DateHelper.format(token.createdAt.getOrElse(DateHelper.now())),
          RefreshTokenExpirationParameter -> token.refreshExpiresIn.toString
        )
      )
    }

    private def userIsRelatedToClient(client: Client)(user: User): Boolean =
      client.roles.isEmpty || user.roles.exists(client.roles.contains)

    override def validateClient(
      maybeCredential: Option[ClientCredential],
      request: AuthorizationRequest
    ): Future[Boolean] = {
      findClient(maybeCredential, request.grantType).map(_.isRight)
    }

    private def findUser(client: Client, request: AuthorizationRequest): Future[Option[User]] = {
      val eventualMaybeUser: Future[Option[User]] = request match {
        case passwordRequest: PasswordRequest =>
          persistentUserService
            .findByEmailAndPassword(passwordRequest.username.toLowerCase(), passwordRequest.password)
        case reconnectRequest: ReconnectRequest =>
          persistentUserService
            .findByReconnectTokenAndPassword(
              reconnectRequest.reconnectToken,
              reconnectRequest.password,
              client.reconnectExpirationSeconds
            )
        case _: ClientCredentialsRequest =>
          client.defaultUserId match {
            case Some(userId) => persistentUserService.get(userId)
            case None         => Future.successful(None)
          }
        case _ => Future.successful(None)
      }

      eventualMaybeUser.flatMap {
        case Some(user) if !userIsRelatedToClient(client)(user) =>
          Future.failed(ClientAccessUnauthorizedException.insufficientRole(user, client))
        case other => Future.successful(other)
      }
    }

    private def findClient(
      maybeCredentials: Option[ClientCredential],
      grantType: String
    ): Future[Either[ClientError, Client]] = {
      (maybeCredentials match {
        case None =>
          // Do not use the default client when using the client credential grant type
          if (grantType == OAuthGrantType.CLIENT_CREDENTIALS) {
            Future.successful(
              Left(
                ClientError(
                  ClientErrorCode.CredentialsMissing,
                  "Client credentials are mandatory for the client_credentials grant type"
                )
              )
            )
          } else {
            clientService.getDefaultClient()
          }
        case Some(credentials) =>
          clientService.getClient(ClientId(credentials.clientId), credentials.clientSecret)
      }).map {
        case Right(client) =>
          if (client.allowedGrantTypes.contains(grantType)) {
            Right(client)
          } else {
            Left(
              ClientError(ClientErrorCode.ForbiddenGrantType, s"Grant type $grantType is not allowed for this client")
            )
          }
        case error => error
      }
    }

    override def findUser(
      maybeCredential: Option[ClientCredential],
      request: AuthorizationRequest
    ): Future[Option[UserRights]] = {

      findClient(maybeCredential, request.grantType).flatMap {
        case Right(client) => findUser(client, request).map(_.map(UserRights.fromUser))
        case Left(e)       => Future.failed(ClientAccessUnauthorizedException(e.code, e.label))
      }
    }

    override def createAccessToken(authInfo: AuthInfo[UserRights]): Future[AccessToken] = {
      val futureAccessTokens = oauthTokenGenerator.generateAccessToken()
      val futureRefreshTokens = oauthTokenGenerator.generateRefreshToken()

      val clientId: String = authInfo.clientId.getOrElse(makeSettings.Authentication.defaultClientId)

      val futureClient = clientService.getClient(ClientId(clientId))
      val futureResult: Future[Token] = for {
        (accessToken, _)  <- futureAccessTokens
        (refreshToken, _) <- futureRefreshTokens
        maybeClient       <- futureClient
        client <- maybeClient.fold(
          Future.failed[Client](
            ClientAccessUnauthorizedException(ClientErrorCode.UnknownClient, s"Client with id $clientId not found")
          )
        )(Future.successful)
      } yield {
        val maybeRefreshToken =
          if (client.allowedGrantTypes.contains(OAuthGrantType.REFRESH_TOKEN)) {
            Some(refreshToken)
          } else {
            None
          }

        Token(
          accessToken = accessToken,
          refreshToken = maybeRefreshToken,
          scope = None,
          expiresIn = client.tokenExpirationSeconds,
          refreshExpiresIn = client.refreshExpirationSeconds,
          user = authInfo.user,
          client = client
        )
      }

      futureResult
        .flatMap(persistentTokenService.persist)
        .map(toAccessToken)
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
          clientService.getClient(authCode.client).flatMap {
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
                    Future.failed(ClientAccessUnauthorizedException.insufficientRole(user, client))
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
        case Some(token) if !token.isRefreshTokenExpired =>
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
        case _ => Future.successful(None)
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
        .filter(!_.isAccessTokenExpired)
        .map(token => Future.successful(Some(token)))
        .getOrElse {
          val future = persistentTokenService
            .get(token)
            .map(_.filter(!_.isAccessTokenExpired))
          future.onComplete {
            case Success(Some(userToken)) => accessTokenCache.put(token, userToken)
            case _                        =>
          }
          future
        }
        .map(_.map(toAccessToken))
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

      clientService.getClient(clientId).flatMap {
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
            case Some(user) => Future.failed(ClientAccessUnauthorizedException.insufficientRole(user, client))
          }
      }
    }

    override def refreshIfTokenIsExpired(tokenValue: String): Future[Option[Token]] = {
      Option(accessTokenCache.getIfPresent(tokenValue))
        .map(token => Future.successful(Some(token)))
        .getOrElse {
          persistentTokenService.get(tokenValue)
        }
        .flatMap {
          case Some(token @ Token(_, Some(refreshToken), _, _, _, _, _, _, _))
              if token.isAccessTokenExpired &&
                !token.isRefreshTokenExpired =>
            findAuthInfoByAccessToken(toAccessToken(token)).flatMap {
              case Some(authInfo) =>
                refreshAccessToken(authInfo, refreshToken)
                  .flatMap(token => persistentTokenService.get(token.token))
              case _ => Future.successful(None)
            }
          case _ => Future.successful(None)
        }
    }

    override def removeToken(token: String): Future[Unit] = {
      persistentTokenService.deleteByAccessToken(token).map(_ => accessTokenCache.invalidate(token))
    }
  }
}

final case class TokenAlreadyRefreshed(refreshToken: String)
    extends Exception(s"Refresh token $refreshToken has already been refreshed")

final case class ClientAccessUnauthorizedException(error: ClientErrorCode, message: String) extends Exception(message)

object ClientAccessUnauthorizedException {
  def insufficientRole(user: User, client: Client): ClientAccessUnauthorizedException = {
    ClientAccessUnauthorizedException(
      ClientErrorCode.MissingRole,
      s"User: ${user.userId} tried to connect to client ${client.clientId} with insufficient roles. " +
        s"Expected one of: ${client.roles.map(_.value).mkString(", ")}." +
        s"Actual: ${user.roles.map(_.value).mkString(", ")}"
    )
  }
}
