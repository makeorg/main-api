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

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait PersistentClientServiceComponent {
  def persistentClientService: PersistentClientService
}

trait PersistentClientService {
  def get(clientId: ClientId): Future[Option[Client]]
  def findByClientIdAndSecret(clientId: String, secret: Option[String]): Future[Option[Client]]
  def persist(client: Client): Future[Client]
  def update(client: Client): Future[Option[Client]]
  def search(start: Start, end: Option[End], name: Option[String]): Future[Seq[Client]]
  def count(name: Option[String]): Future[Int]
}
