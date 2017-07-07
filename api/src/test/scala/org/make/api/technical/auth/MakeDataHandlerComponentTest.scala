package org.make.api.technical.auth

import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

import org.make.api.MakeUnitTest
import org.make.api.technical.{IdGeneratorComponent, ShortenedNames}
import org.make.api.user.PersistentUserServiceComponent
import org.make.core.auth.{Client, ClientId, Token}
import org.make.core.user.User
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{doReturn, spy, verify, when}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scalaoauth2.provider.{AccessToken, AuthInfo, AuthorizationRequest, ClientCredential}

class MakeDataHandlerComponentTest
    extends MakeUnitTest
    with MakeDataHandlerComponent
    with PersistentTokenServiceComponent
    with PersistentUserServiceComponent
    with PersistentClientServiceComponent
    with IdGeneratorComponent
    with TokenGeneratorComponent
    with ShortenedNames {

  override val readExecutionContext: EC = ECGlobal
  override val writeExecutionContext: EC = ECGlobal
  implicit val someExecutionContext: EC = readExecutionContext
  override val oauth2DataHandler: MakeDataHandler = new MakeDataHandler
  override val idGenerator: IdGenerator = new UUIDIdGenerator
  override val tokenGenerator: TokenGenerator = mock[TokenGenerator]

  val clientId = "apiclient"
  val secret = Some("secret")
  val invalidClientId = "invalidClientId"

  val exampleClient = Client(
    clientId = ClientId(clientId),
    allowedGrantTypes = Seq("grant_type", "other_grant_type"),
    secret = secret,
    scope = None,
    redirectUri = None
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
    accessToken = "access_token_hashed",
    refreshToken = Some("refresh_token_hashed"),
    scope = None,
    expiresIn = 1800,
    user = exampleUser,
    client = exampleClient
  )
  val accessTokenExample = AccessToken(
    token = "access_token",
    refreshToken = Some("refresh_token"),
    scope = None,
    lifeSeconds = Some(213123L),
    createdAt = new SimpleDateFormat("yyyy-MM-dd").parse("2017-01-01")
  )

  //A valid client
  when(persistentClientService.findByClientIdAndSecret(ArgumentMatchers.eq(clientId), ArgumentMatchers.eq(secret)))
    .thenReturn(Future.successful(Some(exampleClient)))
  when(persistentClientService.get(ClientId(clientId))).thenReturn(Future(Some(exampleClient)))

  //A invalid client
  when(
    persistentClientService.findByClientIdAndSecret(ArgumentMatchers.eq(invalidClientId), ArgumentMatchers.eq(None))
  ).thenReturn(Future.successful(None))

  //A valid request
  when(mockMap.apply(ArgumentMatchers.eq("username"))).thenReturn(Seq(validUsername))
  when(mockMap.apply(ArgumentMatchers.eq("password"))).thenReturn(Seq(validHashedPassword))
  when(request.params).thenReturn(mockMap)

  //A valid user impl
  when(persistentUserService.persist(exampleUser))
    .thenReturn(Future.successful(exampleUser))
  when(persistentUserService.findByEmailAndHashedPassword(ArgumentMatchers.any[String], ArgumentMatchers.any[String]))
    .thenReturn(Future.successful(Some(exampleUser)))

  feature("find User form client credentials and request") {
    scenario("best case") {
      Given("a valid client")
      val clientCredential = ClientCredential(clientId = clientId, clientSecret = secret)
      And("a valid user in a valid request")

      When("findUser is called")
      val futureMaybeUser: Future[Option[User]] = oauth2DataHandler.findUser(Some(clientCredential), request)

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
      val futureMaybeUser: Future[Option[User]] = oauth2DataHandler.findUser(Some(clientCredential), request)

      Then("the User cannot be found")
      whenReady(futureMaybeUser, Timeout(3.seconds)) { maybeUser =>
        maybeUser shouldBe None
      }
    }

    scenario("nonexistent user") {
      Given("a valid client")
      val clientCredential = ClientCredential(clientId = clientId, clientSecret = secret)
      And("a nonexistent user in a valid request")
      when(
        persistentUserService.findByEmailAndHashedPassword(ArgumentMatchers.any[String], ArgumentMatchers.any[String])
      ).thenReturn(Future.successful(None))

      When("findUser is called")
      val futureMaybeUser: Future[Option[User]] = oauth2DataHandler.findUser(Some(clientCredential), request)

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
      val authInfo = AuthInfo(exampleUser, Some(clientId), None, None)

      And("a generated access token 'access_token' with a hashed value 'access_token_hashed'")
      when(tokenGenerator.generateAccessToken()).thenReturn(Future.successful(("access_token", "access_token_hashed")))

      And("a generated refresh token 'refresh_token' with a hashed value 'refresh_token_hashed")
      when(tokenGenerator.generateRefreshToken())
        .thenReturn(Future.successful(("refresh_token", "refresh_token_hashed")))

      When("I create a new AccessToken")
      when(persistentTokenService.persist(ArgumentMatchers.eq(exampleToken)))
        .thenReturn(Future.successful(exampleToken))
      val futureAccessToken: Future[AccessToken] = oauth2DataHandler.createAccessToken(authInfo)

      Then("persistentClientService must be called with a 'apiclient' as ClientId")
      verify(persistentClientService).get(ClientId("apiclient"))

      whenReady(futureAccessToken, Timeout(3.seconds)) { maybeToken =>
        And("I should get an AccessToken")
        maybeToken shouldBe a[AccessToken]
        And("I should get an AccessToken with a token value equal to \"access_token\"")
        maybeToken.token shouldBe "access_token"
        And("I should get an AccessToken with a expiresIn value equal to 1800")
        maybeToken.lifeSeconds shouldBe Some(1800)
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
      val authInfo = AuthInfo(exampleUser, Some(clientId), None, None)
      And("""a stored AccessToken with values:
          | token: "AF8"
          | refreshToken: "KKJ"
          | scope: None
          | lifeSeconds: None
          | createdAt
        """.stripMargin)
      val exampleDate = ZonedDateTime.parse("2017-08-16T10:15:30+08:00", DateTimeFormatter.ISO_DATE_TIME)
      val token = Token(
        accessToken = "AF8",
        refreshToken = Some("KKJ"),
        scope = None,
        expiresIn = 300,
        user = authInfo.user,
        client = exampleClient,
        createdAt = Some(exampleDate),
        updatedAt = Some(exampleDate)
      )

      When("I get a persisted AccessToken")
      when(persistentTokenService.findByUser(ArgumentMatchers.eq(authInfo.user)))
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
        maybeAccessToken.get.lifeSeconds shouldBe Some(300)
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

    val authInfo = AuthInfo(exampleUser, Some(clientId), None, None)
    val refreshToken: String = "MYREFRESHTOKEN"
    val createdAt = new SimpleDateFormat("yyyy-MM-dd").parse("2017-01-01")
    val accessTokenExample = AccessToken(
      token = "DFG",
      refreshToken = Some("ERT"),
      scope = None,
      lifeSeconds = Some(213123L),
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
      when(persistentTokenService.deleteByRefreshToken(ArgumentMatchers.same(refreshToken)))
        .thenReturn(Future.successful(1))
      val oauth2DataHandlerWithMockedMethods = new MakeDataHandler
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
        maybeAccessToken.lifeSeconds shouldBe Some(213123L)
        And("I should get an AccessToken with an empty scope")
        maybeAccessToken.scope shouldBe empty
        And("I should get an AccessToken with a createdAt equal to 2017-01-01")
        maybeAccessToken.createdAt.toString shouldBe createdAt.toString
      }
    }

    scenario("Refresh an AccessToken when deletion failed") {
      Given("a valid AuthInfo")
      And("a refreshToken: \"MYREFRESHTOKEN\"")
      And("no rows affected by method deleteByRefreshToken")
      when(persistentTokenService.deleteByRefreshToken(ArgumentMatchers.same(refreshToken)))
        .thenReturn(Future.successful(0))

      When("I call method refreshAccessToken")
      val oauth2DataHandlerWithMockedMethods = new MakeDataHandler
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

      When("I call method findAuthInfoByAccessToken")
      when(persistentTokenService.findByAccessToken(ArgumentMatchers.eq(accessTokenObj.token)))
        .thenReturn(Future.successful(None))
      val futureAuthInfo = oauth2DataHandler.findAuthInfoByAccessToken(accessTokenObj)

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
