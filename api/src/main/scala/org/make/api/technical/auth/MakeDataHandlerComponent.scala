package org.make.api.technical.auth

import java.time.ZonedDateTime
import java.util.{Date, NoSuchElementException}

import com.typesafe.scalalogging.StrictLogging
import org.make.api.technical.{IdGeneratorComponent, ShortenedNames}
import org.make.api.user.PersistentUserServiceComponent
import org.make.core.auth.{Client, ClientId, Token}
import org.make.core.user.User
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scalaoauth2.provider._

trait MakeDataHandlerComponent extends StrictLogging with ShortenedNames {
  this: PersistentTokenServiceComponent
    with PersistentUserServiceComponent
    with PersistentClientServiceComponent
    with IdGeneratorComponent
    with OauthTokenGeneratorComponent =>

  def oauth2DataHandler: MakeDataHandler
  val validityDurationAccessTokenSeconds: Int = 30.minutes.toSeconds.toInt
  val validityDurationRefreshTokenSeconds: Int = 4.hours.toSeconds.toInt

  class MakeDataHandler(implicit val ctx: ExecutionContext) extends DataHandler[User] {

    private def toAccessToken(token: Token): AccessToken = {
      AccessToken(
        token = token.accessToken,
        refreshToken = token.refreshToken,
        scope = token.scope,
        lifeSeconds = Some(token.expiresIn.toLong),
        createdAt = Date.from(token.createdAt.getOrElse(ZonedDateTime.now).toInstant)
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
            persistentUserService.findByEmailAndHashedPassword(
              request.requireParam("username"),
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

      val clientId: String = authInfo.clientId.getOrElse(throw new IllegalArgumentException("clientId is required"))

      val futureClient = persistentClientService.get(ClientId(clientId))
      val futureResult: Future[(Token, String, String)] = for {
        (accessToken, hashedAccessToken)   <- futureAccessTokens
        (refreshToken, hashedRefreshToken) <- futureRefreshTokens
        client                             <- futureClient
      } yield
        (
          Token(
            accessToken = hashedAccessToken,
            refreshToken = Some(hashedRefreshToken),
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
  }
}
