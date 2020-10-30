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

import java.sql.SQLException

import org.make.api.DatabaseTest
import org.make.core.auth.{Client, ClientId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import org.make.core.technical.Pagination.Start

class PersistentClientServiceIT extends DatabaseTest with DefaultPersistentClientServiceComponent {

  override protected val cockroachExposedPort: Int = 40006

  Feature("Persist a oauth client") {
    info("As a programmer")
    info("I want to be able to persist a oauth client")

    Scenario("Persist a Client and get the persisted CLient") {
      Given("""a client with the values:
          |    - clientId: apiclient
          |    - allowedGrantTypes: first_grant_type,second_grant_type
          |    - secret: secret
          |    - scope: None
          |    - redirectUri: None
        """.stripMargin)
      val client = Client(
        clientId = ClientId("apiclient"),
        name = "client",
        allowedGrantTypes = Seq("first_grant_type", "second_grant_type"),
        secret = Some("secret"),
        scope = None,
        redirectUri = None,
        defaultUserId = None,
        roles = Seq.empty,
        tokenExpirationSeconds = 20
      )

      When("I persist apiclient")
      And("I get the persisted client")

      val futureClient: Future[Client] = persistentClientService.persist(client)

      whenReady(futureClient, Timeout(3.seconds)) { client =>
        And("clientId should be apiclient")
        client.clientId shouldBe ClientId("apiclient")

        And("allowedGrantTypes should be first_grant_type and second_grant_type")
        client.allowedGrantTypes shouldBe Seq("first_grant_type", "second_grant_type")

        And("secret should be secret")
        client.secret should contain("secret")

        And("scope should be an instance of Option[String]")
        client.scope should be(None)

        And("scope should be secret")
        client.scope should be(None)

        And("redirectUrl should be an instance of Option[String]")
        client.redirectUri should be(None)

        And("redirectUri should be secret")
        client.redirectUri should be(None)

        And("tokenExpirationSeconds should be 20")
        client.tokenExpirationSeconds should be(20)
      }
    }

    Scenario("Persist a Client with a duplicate ClientId") {
      Given("""a client with the values:
          |
          |    - clientId: apiclient
          |    - allowedGrantTypes: grant_type_custom
          |    - secret: None
          |    - scope: None
          |    - redirectUri: None
        """.stripMargin)
      val duplicateClient = Client(
        clientId = ClientId("apiclient"),
        name = "client",
        allowedGrantTypes = Seq("grant_type_custom"),
        secret = Some("secret"),
        scope = None,
        redirectUri = None,
        defaultUserId = None,
        roles = Seq.empty,
        tokenExpirationSeconds = 300
      )

      When("I persist client with existing clientId")
      def futureBadClient: Future[Client] = persistentClientService.persist(duplicateClient)

      Then("I get a SQLException")
      intercept[SQLException] {
        logger.info("Expected exception: testing duplicate")
        Await.result(futureBadClient, 5.seconds)
      }
    }
  }

  Feature("A list of oauth clients can be retrieved") {
    val baseClient = Client(
      clientId = ClientId("apiclient-base"),
      name = "client",
      allowedGrantTypes = Seq("first_grant_type", "second_grant_type"),
      secret = Some("secret"),
      scope = None,
      redirectUri = None,
      defaultUserId = None,
      roles = Seq.empty,
      tokenExpirationSeconds = 300
    )

    Scenario("Get a list of all oauth clients") {

      val futurePersistedClientList: Future[Seq[Client]] = for {
        c1 <- persistentClientService.persist(baseClient.copy(clientId = ClientId("apiclient-one")))
        c2 <- persistentClientService.persist(baseClient.copy(clientId = ClientId("apiclient-two")))
        c3 <- persistentClientService.persist(baseClient.copy(clientId = ClientId("apiclient-three")))
      } yield Seq(c1, c2, c3)

      val futureClientsLists: Future[Seq[Client]] = for {
        _            <- futurePersistedClientList
        foundClients <- persistentClientService.search(start = Start.zero, end = None, name = None)
      } yield foundClients

      whenReady(futureClientsLists, Timeout(3.seconds)) { clientsList =>
        clientsList.size >= 3 shouldBe true
        clientsList.map(_.clientId.value).contains("apiclient-three") shouldBe true
      }
    }

    Scenario("Search oauth clients from name") {

      val futurePersistedClientList: Future[Seq[Client]] = for {
        c1 <- persistentClientService.persist(baseClient.copy(clientId = ClientId("42"), name = "name-toto-42"))
        c2 <- persistentClientService.persist(baseClient.copy(clientId = ClientId("21"), name = "name-toto-21"))
        c3 <- persistentClientService.persist(baseClient.copy(clientId = ClientId("1"), name = "other"))
      } yield Seq(c1, c2, c3)

      val futureClientsLists: Future[Seq[Client]] = for {
        _            <- futurePersistedClientList
        foundClients <- persistentClientService.search(start = Start.zero, end = None, name = Some("name-toto"))
      } yield foundClients

      whenReady(futureClientsLists, Timeout(3.seconds)) { clientsList =>
        clientsList.size shouldBe 2
        clientsList.map(_.clientId.value).contains("other") shouldBe false
      }
    }
  }

  Feature("One client can be updated") {
    val baseClient = Client(
      clientId = ClientId("update-apiclient-base"),
      name = "client",
      allowedGrantTypes = Seq("first_grant_type", "second_grant_type"),
      secret = Some("secret"),
      scope = None,
      redirectUri = None,
      defaultUserId = None,
      roles = Seq.empty,
      tokenExpirationSeconds = 300
    )

    Scenario("Update client") {
      val futureClient: Future[Option[Client]] = for {
        _ <- persistentClientService.persist(baseClient)
        c <- persistentClientService.update(baseClient.copy(name = "updated name"))
      } yield c

      whenReady(futureClient, Timeout(3.seconds)) { result =>
        result.map(_.clientId.value) shouldBe Some(baseClient.clientId.value)
        result.map(_.name) shouldBe Some("updated name")
      }
    }

    Scenario("Update client that does not exists") {
      val futureClientId: Future[Option[Client]] =
        persistentClientService.update(baseClient.copy(clientId = ClientId("fake")))

      whenReady(futureClientId, Timeout(3.seconds)) { result =>
        Then("result should be None")
        result shouldBe None
      }
    }
  }

}
