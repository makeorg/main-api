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

import org.make.core.auth.Token
import org.make.core.user.UserId

import scala.concurrent.Future

trait PersistentTokenServiceComponent {
  def persistentTokenService: PersistentTokenService
}

trait PersistentTokenService {
  def accessTokenExists(token: String): Future[Boolean]
  def refreshTokenExists(token: String): Future[Boolean]
  def findByRefreshToken(token: String): Future[Option[Token]]
  def get(accessToken: String): Future[Option[Token]]
  def findByUserId(userId: UserId): Future[Option[Token]]
  def persist(token: Token): Future[Token]
  def deleteByAccessToken(accessToken: String): Future[Int]
  def deleteByUserId(userId: UserId): Future[Int]
}
