package org.make.api.technical.auth

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.Date

import org.make.api.user.PersistentUserServiceComponent
import org.make.api.technical.{IdGeneratorComponent, ShortenedNames}
import org.make.core.user.User

import scala.concurrent.{ExecutionContext, Future}
import scalaoauth2.provider._

trait MakeDataHandlerComponent {
  this: TokenServiceComponent with PersistentUserServiceComponent with IdGeneratorComponent with ShortenedNames =>

  def oauth2DataHandler: MakeDataHandler
  val validityDurationSeconds = 12000000

  class MakeDataHandler(implicit val ctx: ExecutionContext) extends DataHandler[User] {

    private def toAccessToken(token: Token): AccessToken = {
      AccessToken(
        token = token.id,
        refreshToken = Option(token.refreshToken),
        scope = Option(token.scope),
        lifeSeconds = Some(token.validityDurationSeconds.toLong),
        createdAt = Date.from(token.creationDate.toInstant)
      )
    }

    override def validateClient(maybeCredential: Option[ClientCredential],
                                request: AuthorizationRequest): Future[Boolean] = {
      findUser(maybeCredential, request).map(_.isDefined)
    }

    override def findUser(maybeCredential: Option[ClientCredential],
                          request: AuthorizationRequest): Future[Option[User]] = {
      maybeCredential match {
        case Some(ClientCredential(clientId, Some(secret))) =>
          persistentUserService.findByEmailAndHashedPassword(clientId, secret)
        case _ => Future.successful(None)
      }
    }

    override def createAccessToken(authInfo: AuthInfo[User]): Future[AccessToken] = {
      val user = authInfo.user
      tokenService
        .insert(
          Token(
            id = idGenerator.nextId(),
            refreshToken = idGenerator.nextId(),
            userId = user.userId,
            scope = "user",
            creationDate = ZonedDateTime.now(ZoneOffset.UTC),
            validityDurationSeconds = validityDurationSeconds,
            parameters = ""
          )
        )
        .map(toAccessToken)
    }

    override def getStoredAccessToken(authInfo: AuthInfo[User]): Future[Option[AccessToken]] = {
      tokenService
        .latestTokenForUser(authInfo.user.userId)
        .map(_.map(toAccessToken))
    }

    override def refreshAccessToken(authInfo: AuthInfo[User], refreshToken: String): Future[AccessToken] = {
      tokenService.getTokenByRefreshToken(refreshToken).flatMap {
        case Some(_) => createAccessToken(authInfo)
        case None =>
          Future.failed(new IllegalStateException("Unable to find corresponding token"))
      }

    }

    override def findAuthInfoByCode(code: String): Future[Option[AuthInfo[User]]] = {
      ???
    }

    override def deleteAuthCode(code: String): Future[Unit] = {
      ???
    }

    override def findAuthInfoByRefreshToken(refreshToken: String): Future[Option[AuthInfo[User]]] = {
      tokenService.getTokenByRefreshToken(refreshToken).flatMap {
        case Some(token) =>
          persistentUserService
            .get(token.userId)
            .map(_.map { user =>
              AuthInfo(user = user, clientId = Some(user.email), scope = Some(token.scope), redirectUri = None)
            })
        case None => Future.successful(None)
      }
    }

    override def findAuthInfoByAccessToken(accessToken: AccessToken): Future[Option[AuthInfo[User]]] = {
      for {
        maybeToken <- tokenService.getToken(accessToken.token)
        maybeUser <- findUser(maybeToken)
      } yield
        maybeUser.map { user =>
          val token = maybeToken.get
          AuthInfo(user = user, clientId = Some(user.email), scope = Some(token.scope), redirectUri = None)
        }
    }

    private def findUser(maybeToken: Option[Token]): Future[Option[User]] =
      maybeToken match {
        case Some(token) => persistentUserService.get(token.userId)
        case None        => Future.successful(None)
      }

    override def findAccessToken(token: String): Future[Option[AccessToken]] = {
      tokenService.getToken(token).map(_.map(toAccessToken))
    }
  }

}
