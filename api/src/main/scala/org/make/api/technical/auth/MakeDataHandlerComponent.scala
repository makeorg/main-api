package org.make.api.technical.auth

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.Date

import org.make.api.citizen.PersistentCitizenServiceComponent
import org.make.api.technical.{IdGeneratorComponent, ShortenedNames}
import org.make.core.citizen.Citizen

import scala.concurrent.{ExecutionContext, Future}
import scalaoauth2.provider._

trait MakeDataHandlerComponent {
  this: TokenServiceComponent with PersistentCitizenServiceComponent with IdGeneratorComponent with ShortenedNames =>

  def oauth2DataHandler: MakeDataHandler

  class MakeDataHandler(implicit val ctx: ExecutionContext) extends DataHandler[Citizen] {

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
                          request: AuthorizationRequest): Future[Option[Citizen]] = {
      maybeCredential match {
        case Some(ClientCredential(clientId, Some(secret))) =>
          persistentCitizenService.find(clientId, secret)
        case _ => Future.successful(None)
      }
    }

    override def createAccessToken(authInfo: AuthInfo[Citizen]): Future[AccessToken] = {
      val citizen = authInfo.user
      tokenService
        .insert(
          Token(
            id = idGenerator.nextId(),
            refreshToken = idGenerator.nextId(),
            citizenId = citizen.citizenId,
            scope = "user",
            creationDate = ZonedDateTime.now(ZoneOffset.UTC),
            validityDurationSeconds = 12000000,
            parameters = ""
          )
        )
        .map(toAccessToken)
    }

    override def getStoredAccessToken(authInfo: AuthInfo[Citizen]): Future[Option[AccessToken]] = {
      tokenService
        .latestTokenForUser(authInfo.user.citizenId)
        .map(_.map(toAccessToken))
    }

    override def refreshAccessToken(authInfo: AuthInfo[Citizen], refreshToken: String): Future[AccessToken] = {
      tokenService.getTokenByRefreshToken(refreshToken).flatMap {
        case Some(_) => createAccessToken(authInfo)
        case None =>
          Future.failed(new IllegalStateException("Unable to find corresponding token"))
      }

    }

    override def findAuthInfoByCode(code: String): Future[Option[AuthInfo[Citizen]]] = {
      println(s"findAuthInfoByCode($code)")
      ???
    }

    override def deleteAuthCode(code: String): Future[Unit] = {
      println(s"deleteAuthCode($code)")
      ???
    }

    override def findAuthInfoByRefreshToken(refreshToken: String): Future[Option[AuthInfo[Citizen]]] = {
      tokenService.getTokenByRefreshToken(refreshToken).flatMap {
        case Some(token) =>
          persistentCitizenService
            .get(token.citizenId)
            .map(_.map { citizen =>
              AuthInfo(user = citizen, clientId = Some(citizen.email), scope = Some(token.scope), redirectUri = None)
            })
        case None => Future.successful(None)
      }
    }

    override def findAuthInfoByAccessToken(accessToken: AccessToken): Future[Option[AuthInfo[Citizen]]] = {
      for {
        maybeToken <- tokenService.getToken(accessToken.token)
        maybeCitizen <- findUser(maybeToken)
      } yield
        maybeCitizen.map { citizen =>
          val token = maybeToken.get
          AuthInfo(user = citizen, clientId = Some(citizen.email), scope = Some(token.scope), redirectUri = None)
        }
    }

    private def findUser(maybeToken: Option[Token]): Future[Option[Citizen]] =
      maybeToken match {
        case Some(token) => persistentCitizenService.get(token.citizenId)
        case None => Future.successful(None)
      }

    override def findAccessToken(token: String): Future[Option[AccessToken]] = {
      tokenService.getToken(token).map(_.map(toAccessToken))
    }
  }

}
