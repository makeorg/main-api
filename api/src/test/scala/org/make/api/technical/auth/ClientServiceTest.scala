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

import org.make.api.MakeUnitTest
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.core.auth.{Client, ClientId}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ClientServiceTest
    extends MakeUnitTest
    with DefaultClientServiceComponent
    with PersistentClientServiceComponent
    with DefaultIdGeneratorComponent {

  override val persistentClientService: PersistentClientService = mock[PersistentClientService]

  feature("get client") {
    scenario("get client from ClientId") {
      clientService.getClient(ClientId("valid-client"))

      Mockito.verify(persistentClientService).get(ClientId("valid-client"))
    }
  }

  feature("create client") {
    scenario("creating a client success") {
      Mockito
        .when(persistentClientService.get(ArgumentMatchers.any[ClientId]))
        .thenReturn(Future.successful(None))

      val client =
        Client(
          clientId = ClientId("whatever"),
          name = "client",
          allowedGrantTypes = Seq.empty,
          secret = Some("secret"),
          scope = None,
          redirectUri = None,
          defaultUserId = None,
          roles = Seq.empty,
          tokenExpirationSeconds = 300
        )

      Mockito
        .when(persistentClientService.persist(ArgumentMatchers.any[Client]))
        .thenReturn(Future.successful(client))

      val futureNewClient: Future[Client] = clientService.createClient(
        name = "client",
        allowedGrantTypes = Seq.empty,
        secret = Some("secret"),
        scope = None,
        redirectUri = None,
        defaultUserId = None,
        roles = Seq.empty,
        tokenExpirationSeconds = 300
      )

      whenReady(futureNewClient, Timeout(3.seconds)) { _ =>
        Mockito.verify(persistentClientService).persist(ArgumentMatchers.any[Client])
      }
    }
  }

  feature("find clients") {
    scenario("find all clients") {
      Mockito
        .when(persistentClientService.search(start = 0, end = None, name = None))
        .thenReturn(Future.successful(Seq.empty))
      val futureFindAll: Future[Seq[Client]] = clientService.search(start = 0, end = None, name = None)

      whenReady(futureFindAll, Timeout(3.seconds)) { _ =>
        Mockito.verify(persistentClientService).search(start = 0, end = None, name = None)
      }
    }
  }

  feature("update a client") {
    scenario("update a client") {
      val oldClient = Client(
        clientId = ClientId("client"),
        name = "old-client",
        allowedGrantTypes = Seq.empty,
        secret = Some("secret"),
        scope = None,
        redirectUri = None,
        defaultUserId = None,
        roles = Seq.empty,
        tokenExpirationSeconds = 300
      )
      val newClient = Client(
        clientId = ClientId("client"),
        name = "new-client",
        allowedGrantTypes = Seq.empty,
        secret = Some("secret"),
        scope = None,
        redirectUri = None,
        defaultUserId = None,
        roles = Seq.empty,
        tokenExpirationSeconds = 300
      )
      Mockito
        .when(persistentClientService.get(ClientId("client")))
        .thenReturn(Future.successful(Some(oldClient)))
      Mockito
        .when(persistentClientService.update(ArgumentMatchers.any[Client]))
        .thenReturn(Future.successful(Some(newClient)))

      val futureClient: Future[Option[Client]] = clientService.updateClient(
        clientId = oldClient.clientId,
        name = "new-client",
        allowedGrantTypes = Seq.empty,
        secret = Some("secret"),
        scope = None,
        redirectUri = None,
        defaultUserId = None,
        roles = Seq.empty,
        tokenExpirationSeconds = 300
      )

      whenReady(futureClient, Timeout(3.seconds)) { client =>
        client.map(_.name) shouldEqual Some(newClient.name)
      }
    }

    scenario("update an non existent client ") {
      Mockito.when(persistentClientService.get(ClientId("non-existent-client"))).thenReturn(Future.successful(None))

      val futureClient: Future[Option[Client]] = clientService.updateClient(
        clientId = ClientId("non-existent-client"),
        name = "client",
        allowedGrantTypes = Seq.empty,
        secret = Some("secret"),
        scope = None,
        redirectUri = None,
        defaultUserId = None,
        roles = Seq.empty,
        tokenExpirationSeconds = 300
      )

      whenReady(futureClient) { client =>
        client shouldBe empty
      }
    }
  }

}
