package org.make.api.technical.auth

import java.sql.SQLException

import org.make.api.DatabaseTest
import org.make.core.auth.{Client, ClientId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class PersistentClientServiceIT extends DatabaseTest with PersistentClientServiceComponent {
  override val persistentClientService: PersistentClientService = new PersistentClientService

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
        redirectUri = None
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
        redirectUri = None
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
}
