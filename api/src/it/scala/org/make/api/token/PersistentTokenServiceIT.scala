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

package org.make.api.token

import java.sql.SQLException
import java.time.ZonedDateTime

import org.make.api.technical.auth.{DefaultPersistentClientServiceComponent, DefaultPersistentTokenServiceComponent}
import org.make.api.user.DefaultPersistentUserServiceComponent
import org.make.api.{DatabaseTest, TestUtilsIT}
import org.make.core.DateHelper
import org.make.core.auth.{Client, ClientId, Token, UserRights}
import org.make.core.user.{Role, User, UserId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class PersistentTokenServiceIT
    extends DatabaseTest
    with DefaultPersistentTokenServiceComponent
    with DefaultPersistentUserServiceComponent
    with DefaultPersistentClientServiceComponent {

  val before: Option[ZonedDateTime] = Some(ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]"))
  val now: ZonedDateTime = DateHelper.now()

  val user: User = TestUtilsIT.user(
    id = UserId("1"),
    email = "doe@example.com",
    firstName = Some("John"),
    lastName = Some("Doe"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = before,
    roles = Seq(Role.RoleAdmin, Role.RoleCitizen)
  )
  val client: Client = Client(
    clientId = ClientId("apiclient"),
    name = "client",
    allowedGrantTypes = Seq("grant_type", "other_grant_type"),
    secret = Some("secret"),
    scope = None,
    redirectUri = None,
    defaultUserId = None,
    roles = Seq.empty,
    tokenExpirationSeconds = 300,
    refreshExpirationSeconds = 400,
    reconnectExpirationSeconds = 50
  )
  val exampleToken: Token = Token(
    accessToken = "ACCESS_TOKEN",
    refreshToken = Some("REFRESH_TOKEN"),
    scope = Some("scope"),
    expiresIn = 42,
    refreshExpiresIn = 45,
    user = UserRights(
      userId = user.userId,
      roles = user.roles,
      availableQuestions = user.availableQuestions,
      emailVerified = true
    ),
    client = client
  )

  Feature("The app can persist a token") {
    info("As a programmer")
    info("I want to be able to persist a Token")

    Scenario("Persist a Token and get the persisted Token") {
      Given("a user John Doe and a client apiclient")
      And("""a Token with the values:
          |
          |    - accessToken: "ACCESS_TOKEN"
          |    - refreshToken: "REFRESH_TOKEN"
          |    - scope: "scope"
          |    - expiresIn: 42
          |    - user: "John Doe"
          |    - client: "apiclient"
        """.stripMargin)
      val futureClient: Future[Client] = persistentClientService.persist(client)
      val futureUser: Future[User] = persistentUserService.persist(user)
      def futureTokenPersister: Future[Token] = persistentTokenService.persist(exampleToken)

      When("""I persist a Token with the user "John Doe" and the client "apiclient"""")
      And("I get the persisted Token")
      val futureFoundToken: Future[Option[Token]] = for {
        _     <- futureUser
        _     <- futureClient
        _     <- futureTokenPersister
        token <- persistentTokenService.get(exampleToken.accessToken)
      } yield token

      whenReady(futureFoundToken, Timeout(3.seconds)) { result =>
        Then("result must an instance of Token")
        val userToken = result.get

        And("""the access token must be "ACCESS_TOKEN"""")
        userToken.accessToken shouldBe exampleToken.accessToken

        And("""the refresh token must be "REFRESH_TOKEN"""")
        userToken.accessToken shouldBe exampleToken.accessToken

        And("the expires in must be 42")
        userToken.accessToken shouldBe exampleToken.accessToken

        And("""the user must be "John Doe"""")
        userToken.user.userId.value shouldBe exampleToken.user.userId.value

        And("""the client must be "apiclient"""")
        userToken.client.clientId.value shouldBe exampleToken.client.clientId.value

        And("the Token cannot be persisted if duplicate")
        intercept[SQLException] {
          logger.info("Expected exception: testing duplicate")
          Await.result(futureTokenPersister, 5.seconds)
        }
      }
    }
  }

  Feature("Find a Token from a User") {
    Scenario("Find a Token from a valid user") {
      Given("a valid User")
      When("a token is searched from this User")
      val futureFoundToken: Future[Option[Token]] = persistentTokenService.findByUserId(user.userId)

      whenReady(futureFoundToken, Timeout(3.seconds)) { result =>
        Then("the user's token is returned")
        val userToken = result.get

        And("""the access token must be "ACCESS_TOKEN"""")
        userToken.accessToken shouldBe exampleToken.accessToken

        And("""the refresh token must be "REFRESH_TOKEN"""")
        userToken.accessToken shouldBe exampleToken.accessToken

        And("""the token user must be "John Doe"""")
        userToken.user.userId.value shouldBe exampleToken.user.userId.value
      }
    }
  }

  Feature("Find a Token from an access token") {
    info("As a programmer")
    info("I want to be able to get a Token from an access token")

    Scenario("Find a Token from a valid access token") {
      Given("a valid User")
      And("a persisted token")
      val accessToken = exampleToken.copy(accessToken = "VALID_TOKEN")

      When("I get a Token from access token")
      val futureFoundToken: Future[Option[Token]] = for {
        _     <- persistentTokenService.persist(accessToken)
        token <- persistentTokenService.get("VALID_TOKEN")
      } yield token

      whenReady(futureFoundToken, Timeout(3.seconds)) { result =>
        Then("an Option of Token is returned")
        val userToken = result.get

        And("""the access token should be "VALID_TOKEN"""")
        userToken.accessToken shouldBe accessToken.accessToken
      }
    }

    Scenario("Find a Token from an nonexistent access token") {
      Given("an nonexistent access token")
      When("I get a Token from access token")
      val futureNotFoundToken = persistentTokenService.get("NON_TOKEN")

      whenReady(futureNotFoundToken, Timeout(3.seconds)) { token =>
        Then("an empty result is returned")
        token shouldBe empty
      }
    }
  }

  Feature("Find a Token from a refresh token") {
    info("As a programmer")
    info("I want to be able to get a Token from a refresh token")

    Scenario("Find a Token from a valid refresh token") {
      Given("a valid User")
      And("a persisted token")
      val accessToken = exampleToken.copy(accessToken = "VALID2_TOKEN", refreshToken = Some("VALID_REFRESH_TOKEN"))

      When("I get a Token from access token")
      val futureFoundToken: Future[Option[Token]] = for {
        _     <- persistentTokenService.persist(accessToken)
        token <- persistentTokenService.findByRefreshToken("VALID_REFRESH_TOKEN")
      } yield token

      whenReady(futureFoundToken, Timeout(3.seconds)) { result =>
        Then("an Option of Token is returned")
        val userToken = result.get

        And("""the access token should be "VALID2_TOKEN"""")
        userToken.accessToken shouldBe accessToken.accessToken
        And("""the refresh token should be "VALID_REFRESH_TOKEN"""")
        userToken.refreshToken shouldBe accessToken.refreshToken
      }
    }

    Scenario("Find a Token from an nonexistent refresh token") {
      Given("an nonexistent refresh token")
      When("I get a Token from access token")
      val futureNotFoundToken = persistentTokenService.findByRefreshToken("NON_TOKEN")

      whenReady(futureNotFoundToken, Timeout(3.seconds)) { token =>
        Then("an empty result is returned")
        token shouldBe empty
      }
    }
  }

}
