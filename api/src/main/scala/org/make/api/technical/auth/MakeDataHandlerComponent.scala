package org.make.api.technical.auth

import java.util.{Date, NoSuchElementException}

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.ShortenedNames
import org.make.api.user.PersistentUserServiceComponent
import org.make.core.DateHelper
import org.make.core.auth.{Client, ClientId, Token}
import org.make.core.user.{User, UserId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalaoauth2.provider._

trait MakeDataHandlerComponent {
  def oauth2DataHandler: MakeDataHandler
}

trait MakeDataHandler extends DataHandler[User] {
  def removeTokenByAccessToken(token: String): Future[Int]
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
                          request: AuthorizationRequest): Future[Option[User]] = {
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
            case Some(_) => futureFoundUser
            case _       => Future.successful(None)
          }
        case _ => Future.successful(None)
      }
    }

    override def createAccessToken(authInfo: AuthInfo[User]): Future[AccessToken] = {
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

    override def getStoredAccessToken(authInfo: AuthInfo[User]): Future[Option[AccessToken]] = {
      persistentTokenService.findByUser(authInfo.user).map(_.map(toAccessToken))
    }

    override def refreshAccessToken(authInfo: AuthInfo[User], refreshToken: String): Future[AccessToken] = {
      val futureAccessToken = for {
        affectedRows <- persistentTokenService.deleteByRefreshToken(refreshToken)
        accessToken  <- createAccessToken(authInfo) if affectedRows == 1
      } yield accessToken

      futureAccessToken.recoverWith {
        case e: NoSuchElementException =>
          Future.failed(new NoSuchElementException("Refresh token not found: " + e.getMessage))
      }
    }

    override def findAuthInfoByCode(code: String): Future[Option[AuthInfo[User]]] = {
      // TODO: implement when needed Authorization Code Grant
      ???
    }

    override def deleteAuthCode(code: String): Future[Unit] = {
      // TODO: implement when needed Authorization Code Grant
      ???
    }

    override def findAuthInfoByRefreshToken(refreshToken: String): Future[Option[AuthInfo[User]]] = {
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

    override def findAuthInfoByAccessToken(accessToken: AccessToken): Future[Option[AuthInfo[User]]] = {
      persistentTokenService.findByAccessToken(accessToken.token).flatMap {
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

    override def findAccessToken(token: String): Future[Option[AccessToken]] = {
      persistentTokenService.findByAccessToken(token).map(_.map(toAccessToken))
    }

    override def removeTokenByAccessToken(token: String): Future[Int] = {
      persistentTokenService.deleteByAccessToken(token)
    }

    override def removeTokenByUserId(userId: UserId): Future[Int] = {
      persistentTokenService.deleteByUserId(userId)
    }
  }
}
