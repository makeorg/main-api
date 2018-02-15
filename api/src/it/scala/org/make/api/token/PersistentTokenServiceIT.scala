package org.make.api.token

import java.sql.SQLException
import java.time.ZonedDateTime

import org.make.api.DatabaseTest
import org.make.api.technical.auth.{DefaultPersistentClientServiceComponent, DefaultPersistentTokenServiceComponent}
import org.make.api.user.DefaultPersistentUserServiceComponent
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

  val before: ZonedDateTime = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")
  val now: ZonedDateTime = DateHelper.now()

  val exampleUser = User(
    userId = UserId("1"),
    email = "doe@example.com",
    firstName = Some("John"),
    lastName = Some("Doe"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    enabled = true,
    verified = true,
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleAdmin, Role.RoleCitizen),
    country = "FR",
    language = "fr",
    profile = None
  )
  val exampleClient = Client(
    clientId = ClientId("apiclient"),
    allowedGrantTypes = Seq("grant_type", "other_grant_type"),
    secret = Some("secret"),
    scope = None,
    redirectUri = None
  )
  val exampleToken = Token(
    accessToken = "ACCESS_TOKEN",
    refreshToken = Some("REFRESH_TOKEN"),
    scope = Some("scope"),
    expiresIn = 42,
    user = UserRights(exampleUser.userId, exampleUser.roles),
    client = exampleClient
  )

  feature("The app can persist a token") {
    info("As a programmer")
    info("I want to be able to persist a Token")

    scenario("Persist a Token and get the persisted Token") {
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
      val futureClient: Future[Client] = persistentClientService.persist(exampleClient)
      val futureUser: Future[User] = persistentUserService.persist(exampleUser)
      def futureTokenPersister: Future[Token] = persistentTokenService.persist(exampleToken)

      When("""I persist a Token with the user "John Doe" and the client "apiclient"""")
      And("I get the persisted Token")
      val futureFoundToken: Future[Option[Token]] = for {
        _     <- futureUser
        _     <- futureClient
        _     <- futureTokenPersister
        token <- persistentTokenService.get(exampleToken)
      } yield token

      whenReady(futureFoundToken, Timeout(3.seconds)) { result =>
        Then("result must an instance of Token")
        val token = result.get
        token shouldBe a[Token]

        And("""the access token must be "ACCESS_TOKEN"""")
        token.accessToken shouldBe exampleToken.accessToken

        And("""the refresh token must be "REFRESH_TOKEN"""")
        token.accessToken shouldBe exampleToken.accessToken

        And("the expires in must be 42")
        token.accessToken shouldBe exampleToken.accessToken

        And("""the user must be "John Doe"""")
        token.user.userId.value shouldBe exampleToken.user.userId.value

        And("""the client must be "apiclient"""")
        token.client.clientId.value shouldBe exampleToken.client.clientId.value

        And("the Token cannot be persisted if duplicate")
        intercept[SQLException] {
          logger.info("Expected exception: testing duplicate")
          Await.result(futureTokenPersister, 5.seconds)
        }
      }
    }
  }

  feature("Find a Token from a User") {
    scenario("Find a Token from a valid user") {
      Given("a valid User")
      When("a token is searched from this User")
      val futureFoundToken: Future[Option[Token]] = persistentTokenService.findByUserId(exampleUser.userId)

      whenReady(futureFoundToken, Timeout(3.seconds)) { result =>
        Then("the user's token is returned")
        val token = result.get
        token shouldBe a[Token]

        And("""the access token must be "ACCESS_TOKEN"""")
        token.accessToken shouldBe exampleToken.accessToken

        And("""the refresh token must be "REFRESH_TOKEN"""")
        token.accessToken shouldBe exampleToken.accessToken

        And("""the token user must be "John Doe"""")
        token.user.userId.value shouldBe exampleToken.user.userId.value
      }
    }
  }

  feature("Find a Token from an access token") {
    info("As a programmer")
    info("I want to be able to get a Token from an access token")

    scenario("Find a Token from a valid access token") {
      Given("a valid User")
      And("a persisted token")
      val accessToken = exampleToken.copy(accessToken = "VALID_TOKEN")

      When("I get a Token from access token")
      val futureFoundToken: Future[Option[Token]] = for {
        _     <- persistentTokenService.persist(accessToken)
        token <- persistentTokenService.findByAccessToken("VALID_TOKEN")
      } yield token

      whenReady(futureFoundToken, Timeout(3.seconds)) { result =>
        Then("an Option of Token is returned")
        val token = result.get
        token shouldBe a[Token]

        And("""the access token should be "VALID_TOKEN"""")
        token.accessToken shouldBe accessToken.accessToken
      }
    }

    scenario("Find a Token from an nonexistent access token") {
      Given("an nonexistent access token")
      When("I get a Token from access token")
      val futureNotFoundToken = persistentTokenService.findByAccessToken("NON_TOKEN")

      whenReady(futureNotFoundToken, Timeout(3.seconds)) { token =>
        Then("an empty result is returned")
        token shouldBe empty
      }
    }
  }

  feature("Find a Token from a refresh token") {
    info("As a programmer")
    info("I want to be able to get a Token from a refresh token")

    scenario("Find a Token from a valid refresh token") {
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
        val token = result.get
        token shouldBe a[Token]

        And("""the access token should be "VALID2_TOKEN"""")
        token.accessToken shouldBe accessToken.accessToken
        And("""the refresh token should be "VALID_REFRESH_TOKEN"""")
        token.refreshToken shouldBe accessToken.refreshToken
      }
    }

    scenario("Find a Token from an nonexistent refresh token") {
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
