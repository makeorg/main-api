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
import java.util.Date

import org.make.api.MakeUnitTest
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent, ShortenedNames}
import org.make.api.user.{PersistentUserService, PersistentUserServiceComponent}
import org.make.core.auth.{Client, ClientId, Token, UserRights}
import org.make.core.session.VisitorId
import org.make.core.user.{Role, User, UserId}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{doReturn, spy, verify, when}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import scalaoauth2.provider.{AccessToken, AuthInfo, AuthorizationRequest, ClientCredential}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}

class MakeDataHandlerComponentTest
    extends MakeUnitTest
    with DefaultMakeDataHandlerComponent
    with MakeSettingsComponent
    with PersistentTokenServiceComponent
    with PersistentUserServiceComponent
    with PersistentClientServiceComponent
    with IdGeneratorComponent
    with OauthTokenGeneratorComponent
    with PersistentAuthCodeServiceComponent
    with ShortenedNames {

  implicit val someExecutionContext: EC = ECGlobal
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauthTokenGenerator: OauthTokenGenerator = mock[OauthTokenGenerator]
  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val persistentAuthCodeService: PersistentAuthCodeService = mock[PersistentAuthCodeService]

  val clientId = "0cdd82cb-5cc0-4875-bb54-5c3709449429"
  val secret = Some("secret")

  private val authenticationConfiguration = mock[makeSettings.Authentication.type]
  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  private val visitorCookieConfiguration = mock[makeSettings.VisitorCookie.type]
  when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  when(sessionCookieConfiguration.isSecure).thenReturn(false)
  when(sessionCookieConfiguration.domain).thenReturn(".foo.com")
  when(sessionCookieConfiguration.lifetime).thenReturn(Duration("20 minutes"))
  private val oauthConfiguration = mock[makeSettings.Oauth.type]
  private val tokenLifeTime = 1800
  when(oauthConfiguration.refreshTokenLifetime).thenReturn(tokenLifeTime)
  when(oauthConfiguration.accessTokenLifetime).thenReturn(tokenLifeTime)
  when(makeSettings.Authentication).thenReturn(authenticationConfiguration)
  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(makeSettings.Oauth).thenReturn(oauthConfiguration)
  when(authenticationConfiguration.defaultClientId).thenReturn(clientId)
  when(visitorCookieConfiguration.name).thenReturn("cookie-visitor")
  when(visitorCookieConfiguration.isSecure).thenReturn(false)
  when(visitorCookieConfiguration.domain).thenReturn(".foo.com")
  when(makeSettings.VisitorCookie).thenReturn(visitorCookieConfiguration)
  when(idGenerator.nextId()).thenReturn("some-id")
  when(idGenerator.nextVisitorId()).thenReturn(VisitorId("some-id"))

  val invalidClientId = "invalidClientId"

  val exampleClient = Client(
    clientId = ClientId(clientId),
    allowedGrantTypes = Seq("grant_type", "other_grant_type"),
    secret = secret,
    scope = None,
    redirectUri = None,
    defaultUserId = None
  )

  val validUsername = "john.doe@example.com"
  val validHashedPassword = "hash:abcde"

  val persistentTokenService: PersistentTokenService = mock[PersistentTokenService]
  val persistentUserService: PersistentUserService = mock[PersistentUserService]
  val persistentClientService: PersistentClientService = mock[PersistentClientService]
  val request: AuthorizationRequest = mock[AuthorizationRequest]
  val exampleUser: User = mock[User]
  val mockMap: Map[String, Seq[String]] = mock[Map[String, Seq[String]]]
  val exampleToken = Token(
    accessToken = "access_token",
    refreshToken = Some("refresh_token"),
    scope = None,
    expiresIn = tokenLifeTime,
    user = UserRights(UserId("user-id"), Seq(Role.RoleCitizen), Seq.empty),
    client = exampleClient
  )

  private val lifeTimeBig = 213123L
  val accessTokenExample = AccessToken(
    token = "access_token",
    refreshToken = Some("refresh_token"),
    scope = None,
    lifeSeconds = Some(lifeTimeBig),
    createdAt = new SimpleDateFormat("yyyy-MM-dd").parse("2017-01-01")
  )

  when(request.params).thenReturn(Map[String, Seq[String]]())
  when(request.requireParam(ArgumentMatchers.eq("username"))).thenReturn("john")
  when(request.requireParam(ArgumentMatchers.eq("password"))).thenReturn("passpass")

  //A valid client
  when(persistentClientService.findByClientIdAndSecret(ArgumentMatchers.eq(clientId), ArgumentMatchers.eq(secret)))
    .thenReturn(Future.successful(Some(exampleClient)))
  when(persistentClientService.get(ClientId(clientId))).thenReturn(Future(Some(exampleClient)))

  //A invalid client
  when(persistentClientService.findByClientIdAndSecret(ArgumentMatchers.eq(invalidClientId), ArgumentMatchers.eq(None)))
    .thenReturn(Future.successful(None))

  //A valid request
  when(mockMap.apply(ArgumentMatchers.eq("username"))).thenReturn(Seq(validUsername))
  when(mockMap.apply(ArgumentMatchers.eq("password"))).thenReturn(Seq(validHashedPassword))
  when(request.params).thenReturn(mockMap)

  //A valid user impl
  when(persistentUserService.persist(exampleUser))
    .thenReturn(Future.successful(exampleUser))
  when(persistentUserService.findByEmailAndPassword(ArgumentMatchers.any[String], ArgumentMatchers.any[String]))
    .thenReturn(Future.successful(Some(exampleUser)))

  when(persistentUserService.verificationTokenExists(ArgumentMatchers.any[String])).thenReturn(Future(false))

  feature("find User form client credentials and request") {
    scenario("best case") {
      Given("a valid client")
      val clientCredential = ClientCredential(clientId = clientId, clientSecret = secret)
      And("a valid user in a valid request")

      When("findUser is called")
      val futureMaybeUser: Future[Option[UserRights]] = oauth2DataHandler.findUser(Some(clientCredential), request)

      Then("the User is returned")
      whenReady(futureMaybeUser, Timeout(3.seconds)) { maybeUser =>
        maybeUser.isDefined shouldBe true
      }
    }

    scenario("nonexistent client") {
      Given("a invalid client")
      val clientCredential = ClientCredential(clientId = invalidClientId, clientSecret = None)
      And("a valid user in a valid request")

      When("findUser is called")
      val futureMaybeUser: Future[Option[UserRights]] = oauth2DataHandler.findUser(Some(clientCredential), request)

      Then("the User cannot be found")
      whenReady(futureMaybeUser, Timeout(3.seconds)) { maybeUser =>
        maybeUser shouldBe None
      }
    }

    scenario("nonexistent user") {
      Given("a valid client")
      val clientCredential = ClientCredential(clientId = clientId, clientSecret = secret)
      And("a nonexistent user in a valid request")
      when(persistentUserService.findByEmailAndPassword(ArgumentMatchers.any[String], ArgumentMatchers.any[String]))
        .thenReturn(Future.successful(None))

      When("findUser is called")
      val futureMaybeUser: Future[Option[UserRights]] = oauth2DataHandler.findUser(Some(clientCredential), request)

      Then("the User cannot be found")
      whenReady(futureMaybeUser, Timeout(3.seconds)) { maybeUser =>
        maybeUser shouldBe None
      }
    }
  }

  feature("Create a new AccessToken") {
    info("In order to authenticate a user")
    info("As a developer")
    info("I want to create and persist a new AccessToken from AuthInfo")

    scenario("Create a new AccessToken from valid AuthInfo") {
      Given("a valid AuthInfo")
      val authInfo = AuthInfo(UserRights(UserId("user-id"), Seq(Role.RoleCitizen), Seq.empty), None, None, None)

      And("a generated access token 'access_token' with a hashed value 'access_token_hashed'")
      when(oauthTokenGenerator.generateAccessToken())
        .thenReturn(Future.successful(("access_token", "access_token_hashed")))

      And("a generated refresh token 'refresh_token' with a hashed value 'refresh_token_hashed")
      when(oauthTokenGenerator.generateRefreshToken())
        .thenReturn(Future.successful(("refresh_token", "refresh_token_hashed")))

      When("I create a new AccessToken")
      when(persistentTokenService.persist(ArgumentMatchers.eq(exampleToken)))
        .thenReturn(Future.successful(exampleToken))
      val futureAccessToken: Future[AccessToken] = oauth2DataHandler.createAccessToken(authInfo)

      Then("persistentClientService must be called with a '0cdd82cb-5cc0-4875-bb54-5c3709449429' as ClientId")
      verify(persistentClientService).get(ClientId("0cdd82cb-5cc0-4875-bb54-5c3709449429"))

      whenReady(futureAccessToken, Timeout(3.seconds)) { maybeToken =>
        And("I should get an AccessToken")
        maybeToken shouldBe a[AccessToken]
        And("I should get an AccessToken with a token value equal to \"access_token\"")
        maybeToken.token shouldBe "access_token"
        And("I should get an AccessToken with a expiresIn value equal to 1800")
        maybeToken.lifeSeconds shouldBe Some(tokenLifeTime)
        And("I should get an AccessToken with a refresh token value equal to \"refresh_token\"")
        maybeToken.refreshToken shouldBe Some("refresh_token")
        And("I should get an AccessToken with an empty scope")
        maybeToken.scope shouldBe empty
      }
    }
  }

  feature("Get a stored AccessToken") {
    info("In order to authenticate a user")
    info("As a developer")
    info("I want to retrieve a stored access token")

    scenario("Create a new AccessToken from valid AuthInfo") {
      Given("a valid AuthInfo")
      val authInfo =
        AuthInfo(UserRights(UserId("user-id"), Seq(Role.RoleCitizen), Seq.empty), Some(clientId), None, None)
      And("""a stored AccessToken with values:
          | token: "AF8"
          | refreshToken: "KKJ"
          | scope: None
          | lifeSeconds: None
          | createdAt
        """.stripMargin)
      val exampleDate = ZonedDateTime.parse("2017-08-16T10:15:30+08:00", DateTimeFormatter.ISO_DATE_TIME)
      val expiresIn = 300
      val token = Token(
        accessToken = "AF8",
        refreshToken = Some("KKJ"),
        scope = None,
        expiresIn = expiresIn,
        user = authInfo.user,
        client = exampleClient,
        createdAt = Some(exampleDate),
        updatedAt = Some(exampleDate)
      )

      When("I get a persisted AccessToken")
      when(persistentTokenService.findByUserId(ArgumentMatchers.eq(authInfo.user.userId)))
        .thenReturn(Future.successful(Option(token)))
      val futureAccessToken = oauth2DataHandler.getStoredAccessToken(authInfo)

      whenReady(futureAccessToken, Timeout(3.seconds)) { maybeAccessToken =>
        Then("I should get an Option")
        maybeAccessToken shouldBe a[Option[_]]
        And("I should get an AccessToken with a token value equal to \"AF8\"")
        maybeAccessToken.get.token shouldBe "AF8"
        And("I should get an AccessToken with a refresh token value equal to \"KKJ\"")
        maybeAccessToken.get.refreshToken.get shouldBe "KKJ"
        And("I should get an AccessToken with a expiresIn value equal to 1800")
        maybeAccessToken.get.lifeSeconds shouldBe Some(expiresIn)
        And("I should get an AccessToken with an empty scope")
        maybeAccessToken.get.scope shouldBe empty
        And("I should get an AccessToken with an createdAt equal to 2017-08-16")
        maybeAccessToken.get.createdAt shouldBe Date.from(exampleDate.toInstant)
      }
    }
  }

  feature("Refresh an AccessToken") {
    info("In order to authenticate a user")
    info("As a developer")
    info("I want to refresh an access token")

    val authInfo = AuthInfo(UserRights(UserId("user-id"), Seq(Role.RoleCitizen), Seq.empty), Some(clientId), None, None)
    val refreshToken: String = "MYREFRESHTOKEN"
    val createdAt = new SimpleDateFormat("yyyy-MM-dd").parse("2017-01-01")
    val accessTokenExample = AccessToken(
      token = "DFG",
      refreshToken = Some("ERT"),
      scope = None,
      lifeSeconds = Some(lifeTimeBig),
      createdAt = createdAt
    )

    scenario("Refresh an AccessToken with success") {
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
      when(persistentTokenService.deleteByAccessToken(ArgumentMatchers.same(exampleToken.accessToken)))
        .thenReturn(Future.successful(1))

      when(persistentTokenService.findByRefreshToken(ArgumentMatchers.same(refreshToken)))
        .thenReturn(Future.successful(Some(exampleToken)))

      when(persistentTokenService.persist(ArgumentMatchers.any[Token]))
        .thenReturn(Future.successful(exampleToken))

      val oauth2DataHandlerWithMockedMethods = new DefaultMakeDataHandler
      val spyOndataHandler = spy(oauth2DataHandlerWithMockedMethods)
      doReturn(Future.successful(accessTokenExample), Future.successful(accessTokenExample))
        .when(spyOndataHandler)
        .createAccessToken(authInfo)

      val futureAccessToken = spyOndataHandler.refreshAccessToken(authInfo, refreshToken)
      Then("method deleteByRefreshToken should be called")
      And("method createAccessToken should be called")

      whenReady(futureAccessToken, Timeout(3.seconds)) { maybeAccessToken =>
        And("I should get a new AccessToken")
        maybeAccessToken shouldBe a[AccessToken]
        And("I should get an AccessToken with a token value equal to \"DFG\"")
        maybeAccessToken.token shouldBe "DFG"
        And("I should get an AccessToken with a refresh token value equal to \"ERT\"")
        maybeAccessToken.refreshToken.get shouldBe "ERT"
        And("I should get an AccessToken with a expiresIn value equal to 213123L")
        maybeAccessToken.lifeSeconds shouldBe Some(lifeTimeBig)
        And("I should get an AccessToken with an empty scope")
        maybeAccessToken.scope shouldBe empty
        And("I should get an AccessToken with a createdAt equal to 2017-01-01")
        maybeAccessToken.createdAt.toString shouldBe createdAt.toString
      }
    }

    scenario("Refresh an AccessToken corresponding to nothing") {
      Given("a valid AuthInfo")
      And("a refreshToken: \"MYREFRESHTOKEN\"")
      And("no token corresponds to the refresh token")

      when(persistentTokenService.findByRefreshToken(ArgumentMatchers.same(refreshToken)))
        .thenReturn(Future.successful(None))

      when(persistentTokenService.deleteByAccessToken(ArgumentMatchers.same(accessTokenExample.token)))
        .thenReturn(Future.successful(0))

      when(persistentTokenService.persist(ArgumentMatchers.any[Token]))
        .thenReturn(Future.successful(exampleToken))

      When("I call method refreshAccessToken")
      val oauth2DataHandlerWithMockedMethods = new DefaultMakeDataHandler
      val spyOndataHandler = spy(oauth2DataHandlerWithMockedMethods)
      doReturn(Future.successful(accessTokenExample), Future.successful(accessTokenExample))
        .when(spyOndataHandler)
        .createAccessToken(authInfo)

      val futureAccessToken = spyOndataHandler.refreshAccessToken(authInfo, refreshToken)
      Then("a NoSuchElementException should be thrown")
      intercept[NoSuchElementException] {
        Await.result(futureAccessToken, 5.seconds)
      }
    }
  }

  feature("Retrieve AuthInfo") {
    info("In order to authenticate a user")
    info("As a developer")
    info("I want to retrieve AuthInfo")

    val refreshToken: String = "HJBM"
    val accessToken: String = "AACCTT"
    val accessTokenObj = accessTokenExample.copy(token = accessToken, refreshToken = Some(refreshToken))

    info("In order to authenticate a user")
    info("As a developer")
    info("I want to retrieve AuthInfo")

    scenario("Get AuthInfo by refreshToken") {
      Given("a refreshToken: \"HJBM\"")
      When("I call method findAuthInfoByRefreshToken")
      when(persistentTokenService.findByRefreshToken(ArgumentMatchers.eq(refreshToken)))
        .thenReturn(Future.successful(Some(exampleToken)))
      Then("I get an AuthInfo Option")
      val futureAuthInfo = oauth2DataHandler.findAuthInfoByRefreshToken(refreshToken)
      whenReady(futureAuthInfo, Timeout(3.seconds)) { maybeAuthInfo =>
        maybeAuthInfo.get shouldBe a[AuthInfo[_]]
      }
    }

    scenario("Get AuthInfo with a nonexistent refreshToken") {
      Given("a refreshToken: \"OOOO\"")
      When("I call method findAuthInfoByRefreshToken")
      when(persistentTokenService.findByRefreshToken(ArgumentMatchers.eq(refreshToken)))
        .thenReturn(Future.successful(None))
      Then("I get an empty result")
      val futureAuthInfo = oauth2DataHandler.findAuthInfoByRefreshToken(refreshToken)
      whenReady(futureAuthInfo, Timeout(3.seconds)) { maybeAuthInfo =>
        maybeAuthInfo shouldBe empty
      }
    }

    scenario("Get AuthInfo by accessToken") {
      Given("an AccessToken")

      When("I call method findAuthInfoByAccessToken")
      when(persistentTokenService.findByAccessToken(ArgumentMatchers.eq(accessTokenObj.token)))
        .thenReturn(Future.successful(Some(exampleToken)))
      val futureAuthInfo = oauth2DataHandler.findAuthInfoByAccessToken(accessTokenObj)

      Then("I get an AuthInfo Option")
      whenReady(futureAuthInfo, Timeout(3.seconds)) { maybeAuthInfo =>
        maybeAuthInfo.get shouldBe a[AuthInfo[_]]
      }
    }

    scenario("Get AuthInfo with a nonexistent accessToken") {
      Given("an nonexistent AccessToken")
      val unexisting = accessTokenExample.copy(token = "some-inexisting-token")

      When("I call method findAuthInfoByAccessToken")
      when(persistentTokenService.findByAccessToken(ArgumentMatchers.eq(unexisting.token)))
        .thenReturn(Future.successful(None))
      val futureAuthInfo = oauth2DataHandler.findAuthInfoByAccessToken(unexisting)

      Then("I get an empty result")
      whenReady(futureAuthInfo, Timeout(3.seconds)) { maybeAuthInfo =>
        maybeAuthInfo shouldBe empty
      }
    }
  }

  feature("Retrieve an AccessToken") {
    info("In order to authenticate a user")
    info("As a developer")
    info("I want to retrieve an AccessToken")

    scenario("Get an AccessToken from a token") {
      Given("an access token: \"TOKENTOKEN\"")
      val accessToken = "TOKENTOKEN"

      When("I call method findAccessToken")
      when(persistentTokenService.findByAccessToken(ArgumentMatchers.eq(accessToken)))
        .thenReturn(Future.successful(Some(exampleToken)))
      val futureAccessToken = oauth2DataHandler.findAccessToken(accessToken)

      Then("I get an Option of AccessToken")
      whenReady(futureAccessToken, Timeout(3.seconds)) { maybeAccessToken =>
        maybeAccessToken.get shouldBe a[AccessToken]
      }
    }

    scenario("Get an AccessToken from a nonexistent token") {
      Given("an nonexistent AccessToken")
      val accessToken = "NONEXISTENT"

      When("I call method findAccessToken")
      when(persistentTokenService.findByAccessToken(ArgumentMatchers.eq(accessToken)))
        .thenReturn(Future.successful(None))
      val futureAccessToken = oauth2DataHandler.findAccessToken(accessToken)

      Then("I get an empty result")
      whenReady(futureAccessToken, Timeout(3.seconds)) { maybeAccessToken =>
        maybeAccessToken shouldBe empty
      }
    }

  }
}
