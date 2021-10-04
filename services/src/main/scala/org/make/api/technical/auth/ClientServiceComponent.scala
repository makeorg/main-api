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

import org.make.core.auth.{Client, ClientId}
import org.make.core.technical.Pagination._
import org.make.core.user.{CustomRole, UserId}
import scalaoauth2.provider.OAuthError

import scala.concurrent.Future

trait ClientServiceComponent {
  def clientService: ClientService
}

trait ClientService {
  def getClient(clientId: ClientId): Future[Option[Client]]
  def createClient(
    name: String,
    allowedGrantTypes: Seq[String],
    secret: Option[String],
    scope: Option[String],
    redirectUri: Option[String],
    defaultUserId: Option[UserId],
    roles: Seq[CustomRole],
    tokenExpirationSeconds: Int,
    refreshExpirationSeconds: Int,
    reconnectExpirationSeconds: Int
  ): Future[Client]
  def search(start: Start, end: Option[End], name: Option[String]): Future[Seq[Client]]
  def updateClient(
    clientId: ClientId,
    name: String,
    allowedGrantTypes: Seq[String],
    secret: Option[String],
    scope: Option[String],
    redirectUri: Option[String],
    defaultUserId: Option[UserId],
    roles: Seq[CustomRole],
    tokenExpirationSeconds: Int,
    refreshExpirationSeconds: Int,
    reconnectExpirationSeconds: Int
  ): Future[Option[Client]]
  def count(name: Option[String]): Future[Int]
  def getClient(clientId: ClientId, secret: Option[String]): Future[Either[OAuthError, Client]]
  def getDefaultClient(): Future[Either[OAuthError, Client]]
}
