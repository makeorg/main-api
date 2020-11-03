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

package org.make.api.technical.directives

import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.server.Directive1
import org.make.api.technical.auth.ClientServiceComponent
import org.make.core.auth.{Client, ClientId}

trait ClientDirectives extends FutureDirectives {
  self: ClientServiceComponent =>

  def extractClient: Directive1[Option[Client]] = {
    optionalHeaderValueByType(Authorization).flatMap {
      case Some(Authorization(BasicHttpCredentials(clientId, clientSecret))) =>
        val secret = clientSecret match {
          case ""    => None
          case other => Some(other)
        }
        provideAsync(clientService.getClient(ClientId(clientId), secret)).flatMap {
          case Left(e)       => failWith(e)
          case Right(client) => provide(Some(client))
        }
      case _ => provide[Option[Client]](None)
    }
  }

  def extractClientOrDefault: Directive1[Client] = {
    extractClient.flatMap {
      case Some(client) => provide(client)
      case None =>
        provideAsync(clientService.getDefaultClient()).flatMap {
          case Left(e)       => failWith(e)
          case Right(client) => provide(client)
        }
    }
  }
}
