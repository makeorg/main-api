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

import org.make.core.auth._
import org.make.core.user.{User, UserId}
import scalaoauth2.provider._

import scala.concurrent.Future

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

  def insufficientRole(user: User, client: Client): String = {
    s"User: ${user.userId} tried to connect to client ${client.clientId} with insufficient roles. " +
      s"Expected one of: ${client.roles.map(_.value).mkString(", ")}." +
      s"Actual: ${user.roles.map(_.value).mkString(", ")}"
  }
}
