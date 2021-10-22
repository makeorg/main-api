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

import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.make.api.MakeUnitTest
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical.{IdGeneratorComponent, ShortenedNames}
import org.make.api.user.{PersistentUserService, PersistentUserServiceComponent}
import org.make.core.DateHelper
import org.make.core.auth.{Client, ClientId, Token, UserRights}
import org.make.core.technical.IdGenerator
import org.make.core.user.{CustomRole, Role, User, UserId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import scalaoauth2.provider._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class MakeDataHandlerComponentTest
    extends MakeUnitTest
    with DefaultMakeDataHandlerComponent
    with MakeSettingsComponent
    with PersistentTokenServiceComponent
    with PersistentUserServiceComponent
    with ClientServiceComponent
    with IdGeneratorComponent
    with OauthTokenGeneratorComponent
    with PersistentAuthCodeServiceComponent
    with ShortenedNames {

  implicit val someExecutionContext: EC = ECGlobal
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauthTokenGenerator: OauthTokenGenerator = mock[OauthTokenGenerator]
  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val persistentAuthCodeService: PersistentAuthCodeService = mock[PersistentAuthCodeService]
  override val persistentTokenService: PersistentTokenService = mock[PersistentTokenService]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val clientService: ClientService = mock[ClientService]

  // Users and mocks
  val userWithoutRoles: User = user(id = UserId("userWithoutRoles"), roles = Nil, email = "john.doe@example.com")
  val userWithRoles: User =
    user(
      id = UserId("userWithRoles"),
      email = "admin@example.com",
      roles = Seq(CustomRole("role-client"), CustomRole("role-admin-client"))
    )

  val users: Seq[User] = Seq(userWithoutRoles, userWithRoles)

  when(persistentUserService.findByEmailAndPassword(any[String], any[String])).thenAnswer[String] { email =>
    Future.successful(users.find(_.email == email))
  }

  when(persistentUserService.get(any[UserId])).thenAnswer[UserId] { id =>
    Future.successful(users.find(_.userId == id))
  }

  when(persistentUserService.persist(any[User])).thenAnswer[User] { user =>
    Future.successful(user)
  }

  when(persistentUserService.verificationTokenExists(any[String])).thenReturn(Future(false))

  // Clients and mocks
  val secret: String = "secret"
  private val tokenLifeTime = 1800
  private val refreshLifeTime = 3600

  val defaultClient: Client = client(
    clientId = ClientId("0cdd82cb-5cc0-4875-bb54-5c3709449429"),
    name = "client",
    allowedGrantTypes = Seq(OAuthGrantType.PASSWORD, OAuthGrantType.REFRESH_TOKEN),
    secret = Some(secret),
    tokenExpirationSeconds = tokenLifeTime
  )

  val clientWithExpiration: Client = client(
    clientId = ClientId("client-with-expiration"),
    name = "client",
    allowedGrantTypes = Seq(OAuthGrantType.PASSWORD),
    secret = Some(secret),
    tokenExpirationSeconds = 42
  )

  val clientWithRoles: Client = client(
    clientId = ClientId("d45efaff-b8af-46ea-873b-d93a38f667b1"),
    name = "client-with-roles",
    allowedGrantTypes = Seq(OAuthGrantType.PASSWORD),
    secret = Some(secret),
    roles = Seq(CustomRole("role-client"), CustomRole("role-admin-client")),
    tokenExpirationSeconds = tokenLifeTime
  )

  val clientWithoutUserId: Client = client(
    clientId = ClientId("clientWithoutUserId"),
    name = "clientWithoutUserId",
    allowedGrantTypes = Seq(OAuthGrantType.CLIENT_CREDENTIALS),
    secret = Some(secret),
    tokenExpirationSeconds = tokenLifeTime
  )

  val clientForClientCredentials: Client = client(
    clientId = ClientId("clientForClientCredentials"),
    name = "clientForClientCredentials",
    allowedGrantTypes = Seq(OAuthGrantType.CLIENT_CREDENTIALS),
    secret = Some(secret),
    defaultUserId = Some(userWithRoles.userId),
    roles = Seq(CustomRole("role-client")),
    tokenExpirationSeconds = tokenLifeTime
  )

  val clients: Map[ClientId, Client] =
    Seq(defaultClient, clientWithRoles, clientWithExpiration, clientWithoutUserId, clientForClientCredentials)
      .map(client => client.clientId -> client)
      .toMap

  when(clientService.getClient(any[ClientId])).thenAnswer[ClientId] { id =>
    Future.successful(clients.get(id))
  }

  when(clientService.getClient(any[ClientId], any[Option[String]])).thenAnswer {
    (clientId: ClientId, password: Option[String]) =>
      clients.get(clientId) match {
        case None =>
          Future.successful(Left(new InvalidClient(s"No client for credentials ${clientId.value}:$secret")))
        case Some(client) if client.secret == password => Future.successful(Right(client))
        case _ =>
          Future.successful(Left(new InvalidClient(s"Secrets don't match for client ${clientId.value}")))
      }
  }

  private val authenticationConfiguration = mock[makeSettings.Authentication.type]
  private val secureCookieConfiguration = mock[makeSettings.SecureCookie.type]
  private val visitorCookieConfiguration = mock[makeSettings.VisitorCookie.type]
  when(secureCookieConfiguration.name).thenReturn("cookie-secure")
  when(secureCookieConfiguration.expirationName).thenReturn("cookie-secure-expiration")
  when(secureCookieConfiguration.isSecure).thenReturn(false)
  when(secureCookieConfiguration.domain).thenReturn(".foo.com")
  when(makeSettings.Authentication).thenReturn(authenticationConfiguration)
  when(makeSettings.SecureCookie).thenReturn(secureCookieConfiguration)
  when(authenticationConfiguration.defaultClientId).thenReturn(defaultClient.clientId.value)
  when(visitorCookieConfiguration.name).thenReturn("cookie-visitor")
  when(visitorCookieConfiguration.createdAtName).thenReturn("cookie-visitor-created-at")
  when(visitorCookieConfiguration.isSecure).thenReturn(false)
  when(visitorCookieConfiguration.domain).thenReturn(".foo.com")
  when(makeSettings.VisitorCookie).thenReturn(visitorCookieConfiguration)

  val exampleToken: Token = Token(
    accessToken = "access_token",
    refreshToken = Some("refresh_token"),
    scope = None,
    expiresIn = tokenLifeTime,
    refreshExpiresIn = refreshLifeTime,
    user = UserRights(
      userId = UserId("user-id"),
      roles = Seq(Role.RoleCitizen),
      availableQuestions = Seq.empty,
      emailVerified = true
    ),
    client = defaultClient,
    createdAt = Some(DateHelper.now())
  )

  val passwordRequest: PasswordRequest = {
    val request = mock[PasswordRequest]
    when(request.grantType).thenReturn(OAuthGrantType.PASSWORD)
    when(request.username).thenReturn(userWithoutRoles.email)
    when(request.password).thenReturn("passpass")
    request
  }

  val passwordWithRolesRequest: PasswordRequest = {
    val request = mock[PasswordRequest]
    when(request.grantType).thenReturn(OAuthGrantType.PASSWORD)
    when(request.username).thenReturn(userWithRoles.email)
    when(request.password).thenReturn("passpass")
    request
  }

  private val lifeTimeBig = 213123L
  val accessTokenExample: AccessToken = AccessToken(
    token = "access_token",
    refreshToken = Some("refresh_token"),
    scope = None,
    lifeSeconds = Some(lifeTimeBig),
    createdAt = new SimpleDateFormat("yyyy-MM-dd").parse("2017-01-01")
  )

  when(persistentTokenService.persist(any[Token])).thenAnswer { token: Token =>
    Future.successful(token)
  }

  Feature("find User form client credentials and request") {
    Scenario("best case") {
      Given("a valid client")
      val clientCredential = ClientCredential(clientId = defaultClient.clientId.value, clientSecret = Some(secret))
      And("a valid user in a valid request")

      When("findUser is called")
      val futureMaybeUser: Future[Option[UserRights]] =
        oauth2DataHandler.findUser(Some(clientCredential), passwordRequest)

      Then("the User is returned")
      whenReady(futureMaybeUser, Timeout(3.seconds)) { maybeUser =>
        maybeUser should be(defined)
      }
    }

    Scenario("nonexistent client") {
      Given("a invalid client")
      val clientCredential = ClientCredential(clientId = "Invalid Client Id", clientSecret = None)
      And("a valid user in a valid request")

      When("findUser is called")

      val futureMaybeUser: Future[Option[UserRights]] =
        oauth2DataHandler.findUser(Some(clientCredential), passwordRequest)

      Then("the User cannot be found")
      whenReady(futureMaybeUser.failed, Timeout(3.seconds)) {
        _ should be(a[InvalidClient])
      }
    }

    Scenario("flow not defined for client client") {
      Given("a invalid client")
      val clientCredential = ClientCredential(clientId = defaultClient.clientId.value, clientSecret = Some(secret))
      And("a valid user in a valid request")
      val refreshRequest = mock[RefreshTokenRequest]
      when(refreshRequest.grantType).thenReturn(OAuthGrantType.AUTHORIZATION_CODE)

      When("findUser is called")

      val futureMaybeUser: Future[Option[UserRights]] =
        oauth2DataHandler.findUser(Some(clientCredential), refreshRequest)

      Then("the User cannot be found")
      whenReady(futureMaybeUser.failed, Timeout(3.seconds)) {
        _ should be(a[UnauthorizedClient])
      }
    }

    Scenario("nonexistent user") {
      Given("a valid client")
      val clientCredential = ClientCredential(clientId = defaultClient.clientId.value, clientSecret = Some(secret))
      And("a nonexistent user in a valid request")
      val request = mock[PasswordRequest]
      when(request.grantType).thenReturn(OAuthGrantType.PASSWORD)
      when(request.username).thenReturn("unknown@example.com")
      when(request.password).thenReturn("some password")

      When("findUser is called")
      val futureMaybeUser: Future[Option[UserRights]] =
        oauth2DataHandler.findUser(Some(clientCredential), request)

      Then("the User cannot be found")
      whenReady(futureMaybeUser, Timeout(3.seconds)) { maybeUser =>
        maybeUser shouldBe None
      }
    }

    Scenario("user with insufficient roles") {
      Given("a valid client")
      val clientCredential = ClientCredential(clientId = clientWithRoles.clientId.value, clientSecret = Some(secret))
      And("a valid user in a valid request but with insufficient roles")

      When("findUser is called")
      val futureMaybeUser: Future[Option[UserRights]] =
        oauth2DataHandler.findUser(Some(clientCredential), passwordRequest)

      Then("An error is raised")
      whenReady(futureMaybeUser.failed, Timeout(3.seconds)) {
        _ should be(a[AccessDenied])
      }

    }

    Scenario("user with one of the client roles") {
      Given("a valid client")
      val clientCredential = ClientCredential(clientId = clientWithRoles.clientId.value, clientSecret = Some(secret))
      And("a valid user in a valid request")

      When("findUser is called")
      val futureMaybeUser: Future[Option[UserRights]] =
        oauth2DataHandler.findUser(Some(clientCredential), passwordWithRolesRequest)

      Then("the User is returned")
      whenReady(futureMaybeUser, Timeout(3.seconds)) { maybeUser =>
        maybeUser.isDefined shouldBe true
      }
    }

    Scenario("Using the client grant type with no client defined") {
      val request = mock[ClientCredentialsRequest]
      when(request.grantType).thenReturn(OAuthGrantType.CLIENT_CREDENTIALS)

      val clientCredential = ClientCredential(clientWithoutUserId.clientId.value, Some(secret))

      whenReady(oauth2DataHandler.findUser(Some(clientCredential), request), Timeout(3.seconds)) {
        _ should be(None)
      }
    }

    Scenario("Using the client grant type with a client without a user") {
      val request = mock[ClientCredentialsRequest]
      when(request.grantType).thenReturn(OAuthGrantType.CLIENT_CREDENTIALS)

      whenReady(oauth2DataHandler.findUser(None, request).failed, Timeout(3.seconds)) {
        _ should be(a[InvalidRequest])
      }
    }

    Scenario("Using the client grant type with a client with a user") {
      val request = mock[ClientCredentialsRequest]
      when(request.grantType).thenReturn(OAuthGrantType.CLIENT_CREDENTIALS)

      val eventualUser = oauth2DataHandler.findUser(
        Some(ClientCredential(clientForClientCredentials.clientId.value, clientForClientCredentials.secret)),
        request
      )
      whenReady(eventualUser, Timeout(3.seconds))(_.map(_.userId) should contain(userWithRoles.userId))
    }

    Scenario("Using the client grant type with a client with a bad secret key") {
      val request = mock[ClientCredentialsRequest]
      when(request.grantType).thenReturn(OAuthGrantType.CLIENT_CREDENTIALS)

      val eventualUser = oauth2DataHandler.findUser(
        Some(ClientCredential(clientForClientCredentials.clientId.value, Some("bad-client"))),
        request
      )
      whenReady(eventualUser.failed, Timeout(3.seconds)) {
        _ should be(a[InvalidClient])
      }
    }

    Scenario("Using the client grant type with a bad client id") {
      val request = mock[ClientCredentialsRequest]
      when(request.grantType).thenReturn(OAuthGrantType.CLIENT_CREDENTIALS)

      val eventualUser = oauth2DataHandler.findUser(Some(ClientCredential("unknown", Some(secret))), request)
      whenReady(eventualUser.failed, Timeout(3.seconds)) {
        _ should be(a[InvalidClient])
      }
    }
  }

  Feature("Create a new AccessToken") {
    info("In order to authenticate a user")
    info("As a developer")
    info("I want to create and persist a new AccessToken from AuthInfo")

    Scenario("Create a new AccessToken from valid AuthInfo") {
      Given("a valid AuthInfo")
      val authInfo = AuthInfo(
        UserRights(
          userId = UserId("user-id"),
          roles = Seq(Role.RoleCitizen),
          availableQuestions = Seq.empty,
          emailVerified = true
        ),
        None,
        None,
        None
      )

      And("a generated access token 'access_token' with a hashed value 'access_token_hashed'")
      when(oauthTokenGenerator.generateAccessToken())
        .thenReturn(Future.successful(("access_token", "access_token_hashed")))

      And("a generated refresh token 'refresh_token' with a hashed value 'refresh_token_hashed")
      when(oauthTokenGenerator.generateRefreshToken())
        .thenReturn(Future.successful(("refresh_token", "refresh_token_hashed")))

      When("I create a new AccessToken")
      val futureAccessToken: Future[AccessToken] = oauth2DataHandler.createAccessToken(authInfo)

      Then("clientService must be called for the default client")
      verify(clientService).getClient(defaultClient.clientId)

      whenReady(futureAccessToken, Timeout(3.seconds)) { maybeToken =>
        And("I should get an AccessToken with a token value equal to \"access_token\"")
        maybeToken.token shouldBe "access_token"
        And("I should get an AccessToken with a expiresIn value equal to 1800")
        maybeToken.lifeSeconds shouldBe Some(tokenLifeTime)
        And("I should get an AccessToken with a refresh token value equal to \"refresh_token\"")
        maybeToken.refreshToken shouldBe Some("refresh_token")
        And("I should get an AccessToken with an empty scope")
        maybeToken.scope should be(None)
      }
    }

    Scenario("Create a new AccessToken from valid AuthInfo with short validity") {
      Given("a valid AuthInfo")
      val authInfo = AuthInfo(
        UserRights(
          userId = UserId("user-id"),
          roles = Seq(Role.RoleCitizen),
          availableQuestions = Seq.empty,
          emailVerified = true
        ),
        Some("client-with-expiration"),
        None,
        None
      )

      And("a generated access token 'access_token' with a hashed value 'access_token_hashed'")
      when(oauthTokenGenerator.generateAccessToken())
        .thenReturn(Future.successful(("access_token", "access_token_hashed")))

      And("a generated refresh token 'refresh_token' with a hashed value 'refresh_token_hashed")
      when(oauthTokenGenerator.generateRefreshToken())
        .thenReturn(Future.successful(("refresh_token", "refresh_token_hashed")))

      When("I create a new AccessToken")
      val futureAccessToken: Future[AccessToken] = oauth2DataHandler.createAccessToken(authInfo)

      whenReady(futureAccessToken, Timeout(3.seconds)) { maybeToken =>
        Then("clientService must be called for clientWithExpiration")
        verify(clientService).getClient(clientWithExpiration.clientId)
        And("I should get an AccessToken with a token value equal to \"access_token\"")
        maybeToken.token shouldBe "access_token"
        And("I should get an AccessToken with a expiresIn value equal to 42")
        maybeToken.lifeSeconds shouldBe Some(42)
        And("I should get an AccessToken with no refresh token, since the refresh flow is not defined for client")
        maybeToken.refreshToken shouldBe None
        And("I should get an AccessToken with an empty scope")
        maybeToken.scope should be(None)
      }
    }
  }

  Feature("Get a stored AccessToken") {
    info("In order to authenticate a user")
    info("As a developer")
    info("I want to retrieve a stored access token")

    Scenario("Create a new AccessToken from valid AuthInfo") {
      Given("a valid AuthInfo")
      val authInfo =
        AuthInfo(
          UserRights(
            userId = UserId("user-id"),
            roles = Seq(Role.RoleCitizen),
            availableQuestions = Seq.empty,
            emailVerified = true
          ),
          Some(defaultClient.clientId.value),
          None,
          None
        )
      And("""a stored AccessToken with values:
          | token: "AF8"
          | refreshToken: "KKJ"
          | scope: None
          | lifeSeconds: None
          | createdAt
        """.stripMargin)
      val exampleDate = ZonedDateTime.parse("2017-08-16T10:15:30+08:00", DateTimeFormatter.ISO_DATE_TIME)
      val expiresIn = 300
      val refreshExpiresIn = 500
      val token = Token(
        accessToken = "AF8",
        refreshToken = Some("KKJ"),
        scope = None,
        expiresIn = expiresIn,
        refreshExpiresIn = refreshExpiresIn,
        user = authInfo.user,
        client = defaultClient,
        createdAt = Some(exampleDate),
        updatedAt = Some(exampleDate)
      )

      When("I get a persisted AccessToken")
      when(persistentTokenService.findByUserId(eqTo(authInfo.user.userId)))
        .thenReturn(Future.successful(Option(token)))
      val futureAccessToken = oauth2DataHandler.getStoredAccessToken(authInfo)

      whenReady(futureAccessToken, Timeout(3.seconds)) {
        _ should be(None)
      }
    }
  }

  Feature("Refresh an AccessToken") {
    info("In order to authenticate a user")
    info("As a developer")
    info("I want to refresh an access token")

    val authInfo = AuthInfo(
      UserRights(
        userId = UserId("user-id"),
        roles = Seq(Role.RoleCitizen),
        availableQuestions = Seq.empty,
        emailVerified = true
      ),
      Some(defaultClient.clientId.value),
      None,
      None
    )
    val refreshToken: String = "MYREFRESHTOKEN"
    val createdAt = new SimpleDateFormat("yyyy-MM-dd").parse("2017-01-01")
    val accessTokenExample = AccessToken(
      token = "DFG",
      refreshToken = Some("ERT"),
      scope = None,
      lifeSeconds = Some(lifeTimeBig),
      createdAt = createdAt
    )

    Scenario("Refresh an AccessToken with success") {
      Given("a valid AuthInfo")
      And("a refreshToken: \"MYREFRESHTOKEN\"")
      And("""a generated AccessToken with values:
          | - token: "DFG",
          | - refreshToken: Some("ERT"),
          | - scope: None,
          | - lifeSeconds: Some(213123L),
          | - createdAt: 2017-01-01
        """.stripMargin)

      When("I call method refreshAccessToken")
      when(persistentTokenService.deleteByAccessToken(same(exampleToken.accessToken)))
        .thenReturn(Future.successful(1))

      when(persistentTokenService.findByRefreshToken(same(refreshToken)))
        .thenReturn(Future.successful(Some(exampleToken)))

      val oauth2DataHandlerWithMockedMethods = new DefaultMakeDataHandler
      val spyOndataHandler = spy(oauth2DataHandlerWithMockedMethods, lenient = true)
      doReturn(Future.successful(accessTokenExample), Future.successful(accessTokenExample))
        .when(spyOndataHandler)
        .createAccessToken(authInfo)

      val futureAccessToken = spyOndataHandler.refreshAccessToken(authInfo, refreshToken)
      Then("method deleteByRefreshToken should be called")
      And("method createAccessToken should be called")

      whenReady(futureAccessToken, Timeout(3.seconds)) { maybeAccessToken =>
        And("I should get an AccessToken with a token value equal to \"DFG\"")
        maybeAccessToken.token shouldBe "DFG"
        And("I should get an AccessToken with a refresh token value equal to \"ERT\"")
        maybeAccessToken.refreshToken.get shouldBe "ERT"
        And("I should get an AccessToken with a expiresIn value equal to 213123L")
        maybeAccessToken.lifeSeconds shouldBe Some(lifeTimeBig)
        And("I should get an AccessToken with an empty scope")
        maybeAccessToken.scope should be(None)
        And("I should get an AccessToken with a createdAt equal to 2017-01-01")
        maybeAccessToken.createdAt.toString shouldBe createdAt.toString
      }
    }

    Scenario("Refresh an AccessToken corresponding to nothing") {
      Given("a valid AuthInfo")
      And("a refreshToken: \"MYREFRESHTOKEN\"")
      And("no token corresponds to the refresh token")

      when(persistentTokenService.findByRefreshToken(same(refreshToken)))
        .thenReturn(Future.successful(None))

      when(persistentTokenService.deleteByAccessToken(same(accessTokenExample.token)))
        .thenReturn(Future.successful(0))

      When("I call method refreshAccessToken")
      val oauth2DataHandlerWithMockedMethods = new DefaultMakeDataHandler
      val spyOndataHandler = spy(oauth2DataHandlerWithMockedMethods, lenient = true)

      val futureAccessToken = spyOndataHandler.refreshAccessToken(authInfo, refreshToken)
      Then("a NoSuchElementException should be thrown")
      intercept[TokenAlreadyRefreshed] {
        Await.result(futureAccessToken, 5.seconds)
      }
    }
  }

  Feature("Retrieve AuthInfo") {
    info("In order to authenticate a user")
    info("As a developer")
    info("I want to retrieve AuthInfo")

    info("In order to authenticate a user")
    info("As a developer")
    info("I want to retrieve AuthInfo")

    Scenario("Get AuthInfo by refreshToken") {
      Given("a refreshToken: \"HJBM\"")
      val refreshToken: String = "HJBM"

      When("I call method findAuthInfoByRefreshToken")
      when(persistentTokenService.findByRefreshToken(eqTo(refreshToken)))
        .thenReturn(Future.successful(Some(exampleToken)))
      Then("I get an AuthInfo Option")
      val futureAuthInfo = oauth2DataHandler.findAuthInfoByRefreshToken(refreshToken)
      whenReady(futureAuthInfo, Timeout(3.seconds)) { maybeAuthInfo =>
        maybeAuthInfo should be(defined)
      }
    }

    Scenario("Get AuthInfo with a nonexistent refreshToken") {
      Given("a refreshToken: \"OOOO\"")
      val refreshToken: String = "0000"

      When("I call method findAuthInfoByRefreshToken")
      when(persistentTokenService.findByRefreshToken(eqTo(refreshToken)))
        .thenReturn(Future.successful(None))
      Then("I get an empty result")
      val futureAuthInfo = oauth2DataHandler.findAuthInfoByRefreshToken(refreshToken)
      whenReady(futureAuthInfo, Timeout(3.seconds)) { maybeAuthInfo =>
        maybeAuthInfo should be(None)
      }
    }

    Scenario("Get AuthInfo with an expired refreshToken") {
      Given("a refreshToken: \"EXPIRED\"")
      val refreshToken: String = "EXPIRED"

      When("I call method findAuthInfoByRefreshToken")
      when(persistentTokenService.findByRefreshToken(eqTo(refreshToken)))
        .thenReturn(Future.successful(Some(exampleToken.copy(createdAt = Some(DateHelper.now().minusDays(1))))))

      Then("I get an empty result")
      val futureAuthInfo = oauth2DataHandler.findAuthInfoByRefreshToken(refreshToken)
      whenReady(futureAuthInfo, Timeout(3.seconds)) { maybeAuthInfo =>
        maybeAuthInfo should be(None)
      }
    }

    Scenario("Get AuthInfo by accessToken") {
      Given("an AccessToken")
      val refreshToken: String = "TTGGAA"
      val accessToken: String = "AACCTT"
      val accessTokenObj = accessTokenExample.copy(token = accessToken, refreshToken = Some(refreshToken))

      When("I call method findAuthInfoByAccessToken")
      when(persistentTokenService.get(eqTo(accessTokenObj.token)))
        .thenReturn(Future.successful(Some(exampleToken)))
      val futureAuthInfo = oauth2DataHandler.findAuthInfoByAccessToken(accessTokenObj)

      Then("I get an AuthInfo Option")
      whenReady(futureAuthInfo, Timeout(3.seconds)) { maybeAuthInfo =>
        maybeAuthInfo should be(defined)
      }
    }

    Scenario("Get AuthInfo with a nonexistent accessToken") {
      Given("an nonexistent AccessToken")
      val unexisting = accessTokenExample.copy(token = "some-inexisting-token")

      When("I call method findAuthInfoByAccessToken")
      when(persistentTokenService.get(eqTo(unexisting.token)))
        .thenReturn(Future.successful(None))
      val futureAuthInfo = oauth2DataHandler.findAuthInfoByAccessToken(unexisting)

      Then("I get an empty result")
      whenReady(futureAuthInfo, Timeout(3.seconds)) { maybeAuthInfo =>
        maybeAuthInfo should be(None)
      }
    }
  }

  Feature("Retrieve an AccessToken") {
    info("In order to authenticate a user")
    info("As a developer")
    info("I want to retrieve an AccessToken")

    Scenario("Get an AccessToken from a token") {
      Given("an access token: \"TOKENTOKEN\"")
      val accessToken = "TOKENTOKEN"

      When("I call method findAccessToken")
      when(persistentTokenService.get(eqTo(accessToken)))
        .thenReturn(Future.successful(Some(exampleToken)))
      val futureAccessToken = oauth2DataHandler.findAccessToken(accessToken)

      Then("I get an Option of AccessToken")
      whenReady(futureAccessToken, Timeout(3.seconds)) { maybeAccessToken =>
        maybeAccessToken should be(defined)
      }
    }

    Scenario("Get an AccessToken from a nonexistent token") {
      Given("an nonexistent AccessToken")
      val accessToken = "NONEXISTENT"

      When("I call method findAccessToken")
      when(persistentTokenService.get(eqTo(accessToken)))
        .thenReturn(Future.successful(None))
      val futureAccessToken = oauth2DataHandler.findAccessToken(accessToken)

      Then("I get an empty result")
      whenReady(futureAccessToken, Timeout(3.seconds)) { maybeAccessToken =>
        maybeAccessToken should be(None)
      }
    }

    Scenario("Get an AccessToken from an expired token") {
      Given("an expired access token: \"TOKENEXPIRED\"")
      val accessToken = "TOKENEXPIRED"

      When("I call method findAccessToken")
      when(persistentTokenService.get(eqTo(accessToken)))
        .thenReturn(Future.successful(Some(exampleToken.copy(accessToken = accessToken, expiresIn = -1))))

      val futureAccessToken = oauth2DataHandler.findAccessToken(accessToken)

      Then("The result should be None")
      whenReady(futureAccessToken, Timeout(3.seconds)) { maybeAccessToken =>
        maybeAccessToken.isDefined shouldBe false
      }
    }
  }

  Feature("Refresh if token is expired") {
    info("In order to keep a user authenticated")
    info("As a developer")
    info("I want to refresh an access token if it's not expired and the refresh token is not expired")

    Scenario("access token not found") {
      when(persistentTokenService.get(eqTo("not-found")))
        .thenReturn(Future.successful(None))
      val futureRefreshedToken = oauth2DataHandler.refreshIfTokenIsExpired("not-found")
      whenReady(futureRefreshedToken, Timeout(3.seconds)) { maybeRefreshedToken =>
        maybeRefreshedToken.isDefined shouldBe false
      }
    }

    Scenario("access token not expired") {
      val accessToken = "TOKENNOTEXPIRED"
      when(persistentTokenService.get(eqTo(accessToken)))
        .thenReturn(Future.successful(Some(exampleToken.copy(accessToken = accessToken))))

      val futureRefreshedToken = oauth2DataHandler.refreshIfTokenIsExpired(accessToken)
      whenReady(futureRefreshedToken, Timeout(3.seconds)) { maybeRefreshedToken =>
        maybeRefreshedToken.isDefined shouldBe false
      }
    }

    Scenario("access token expired and refresh token expired") {
      val accessToken = "TOKENREFRESHNOTEXPIRED"
      when(persistentTokenService.get(eqTo(accessToken)))
        .thenReturn(
          Future.successful(
            Some(exampleToken.copy(accessToken = accessToken, createdAt = Some(DateHelper.now().minusDays(1L))))
          )
        )

      val futureRefreshedToken = oauth2DataHandler.refreshIfTokenIsExpired(accessToken)
      whenReady(futureRefreshedToken, Timeout(3.seconds)) { maybeRefreshedToken =>
        maybeRefreshedToken.isDefined shouldBe false
      }
    }

    Scenario("access token expired and valid refresh token") {
      val accessToken = "TOKENREFRESHNOTEXPIRED"
      val newAccessToken = "new-access-token"

      when(persistentTokenService.get(eqTo(accessToken)))
        .thenReturn(
          Future.successful(
            Some(
              exampleToken.copy(
                accessToken = accessToken,
                createdAt = Some(DateHelper.now().minusHours(1L)),
                expiresIn = 500,
                refreshExpiresIn = 5000
              )
            )
          )
        )
      when(persistentTokenService.get(eqTo(newAccessToken)))
        .thenReturn(
          Future.successful(Some(exampleToken.copy(accessToken = newAccessToken, createdAt = Some(DateHelper.now()))))
        )

      when(persistentTokenService.findByRefreshToken(eqTo(exampleToken.refreshToken.get)))
        .thenReturn(Future.successful(Some(exampleToken)))
      when(persistentTokenService.deleteByAccessToken(same(exampleToken.accessToken)))
        .thenReturn(Future.successful(1))

      when(oauthTokenGenerator.generateAccessToken())
        .thenReturn(Future.successful((newAccessToken, "new_access_token_hashed")))
      when(oauthTokenGenerator.generateRefreshToken())
        .thenReturn(Future.successful(("refresh_token", "refresh_token_hashed")))

      val futureRefreshedToken = oauth2DataHandler.refreshIfTokenIsExpired(accessToken)
      whenReady(futureRefreshedToken, Timeout(3.seconds)) { maybeRefreshedToken =>
        maybeRefreshedToken should be(defined)
        maybeRefreshedToken.map(_.accessToken) should contain(newAccessToken)
      }
    }

  }
}
