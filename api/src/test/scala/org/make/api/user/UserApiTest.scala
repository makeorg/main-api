package org.make.api.user

import java.net.InetAddress
import java.time.{Instant, LocalDate}
import java.util.Date

import akka.http.scaladsl.model.headers.{`Remote-Address`, Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RemoteAddress, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical._
import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.api.technical.auth._
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user.social._
import org.make.api.userhistory.UserEvent.ResetPasswordEvent
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent
import org.make.api.{ActorSystemComponent, MakeApi, MakeApiTestBase}
import org.make.core.auth.UserRights
import org.make.core.user.{Role, User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationError}
import org.mockito.ArgumentMatchers.{any, eq => matches}
import org.mockito.Mockito._
import org.mockito.{ArgumentMatchers, Mockito}
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class UserApiTest
    extends MakeApiTestBase
    with UserApi
    with UserServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SocialServiceComponent
    with SessionHistoryCoordinatorServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ReadJournalComponent
    with PersistentUserServiceComponent
    with EventBusServiceComponent
    with MakeSettingsComponent
    with ActorSystemComponent {

  override val userService: UserService = mock[UserService]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val socialService: SocialService = mock[SocialService]
  override val facebookApi: FacebookApi = mock[FacebookApi]
  override val googleApi: GoogleApi = mock[GoogleApi]

  val routes: Route = sealRoute(handleRejections(MakeApi.rejectionHandler) {
    userRoutes
  })

  val fakeUser = User(
    userId = UserId("ABCD"),
    email = "foo@bar.com",
    firstName = Some("olive"),
    lastName = Some("tom"),
    lastIp = Some("127.0.0.1"),
    hashedPassword = Some("passpass"),
    enabled = true,
    emailVerified = false,
    lastConnection = DateHelper.now(),
    verificationToken = Some("token"),
    verificationTokenExpiresAt = Some(DateHelper.now()),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq.empty,
    country = "FR",
    language = "fr",
    profile = None
  )

  val expiresInSecond = 1000

  feature("register user") {
    scenario("successful register user") {
      Mockito
        .when(
          userService
            .register(any[UserRegisterData], any[RequestContext])
        )
        .thenReturn(Future.successful(fakeUser))
      val request =
        """
          |{
          | "email": "foo@bar.com",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "1997-12-02",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")
      Post("/user", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr))) ~> routes ~> check {
        status should be(StatusCodes.Created)
        verify(userService).register(
          matches(
            UserRegisterData(
              email = "foo@bar.com",
              firstName = Some("olive"),
              lastName = Some("tom"),
              password = Some("mypassss"),
              lastIp = Some("192.0.0.1"),
              dateOfBirth = Some(LocalDate.parse("1997-12-02")),
              country = "FR",
              language = "fr"
            )
          ),
          any[RequestContext]
        )
      }
    }

    scenario("successful register user from proposal context") {
      Mockito
        .when(
          userService
            .register(any[UserRegisterData], any[RequestContext])
        )
        .thenReturn(Future.successful(fakeUser))
      val request =
        """
          |{
          | "email": "foo@bar.com",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "1997-12-02",
          | "postalCode": "75011",
          | "profession": "football player",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")
      Post("/user", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr))) ~> routes ~> check {
        status should be(StatusCodes.Created)
        verify(userService).register(
          matches(
            UserRegisterData(
              email = "foo@bar.com",
              firstName = Some("olive"),
              lastName = Some("tom"),
              password = Some("mypassss"),
              lastIp = Some("192.0.0.1"),
              dateOfBirth = Some(LocalDate.parse("1997-12-02")),
              postalCode = Some("75011"),
              profession = Some("football player"),
              country = "FR",
              language = "fr"
            )
          ),
          any[RequestContext]
        )
      }
    }

    scenario("validation failed for existing email") {
      Mockito
        .when(
          userService
            .register(any[UserRegisterData], any[RequestContext])
        )
        .thenReturn(Future.failed(EmailAlreadyRegisteredException("foo@bar.com")))

      val request =
        """
          |{
          | "email": "foo@bar.com",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "1997-12-02",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(Some(ValidationError("email", Some("Email foo@bar.com already exist"))))
      }
    }

    scenario("validation failed for missing country and/or language") {
      val request =
        """
          |{
          | "email": "foo@bar.com",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "1997-12-02"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val countryError = errors.find(_.field == "country")
        countryError should be(Some(ValidationError("country", Some("country is mandatory"))))
        val languageError = errors.find(_.field == "language")
        languageError should be(Some(ValidationError("language", Some("language is mandatory"))))
      }
    }

    scenario("validation failed for malformed email") {
      val request =
        """
          |{
          | "email": "foo",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "1997-12-02",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(Some(ValidationError("email", Some("email is not a valid email"))))
      }
    }

    scenario("validation failed for postal code too long") {
      val request =
        """
          |{
          | "email": "foo@bar.com",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "1997-12-02",
          | "postalCode": "123456789azertyuiop",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "postalCode")
        emailError should be(
          Some(ValidationError("postalCode", Some("postal code cannot be longer than 10 characters")))
        )
      }
    }

    scenario("validation failed for malformed date of birth") {
      val request =
        """
          |{
          | "email": "foo",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "foo-12-02",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val dateOfBirthError = errors.find(_.field == "dateOfBirth")
        dateOfBirthError should be(
          Some(ValidationError("dateOfBirth", Some("foo-12-02 is not a valid date, it should match yyyy-MM-dd")))
        )
      }
    }

    scenario("validation failed for required field") {
      // todo: add parser of error messages
      val request =
        """
          |{
          | "email": "foo@bar.com",
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  feature("login user from social") {
    scenario("successful login user") {
      Mockito
        .when(
          socialService
            .login(any[String], any[String], any[String], any[String], any[Option[String]], any[RequestContext])
        )
        .thenReturn(
          Future.successful(
            UserIdAndToken(
              UserId("12347"),
              TokenResponse(
                token_type = "Bearer",
                access_token = "access_token",
                expires_in = expiresInSecond,
                refresh_token = "refresh_token"
              )
            )
          )
        )
      val request =
        """
          |{
          | "provider": "google",
          | "token": "ABCDEFGHIJK",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")
      Post("/user/login/social", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr))) ~> routes ~> check {
        status should be(StatusCodes.Created)
        header("Set-Cookie").get.value should include("cookie-session")
        verify(socialService).login(
          matches("google"),
          matches("ABCDEFGHIJK"),
          matches("FR"),
          matches("fr"),
          matches(Some("192.0.0.1")),
          any[RequestContext]
        )
      }
    }

    scenario("bad request when login social user") {
      Mockito
        .when(
          socialService
            .login(any[String], any[String], any[String], any[String], any[Option[String]], any[RequestContext])
        )
        .thenReturn(
          Future.successful(
            UserIdAndToken(
              UserId("12347"),
              TokenResponse(
                token_type = "Bearer",
                access_token = "access_token",
                expires_in = expiresInSecond,
                refresh_token = "refresh_token"
              )
            )
          )
        )
      val request =
        """
          |{
          | "provider": "google"
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")
      Post("/user/login/social", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  feature("reset password") {
    info("In order to reset a password")
    info("As a user with an email")
    info("I want to use api to reset my password")

    val johnDoeUser = User(
      userId = UserId("JOHNDOE"),
      email = "john.doe@example.com",
      firstName = Some("John"),
      lastName = Some("Doe"),
      lastIp = Some("127.0.0.1"),
      hashedPassword = Some("passpass"),
      enabled = true,
      emailVerified = false,
      lastConnection = DateHelper.now(),
      verificationToken = Some("token"),
      verificationTokenExpiresAt = Some(DateHelper.now()),
      resetToken = None,
      resetTokenExpiresAt = None,
      roles = Seq.empty,
      country = "FR",
      language = "fr",
      profile = None
    )
    Mockito
      .when(persistentUserService.findByEmail("john.doe@example.com"))
      .thenReturn(Future.successful(Some(johnDoeUser)))
    Mockito
      .when(persistentUserService.findUserIdByEmail("invalidexample.com"))
      .thenAnswer(_ => throw new IllegalArgumentException("findUserIdByEmail should be called with valid email"))
    when(eventBusService.publish(ArgumentMatchers.any[ResetPasswordEvent]))
      .thenAnswer(
        invocation =>
          if (!invocation.getArgument[ResetPasswordEvent](0).userId.equals(johnDoeUser.userId)) {
            throw new IllegalArgumentException("UserId not match")
        }
      )
    Mockito
      .when(persistentUserService.findByEmail("fake@example.com"))
      .thenReturn(Future.successful(None))

    val fooBarUserId = UserId("foo-bar")
    val fooBarUser = User(
      userId = fooBarUserId,
      email = "foo@exemple.com",
      firstName = None,
      lastName = None,
      lastIp = None,
      hashedPassword = None,
      enabled = true,
      emailVerified = true,
      lastConnection = DateHelper.now(),
      verificationToken = None,
      verificationTokenExpiresAt = None,
      resetToken = Some("baz-bar"),
      resetTokenExpiresAt = Some(DateHelper.now().minusDays(1)),
      roles = Seq(Role.RoleCitizen),
      country = "FR",
      language = "fr",
      profile = None
    )

    val notExpiredResetTokenUserId: UserId = UserId("not-expired-reset-token-user-id")
    val validResetToken: String = "valid-reset-token"
    val notExpiredResetTokenUser = User(
      userId = notExpiredResetTokenUserId,
      email = "foo@exemple.com",
      firstName = None,
      lastName = None,
      lastIp = None,
      hashedPassword = None,
      enabled = true,
      emailVerified = true,
      lastConnection = DateHelper.now(),
      verificationToken = None,
      verificationTokenExpiresAt = None,
      resetToken = Some("valid-reset-token"),
      resetTokenExpiresAt = Some(DateHelper.now().plusDays(1)),
      roles = Seq(Role.RoleCitizen),
      country = "FR",
      language = "fr",
      profile = None
    )

    Mockito
      .when(persistentUserService.findUserByUserIdAndResetToken(fooBarUserId, "baz-bar"))
      .thenReturn(Future.successful(Some(fooBarUser)))
    Mockito
      .when(persistentUserService.findUserByUserIdAndResetToken(fooBarUserId, "bad-bad"))
      .thenReturn(Future.successful(None))
    Mockito
      .when(persistentUserService.findUserByUserIdAndResetToken(UserId("bad-foo"), "baz-bar"))
      .thenReturn(Future.successful(None))
    Mockito
      .when(persistentUserService.findUserByUserIdAndResetToken(notExpiredResetTokenUserId, validResetToken))
      .thenReturn(Future.successful(Some(notExpiredResetTokenUser)))

    Mockito.when(userService.requestPasswordReset(any[UserId])).thenReturn(Future.successful(true))

    scenario("Reset a password from an existing email") {
      Given("a registered user with an email john.doe@example.com")
      When("I reset password with john.doe@example.com")
      val request =
        """
          |{
          | "email": "john.doe@example.com"
          |}
        """.stripMargin

      val resetPasswordRequestRoute = Post(
        "/user/reset-password/request-reset",
        HttpEntity(ContentTypes.`application/json`, request)
      ) ~> routes

      Then("The existence of email is checked")
      And("I get a valid response")
      resetPasswordRequestRoute ~> check {
        verify(eventBusService, times(1)).publish(any[ResetPasswordEvent])
        status should be(StatusCodes.NoContent)
      }
      And("a user Event ResetPasswordEvent is emitted")
    }

    scenario("Reset a password from an nonexistent email") {
      Given("an nonexistent email fake@example.com")
      When("I reset password with fake@example.com")
      val request =
        """
          |{
          | "email": "fake@example.com"
          |}
        """.stripMargin

      val resetPasswordRequestRoute = Post(
        "/user/reset-password/request-reset",
        HttpEntity(ContentTypes.`application/json`, request)
      ) ~> routes

      Then("The existence of email is checked")
      And("I get a not found response")
      resetPasswordRequestRoute ~> check {
        status should be(StatusCodes.NotFound)
      }
      And("any user Event ResetPasswordEvent is emitted")
    }

    scenario("Reset a password from an invalid email") {
      Given("an invalid email invalidexample.com")
      When("I reset password with invalidexample.com")
      val request =
        """
          |{
          | "email": "invalidexample.com"
          |}
        """.stripMargin

      val resetPasswordRequestRoute = Post(
        "/user/reset-password/request-reset",
        HttpEntity(ContentTypes.`application/json`, request)
      ) ~> routes

      Then("The existence of email is not checked")
      And("I get a bad request response")
      resetPasswordRequestRoute ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(Some(ValidationError("email", Some("email is not a valid email"))))
      }
      And("any user Event ResetPasswordEvent is emitted")

    }

    scenario("Check a reset token from an existing user") {
      Given("a registered user with an uuid not-expired-reset-token-user-id and a reset token valid-reset-token")
      When("I check that reset token is for the right user")

      val resetPasswordCheckRoute = Post(
        "/user/reset-password/check-validity/not-expired-reset-token-user-id/valid-reset-token",
        HttpEntity(ContentTypes.`application/json`, "")
      ) ~> routes

      Then("The reset Token is for the right passed user and the reset token is not expired")
      And("I get a valid response")
      resetPasswordCheckRoute ~> check {
        status should be(StatusCodes.NoContent)
      }

      And("an empty result is returned")
    }

    scenario("Check an expired reset token from an existing user") {
      Given("a registered user with an uuid foo-bar and a reset token baz-bar")
      When("I check that reset token is for the right user and is not expired")

      val resetPasswordCheckRoute = Post(
        "/user/reset-password/check-validity/foo-bar/baz-bar",
        HttpEntity(ContentTypes.`application/json`, "")
      ) ~> routes

      Then("The reset Token is for the right passed user but is expired")
      And("I get a valid response")
      resetPasswordCheckRoute ~> check {
        status should be(StatusCodes.BadRequest)
      }

      And("an empty result is returned")
    }

    scenario("Check a bad reset token from an existing user") {
      Given("a registered user with an uuid foo-bar and a reset token baz-bar")
      When("I check that reset token is for the right user")

      val resetPasswordCheckRoute = Post(
        "/user/reset-password-check/foo-bar/bad-bad",
        HttpEntity(ContentTypes.`application/json`, "")
      ) ~> routes

      Then("The user with this reset Token and userId is not found")
      And("I get a not found response")
      resetPasswordCheckRoute ~> check {
        status should be(StatusCodes.NotFound)
      }
      And("a not found result is returned")
    }

    scenario("Check a reset token with a bad user") {
      Given("a registered user with an uuid bad-foo and a reset token baz-bar")
      When("I check that reset token is for the right user")

      val resetPasswordCheckRoute = Post(
        "/user/reset-password/check-validity/bad-foo/baz-bar",
        HttpEntity(ContentTypes.`application/json`, "")
      ) ~> routes

      Then("The user with this reset Token and userId")
      And("I get a not found response")
      resetPasswordCheckRoute ~> check {
        status should be(StatusCodes.NotFound)
      }
      And("a not found result is returned")
    }

    scenario("reset the password of a valid user with valid token") {
      Mockito
        .when(
          userService
            .updatePassword(notExpiredResetTokenUserId, validResetToken, "mynewpassword")
        )
        .thenReturn(Future.successful(true))

      Given("a registered user with an uuid not-expired-reset-token-user-id and a reset token valid-reset-token")
      When("I check that reset token is for the right user")

      val data =
        """
          |{
          | "resetToken": "valid-reset-token",
          | "password": "mynewpassword"
          |}
        """.stripMargin

      val resetPasswordRoute = Post(
        "/user/reset-password/change-password/not-expired-reset-token-user-id",
        HttpEntity(ContentTypes.`application/json`, data)
      ) ~> routes

      Then("The user with this reset Token and userId update his password")
      And("I get a successful response")
      resetPasswordRoute ~> check {
        status should be(StatusCodes.NoContent)
      }
      And("a success result is returned")
    }
  }

  feature("get the connected user") {
    scenario("no auth token") {
      Get("/user/me") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("valid token") {
      val token: String = "TOKEN"
      val accessToken: AccessToken =
        AccessToken("ACCESS_TOKEN", None, None, None, Date.from(Instant.now))
      val fakeAuthInfo: AuthInfo[UserRights] =
        AuthInfo(UserRights(UserId("user-id"), Seq(Role.RoleCitizen)), None, None, None)

      when(userService.getUser(ArgumentMatchers.eq(UserId("user-id")))).thenReturn(
        Future.successful(
          Some(
            User(
              userId = UserId("user-id"),
              email = "my-email@yopmail.com",
              firstName = None,
              lastName = None,
              lastIp = None,
              hashedPassword = None,
              enabled = true,
              emailVerified = true,
              lastConnection = DateHelper.now(),
              verificationToken = None,
              verificationTokenExpiresAt = None,
              resetToken = None,
              resetTokenExpiresAt = None,
              roles = Seq(Role.RoleCitizen),
              country = "FR",
              language = "fr",
              profile = None,
              createdAt = None,
              updatedAt = None
            )
          )
        )
      )

      Mockito
        .when(oauth2DataHandler.findAccessToken(ArgumentMatchers.same(token)))
        .thenReturn(Future.successful(Some(accessToken)))
      Mockito
        .when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.same(accessToken)))
        .thenReturn(Future.successful(Some(fakeAuthInfo)))
      Get("/user/me").withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }
}
