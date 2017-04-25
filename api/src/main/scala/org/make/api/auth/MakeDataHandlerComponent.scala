package org.make.api.auth

import org.make.core.citizen.Citizen

import scala.concurrent.Future
import scalaoauth2.provider._


trait MakeDataHandlerComponent {
  this: TokenServiceComponent =>

  def dataHandler: MakeDataHandler


  class MakeDataHandler extends DataHandler[Citizen] {

    override def validateClient(maybeCredential: Option[ClientCredential], request: AuthorizationRequest): Future[Boolean] = ???

    override def findUser(maybeCredential: Option[ClientCredential], request: AuthorizationRequest): Future[Option[Citizen]] = ???

    override def createAccessToken(authInfo: AuthInfo[Citizen]): Future[AccessToken] = ???

    override def getStoredAccessToken(authInfo: AuthInfo[Citizen]): Future[Option[AccessToken]] = ???

    override def refreshAccessToken(authInfo: AuthInfo[Citizen], refreshToken: String): Future[AccessToken] = ???

    override def findAuthInfoByCode(code: String): Future[Option[AuthInfo[Citizen]]] = ???

    override def deleteAuthCode(code: String): Future[Unit] = ???

    override def findAuthInfoByRefreshToken(refreshToken: String): Future[Option[AuthInfo[Citizen]]] = ???

    override def findAuthInfoByAccessToken(accessToken: AccessToken): Future[Option[AuthInfo[Citizen]]] = ???

    override def findAccessToken(token: String): Future[Option[AccessToken]] = ???
  }

}