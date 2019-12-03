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

import org.make.api.technical.IdGeneratorComponent
import org.make.core.auth.{Client, ClientId}
import org.make.core.user.{CustomRole, UserId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ClientServiceComponent {
  def clientService: ClientService
}

trait ClientService {
  def getClient(clientId: ClientId): Future[Option[Client]]
  def createClient(name: String,
                   allowedGrantTypes: Seq[String],
                   secret: Option[String],
                   scope: Option[String],
                   redirectUri: Option[String],
                   defaultUserId: Option[UserId],
                   roles: Seq[CustomRole],
                   tokenExpirationSeconds: Int): Future[Client]
  def search(start: Int, end: Option[Int], name: Option[String]): Future[Seq[Client]]
  def updateClient(clientId: ClientId,
                   name: String,
                   allowedGrantTypes: Seq[String],
                   secret: Option[String],
                   scope: Option[String],
                   redirectUri: Option[String],
                   defaultUserId: Option[UserId],
                   roles: Seq[CustomRole],
                   tokenExpirationSeconds: Int): Future[Option[Client]]
  def count(name: Option[String]): Future[Int]
}

trait DefaultClientServiceComponent extends ClientServiceComponent {
  this: PersistentClientServiceComponent with IdGeneratorComponent =>

  override lazy val clientService: ClientService = new DefaultClientService

  class DefaultClientService extends ClientService {

    override def getClient(clientId: ClientId): Future[Option[Client]] = {
      persistentClientService.get(clientId)
    }

    override def createClient(name: String,
                              allowedGrantTypes: Seq[String],
                              secret: Option[String],
                              scope: Option[String],
                              redirectUri: Option[String],
                              defaultUserId: Option[UserId],
                              roles: Seq[CustomRole],
                              tokenExpirationSeconds: Int): Future[Client] = {
      persistentClientService.persist(
        Client(
          clientId = idGenerator.nextClientId(),
          name = name,
          allowedGrantTypes = allowedGrantTypes,
          secret = secret,
          scope = scope,
          redirectUri = redirectUri,
          defaultUserId = defaultUserId,
          roles = roles,
          tokenExpirationSeconds = tokenExpirationSeconds
        )
      )
    }

    override def search(start: Int, end: Option[Int], name: Option[String]): Future[Seq[Client]] = {
      persistentClientService.search(start = start, end = end, name = name)
    }

    override def updateClient(clientId: ClientId,
                              name: String,
                              allowedGrantTypes: Seq[String],
                              secret: Option[String],
                              scope: Option[String],
                              redirectUri: Option[String],
                              defaultUserId: Option[UserId],
                              roles: Seq[CustomRole],
                              tokenExpirationSeconds: Int): Future[Option[Client]] = {
      getClient(clientId).flatMap {
        case Some(client) =>
          persistentClientService
            .update(
              client.copy(
                name = name,
                allowedGrantTypes = allowedGrantTypes,
                secret = secret,
                scope = scope,
                redirectUri = redirectUri,
                defaultUserId = defaultUserId,
                roles = roles,
                tokenExpirationSeconds = tokenExpirationSeconds
              )
            )
        case None => Future.successful(None)
      }
    }

    override def count(name: Option[String]): Future[Int] = {
      persistentClientService.count(name = name)
    }
  }
}
