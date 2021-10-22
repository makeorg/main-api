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

import scalaoauth2.provider.{
  AuthInfo,
  AuthorizationHandler,
  AuthorizationRequest,
  ClientCredential,
  GrantHandler,
  GrantHandlerResult,
  InvalidGrant
}

import scala.concurrent.{ExecutionContext, Future}

class Reconnect extends GrantHandler {

  override def handleRequest[U](
    maybeValidatedClientCred: Option[ClientCredential],
    request: AuthorizationRequest,
    handler: AuthorizationHandler[U]
  )(implicit ctx: ExecutionContext): Future[GrantHandlerResult[U]] = {

    val reconnectRequest = ReconnectRequest(request)
    handler
      .findUser(maybeValidatedClientCred, reconnectRequest)
      .flatMap {
        case Some(user) => Future.successful(user)
        case _          => Future.failed(new InvalidGrant("reconnect token or password is incorrect"))
      }
      .flatMap { user =>
        val scope = reconnectRequest.scope
        val authInfo = AuthInfo(user, maybeValidatedClientCred.map(_.clientId), scope, None)

        issueAccessToken(handler, authInfo)
      }
  }
}

object Reconnect {
  val RECONNECT_TOKEN: String = "reconnect_token"
}

final case class ReconnectRequest(request: AuthorizationRequest)
    extends AuthorizationRequest(request.headers, request.params) {

  def reconnectToken: String = requireParam("reconnect_token")

  def password: String = requireParam("password")
}
