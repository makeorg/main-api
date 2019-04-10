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

class PersistentClientServiceIT extends DatabaseTest with DefaultPersistentClientServiceComponent {

  feature("Persist a oauth client") {
    info("As a programmer")
    info("I want to be able to persist a oauth client")

    scenario("Persist a Client and get the persisted CLient") {
      Given("""a client with the values:
          |    - clientId: apiclient
          |    - allowedGrantTypes: first_grant_type,second_grant_type
          |    - secret: secret
          |    - scope: None
          |    - redirectUri: None
        """.stripMargin)
      val client = Client(
        clientId = ClientId("apiclient"),
        allowedGrantTypes = Seq("first_grant_type", "second_grant_type"),
        secret = Some("secret"),
        scope = None,
        redirectUri = None,
        defaultUserId = None
      )

      When("I persist apiclient")
      And("I get the persisted client")

      val futureClient: Future[Client] = persistentClientService.persist(client)

      whenReady(futureClient, Timeout(3.seconds)) { client =>
        Then("result should be an instance of Client")
        client shouldBe a[Client]

        And("clientId should be apiclient")
        client.clientId shouldBe ClientId("apiclient")

        And("allowedGrantTypes should be an instance of Seq")
        client.allowedGrantTypes shouldBe a[Seq[_]]

        And("allowedGrantTypes should be first_grant_type and second_grant_type")
        client.allowedGrantTypes shouldBe Seq("first_grant_type", "second_grant_type")

        And("secret should be an instance of Option")
        client.secret shouldBe a[Option[_]]

        And("secret should be secret")
        client.secret.get shouldBe "secret"

        And("scope should be an instance of Option[String]")
        client.scope shouldBe a[Option[_]]

        And("scope should be secret")
        client.scope shouldBe None

        And("redirectUrl should be an instance of Option[String]")
        client.redirectUri shouldBe a[Option[_]]

        And("redirectUri should be secret")
        client.redirectUri shouldBe None
      }
    }

    scenario("Persist a Client with a duplicate ClientId") {
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
        allowedGrantTypes = Seq("grant_type_custom"),
        secret = Some("secret"),
        scope = None,
        redirectUri = None,
        defaultUserId = None
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

  override protected val cockroachExposedPort: Int = 40006
}
