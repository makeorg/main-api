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

package org.make.api.user

import java.net.InetAddress
import java.time.{Instant, LocalDate, ZonedDateTime}
import java.util.Date

import akka.http.scaladsl.model.headers.{`Remote-Address`, Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RemoteAddress, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.{OperationService, OperationServiceComponent}
import org.make.api.proposal.{
  ProposalResult,
  ProposalService,
  ProposalServiceComponent,
  ProposalsResultSeededResponse,
  _
}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.technical._
import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.api.technical.auth._
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user.social._
import org.make.api.userhistory.UserEvent.ResetPasswordEvent
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent
import org.make.api.{ActorSystemComponent, MakeApi, MakeApiTestBase}
import org.make.core.auth.UserRights
import org.make.core.operation.{Operation, OperationId, OperationStatus}
import org.make.core.profile.Profile
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.reference.{Country, Language}
import org.make.core.user.{Role, User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationError}
import org.mockito.ArgumentMatchers.{any, eq => matches}
import org.mockito.Mockito._
import org.mockito.{ArgumentMatchers, Mockito}
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class UserApiTest
    extends MakeApiTestBase
    with UserApi
    with ProposalServiceComponent
    with OperationServiceComponent
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
  override val proposalService: ProposalService = mock[ProposalService]
  override val operationService: OperationService = mock[OperationService]

  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  private val oauthConfiguration = mock[makeSettings.Oauth.type]

  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(makeSettings.Oauth).thenReturn(oauthConfiguration)
  when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  when(sessionCookieConfiguration.isSecure).thenReturn(false)
  when(idGenerator.nextId()).thenReturn("some-id")
  when(sessionCookieConfiguration.lifetime).thenReturn(Duration("20 minutes"))

  override val proposalJournal: MakeReadJournal = mock[MakeReadJournal]
  override val sequenceJournal: MakeReadJournal = mock[MakeReadJournal]
  override val userJournal: MakeReadJournal = mock[MakeReadJournal]
  override val sessionJournal: MakeReadJournal = mock[MakeReadJournal]

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
    country = Country("FR"),
    language = Language("fr"),
    profile = None
  )

  val expiresInSecond = 1000

  val sylvain: User =
    fakeUser.copy(userId = UserId("sylvain-user-id"), email = "sylvain@example.com", firstName = Some("Sylvain"))
  val vincent: User =
    fakeUser.copy(userId = UserId("vincent-user-id"), email = "vincent@example.com", firstName = Some("Vincent"))

  val token: String = "TOKEN_GET_USERS_PROPOSALS"
  val accessToken: AccessToken =
    AccessToken("ACCESS_TOKEN_GET_USERS_PROPOSALS", None, None, None, Date.from(Instant.now))
  val fakeAuthInfo: AuthInfo[UserRights] =
    AuthInfo(UserRights(sylvain.userId, Seq(Role.RoleCitizen)), None, None, None)
  when(userService.getUser(ArgumentMatchers.eq(sylvain.userId)))
    .thenReturn(Future.successful(Some(sylvain)))

  val token2: String = "TOKEN_GET_USERS_PROPOSALS_2"
  val accessToken2: AccessToken =
    AccessToken("ACCESS_TOKEN_GET_USERS_PROPOSALS_2", None, None, None, Date.from(Instant.now))
  val fakeAuthInfo2: AuthInfo[UserRights] =
    AuthInfo(UserRights(vincent.userId, Seq(Role.RoleCitizen)), None, None, None)
  when(userService.getUser(ArgumentMatchers.eq(vincent.userId)))
    .thenReturn(Future.successful(Some(vincent)))

  Mockito
    .when(oauth2DataHandler.findAccessToken(ArgumentMatchers.same(token)))
    .thenReturn(Future.successful(Some(accessToken)))
  Mockito
    .when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.same(accessToken)))
    .thenReturn(Future.successful(Some(fakeAuthInfo)))
  Mockito
    .when(oauth2DataHandler.findAccessToken(ArgumentMatchers.same(token2)))
    .thenReturn(Future.successful(Some(accessToken2)))
  Mockito
    .when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.same(accessToken2)))
    .thenReturn(Future.successful(Some(fakeAuthInfo2)))

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
              country = Country("FR"),
              language = Language("fr")
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
              country = Country("FR"),
              language = Language("fr")
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
          Some(
            ValidationError(
              "dateOfBirth",
              Some(
                "Could not decode [\"foo-12-02\"] at [.dateOfBirth] as " +
                  "[foo-12-02 is not a valid date, it should match yyyy-MM-dd]."
              )
            )
          )
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
            .login(any[String], any[String], any[Country], any[Language], any[Option[String]], any[RequestContext])
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
          matches(Country("FR")),
          matches(Language("fr")),
          matches(Some("192.0.0.1")),
          any[RequestContext]
        )
      }
    }

    scenario("bad request when login social user") {
      Mockito
        .when(
          socialService
            .login(any[String], any[String], any[Country], any[Language], any[Option[String]], any[RequestContext])
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
      country = Country("FR"),
      language = Language("fr"),
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
      country = Country("FR"),
      language = Language("fr"),
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
      country = Country("FR"),
      language = Language("fr"),
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
            .updatePassword(notExpiredResetTokenUserId, Some(validResetToken), "mynewpassword")
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
              country = Country("FR"),
              language = Language("fr"),
              profile = None,
              createdAt = None,
              updatedAt = None
            )
          )
        )
      )

      Mockito.when(userService.getFollowedUsers(ArgumentMatchers.any[UserId])).thenReturn(Future.successful(Seq.empty))

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

  feature("get voted proposals of an user") {

    val paul: User = fakeUser.copy(
      userId = UserId("11111111-aaaa-2222-bbbb-333333333333"),
      email = "paul-email@example.com",
      firstName = Some("paul")
    )
    val gaston: User =
      fakeUser.copy(userId = UserId("22222222-bbbbbb"), email = "gaston-email@example.com", firstName = Some("gaston"))

    val token: String = "TOKEN_GET_USERS_VOTES"
    val accessToken: AccessToken =
      AccessToken("ACCESS_TOKEN_GET_USERS_VOTES", None, None, None, Date.from(Instant.now))
    val fakeAuthInfo: AuthInfo[UserRights] =
      AuthInfo(UserRights(paul.userId, Seq(Role.RoleCitizen)), None, None, None)
    when(userService.getUser(ArgumentMatchers.eq(paul.userId)))
      .thenReturn(Future.successful(Some(paul)))

    val token2: String = "TOKEN_GET_USERS_2"
    val accessToken2: AccessToken =
      AccessToken("ACCESS_TOKEN_GET_USERS_VOTES82", None, None, None, Date.from(Instant.now))
    val fakeAuthInfo2: AuthInfo[UserRights] =
      AuthInfo(UserRights(gaston.userId, Seq(Role.RoleCitizen)), None, None, None)
    when(userService.getUser(ArgumentMatchers.eq(gaston.userId)))
      .thenReturn(Future.successful(Some(paul.copy(userId = gaston.userId))))

    val indexedProposal = IndexedProposal(
      id = ProposalId("22222222-2222-2222-2222-222222222222"),
      country = Country("FR"),
      language = Language("fr"),
      userId = paul.userId,
      content = "Il faut que ma proposition d'opération soit en CSV.",
      slug = "il-faut-que-ma-proposition-d-operation-soit-en-csv",
      createdAt = ZonedDateTime.now,
      updatedAt = Some(ZonedDateTime.now),
      votesCount = 0,
      toEnrich = false,
      votes = Seq.empty,
      scores = IndexedScores.empty,
      context = None,
      trending = None,
      labels = Seq.empty,
      author = Author(
        firstName = Some("Paul"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("11111"),
        age = Some(26),
        avatarUrl = None
      ),
      organisations = Seq.empty,
      themeId = None,
      tags = Seq.empty,
      status = ProposalStatus.Accepted,
      ideaId = None,
      operationId = Some(OperationId("operation1")),
      questionId = None
    )
    val proposalResult: ProposalResult =
      ProposalResult(indexedProposal = indexedProposal, myProposal = true, voteAndQualifications = None)

    Mockito
      .when(oauth2DataHandler.findAccessToken(ArgumentMatchers.same(token)))
      .thenReturn(Future.successful(Some(accessToken)))
    Mockito
      .when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.same(accessToken)))
      .thenReturn(Future.successful(Some(fakeAuthInfo)))
    Mockito
      .when(oauth2DataHandler.findAccessToken(ArgumentMatchers.same(token2)))
      .thenReturn(Future.successful(Some(accessToken2)))
    Mockito
      .when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.same(accessToken2)))
      .thenReturn(Future.successful(Some(fakeAuthInfo2)))

    Mockito
      .when(
        operationService.find(
          ArgumentMatchers.any[Option[String]],
          ArgumentMatchers.any[Option[Country]],
          ArgumentMatchers.any[Option[String]],
          ArgumentMatchers.any[Option[LocalDate]]
        )
      )
      .thenReturn(
        Future.successful(
          Seq(
            Operation(
              operationId = OperationId("operation1"),
              status = OperationStatus.Pending,
              slug = "operation1",
              defaultLanguage = Language("fr"),
              allowedSources = Seq("core"),
              events = List.empty,
              createdAt = Some(DateHelper.now()),
              updatedAt = None,
              countriesConfiguration = Seq.empty
            )
          )
        )
      )

    Mockito
      .when(
        proposalService
          .searchProposalsVotedByUser(
            userId = ArgumentMatchers.eq(paul.userId),
            filterVotes = ArgumentMatchers.eq(None),
            filterQualifications = ArgumentMatchers.eq(None),
            requestContext = ArgumentMatchers.any[RequestContext]
          )
      )
      .thenReturn(Future.successful(ProposalsResultResponse(total = 1, results = Seq(proposalResult))))
    Mockito
      .when(
        proposalService
          .searchProposalsVotedByUser(
            userId = ArgumentMatchers.eq(gaston.userId),
            filterVotes = ArgumentMatchers.eq(None),
            filterQualifications = ArgumentMatchers.eq(None),
            requestContext = ArgumentMatchers.any[RequestContext]
          )
      )
      .thenReturn(Future.successful(ProposalsResultResponse(total = 0, results = Seq.empty)))

    scenario("not authenticated") {
      Get("/user/11111111-aaaa-2222-bbbb-333333333333/votes") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated but userId parameter is different than connected user id") {
      Get("/user/xxxxxxxxxxxx/votes").withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated with empty voted proposals") {
      Get("/user/22222222-bbbbbb/votes").withHeaders(Authorization(OAuth2BearerToken(token2))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val result = entityAs[ProposalsResultSeededResponse]
        result.total should be(0)
        result.results should be(Seq.empty)
      }
    }

    scenario("authenticated with some voted proposals") {
      Get("/user/11111111-aaaa-2222-bbbb-333333333333/votes")
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val result = entityAs[ProposalsResultSeededResponse]
        result.total should be(1)
        result.results.head.id should be(ProposalId("22222222-2222-2222-2222-222222222222"))
      }
    }
  }

  feature("get user proposals") {

    val indexedProposal = IndexedProposal(
      id = ProposalId("333333-3333-3333-3333-33333333"),
      country = Country("FR"),
      language = Language("fr"),
      userId = sylvain.userId,
      content = "Il faut une proposition de Sylvain",
      slug = "il-faut-une-proposition-de-sylvain",
      createdAt = ZonedDateTime.now,
      updatedAt = Some(ZonedDateTime.now),
      votes = Seq.empty,
      votesCount = 0,
      toEnrich = false,
      scores = IndexedScores.empty,
      context = None,
      trending = None,
      labels = Seq.empty,
      author = Author(
        firstName = sylvain.firstName,
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("11111"),
        age = Some(22),
        avatarUrl = None
      ),
      organisations = Seq.empty,
      themeId = None,
      tags = Seq.empty,
      status = ProposalStatus.Accepted,
      ideaId = None,
      operationId = None,
      questionId = None
    )
    val proposalResult: ProposalResult =
      ProposalResult(indexedProposal = indexedProposal, myProposal = true, voteAndQualifications = None)

    Mockito
      .when(oauth2DataHandler.findAccessToken(ArgumentMatchers.same(token)))
      .thenReturn(Future.successful(Some(accessToken)))
    Mockito
      .when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.same(accessToken)))
      .thenReturn(Future.successful(Some(fakeAuthInfo)))
    Mockito
      .when(oauth2DataHandler.findAccessToken(ArgumentMatchers.same(token2)))
      .thenReturn(Future.successful(Some(accessToken2)))
    Mockito
      .when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.same(accessToken2)))
      .thenReturn(Future.successful(Some(fakeAuthInfo2)))

    Mockito
      .when(
        operationService.find(
          ArgumentMatchers.any[Option[String]],
          ArgumentMatchers.any[Option[Country]],
          ArgumentMatchers.any[Option[String]],
          ArgumentMatchers.any[Option[LocalDate]]
        )
      )
      .thenReturn(
        Future.successful(
          Seq(
            Operation(
              operationId = OperationId("operation1"),
              status = OperationStatus.Pending,
              slug = "operation1",
              defaultLanguage = Language("fr"),
              allowedSources = Seq("core"),
              events = List.empty,
              createdAt = Some(DateHelper.now()),
              updatedAt = None,
              countriesConfiguration = Seq.empty
            )
          )
        )
      )

    Mockito
      .when(
        proposalService
          .searchForUser(
            userId = ArgumentMatchers.any[Option[UserId]],
            query = ArgumentMatchers
              .eq(
                SearchQuery(
                  filters = Some(
                    SearchFilters(
                      user = Some(UserSearchFilter(userId = sylvain.userId)),
                      status = Some(
                        StatusSearchFilter(
                          status = Seq(
                            ProposalStatus.Pending,
                            ProposalStatus.Postponed,
                            ProposalStatus.Accepted,
                            ProposalStatus.Refused
                          )
                        )
                      )
                    )
                  )
                )
              ),
            requestContext = ArgumentMatchers.any[RequestContext]
          )
      )
      .thenReturn(
        Future.successful(ProposalsResultSeededResponse(total = 1, results = Seq(proposalResult), seed = None))
      )

    scenario("not authenticated") {
      Get("/user/sylvain-user-id/proposals") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated but userId parameter is different than connected user id") {
      Get("/user/vincent-user-id/proposals").withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated with some proposals") {
      Get("/user/sylvain-user-id/proposals")
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val result = entityAs[ProposalsResultSeededResponse]
        result.total should be(1)
        result.results.head.id should be(indexedProposal.id)
      }
    }
  }

  feature("update a user") {
    scenario("authentificated with unauthorized user") {

      when(userService.getUser(ArgumentMatchers.eq(UserId("BAD")))).thenReturn(Future.successful(Some(fakeUser)))

      Mockito
        .when(
          userService
            .update(any[User], any[RequestContext])
        )
        .thenReturn(Future.successful(fakeUser))

      val request =
        """
          |{
          | "firstName": "unauthorized",
          | "lastName": "user",
          | "dateOfBirth": "1997-12-02",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")

      Patch("/user", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr))) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("successful update user") {
      val token: String = "TOKEN"
      val accessToken: AccessToken =
        AccessToken("ACCESS_TOKEN", None, None, None, Date.from(Instant.now))
      val fakeAuthInfo: AuthInfo[UserRights] =
        AuthInfo(UserRights(UserId("ABCD"), Seq(Role.RoleCitizen)), None, None, None)

      when(userService.getUser(ArgumentMatchers.eq(UserId("ABCD")))).thenReturn(Future.successful(Some(fakeUser)))

      Mockito
        .when(
          userService
            .update(any[User], any[RequestContext])
        )
        .thenReturn(Future.successful(fakeUser))
      val request =
        """
          |{
          | "firstName": "olive",
          | "lastName": "tom",
          | "dateOfBirth": "1997-12-02",
          | "country": "IT",
          | "language": "it"
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")

      Mockito
        .when(oauth2DataHandler.findAccessToken(ArgumentMatchers.same(token)))
        .thenReturn(Future.successful(Some(accessToken)))
      Mockito
        .when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.same(accessToken)))
        .thenReturn(Future.successful(Some(fakeAuthInfo)))

      Patch("/user", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr)))
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
        verify(userService).update(
          matches(
            fakeUser.copy(
              firstName = Some("olive"),
              lastName = Some("tom"),
              country = Country("IT"),
              language = Language("it"),
              profile = Some(
                Profile.default
                  .copy(dateOfBirth = Some(LocalDate.parse("1997-12-02")), optInNewsletter = false)
              )
            )
          ),
          any[RequestContext]
        )
      }
    }

    scenario("user remove age from the front") {
      val token: String = "TOKEN"
      val accessToken: AccessToken =
        AccessToken("ACCESS_TOKEN", None, None, None, Date.from(Instant.now))
      val fakeAuthInfo: AuthInfo[UserRights] =
        AuthInfo(UserRights(UserId("ABCD"), Seq(Role.RoleCitizen)), None, None, None)

      when(userService.getUser(ArgumentMatchers.eq(UserId("ABCD")))).thenReturn(Future.successful(Some(fakeUser)))

      Mockito
        .when(
          userService
            .update(any[User], any[RequestContext])
        )
        .thenReturn(Future.successful(fakeUser))
      val request =
        """
          |{
          | "firstName": "olive",
          | "lastName": "tom",
          | "dateOfBirth": "",
          | "country": "IT",
          | "language": "it"
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")

      Mockito
        .when(oauth2DataHandler.findAccessToken(ArgumentMatchers.same(token)))
        .thenReturn(Future.successful(Some(accessToken)))
      Mockito
        .when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.same(accessToken)))
        .thenReturn(Future.successful(Some(fakeAuthInfo)))

      Patch("/user", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr)))
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
        verify(userService).update(
          matches(
            fakeUser.copy(
              firstName = Some("olive"),
              lastName = Some("tom"),
              country = Country("IT"),
              language = Language("it"),
              profile = Some(
                Profile.default
                  .copy(dateOfBirth = None, optInNewsletter = false)
              )
            )
          ),
          any[RequestContext]
        )
      }
    }
  }

  feature("modify user password") {

    val fakeRequest =
      """
        |{
        | "actualPassword": "",
        | "newPassword": "12345678"
        |}
      """.stripMargin

    scenario("unauthenticated user") {
      Post("/user/sylvain-user-id/change-password", HttpEntity(ContentTypes.`application/json`, fakeRequest)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated but userId parameter is different than connected user id") {
      Post("/user/vincent-user-id/change-password", HttpEntity(ContentTypes.`application/json`, fakeRequest))
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("social login user change password") {

      val request =
        """
          |{
          | "newPassword": "mynewpassword"
          |}
        """.stripMargin

      Mockito
        .when(
          userService
            .getUserByUserIdAndPassword(ArgumentMatchers.any[UserId], ArgumentMatchers.same(None))
        )
        .thenReturn(Future.successful(Some(sylvain)))

      Mockito
        .when(
          userService
            .updatePassword(ArgumentMatchers.any[UserId], ArgumentMatchers.same(None), ArgumentMatchers.any[String])
        )
        .thenReturn(Future.successful(true))

      Post("/user/sylvain-user-id/change-password", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  feature("delete user") {

    val fakeRequest =
      """
        | {
        |   "password": "mypassword"
        | }
      """.stripMargin

    scenario("unauthenticated user") {
      Post("/user/sylvain-user-id/delete", HttpEntity(ContentTypes.`application/json`, fakeRequest)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated but userId parameter is different than connected user id") {
      Post("/user/vincent-user-id/delete", HttpEntity(ContentTypes.`application/json`, fakeRequest))
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("wrong password") {
      Mockito
        .when(
          userService
            .getUserByUserIdAndPassword(ArgumentMatchers.any[UserId], ArgumentMatchers.any[Option[String]])
        )
        .thenReturn(Future.successful(None))

      Post("/user/sylvain-user-id/delete", HttpEntity(ContentTypes.`application/json`, fakeRequest))
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("user request anonymization") {
      Mockito
        .when(
          userService
            .getUserByUserIdAndPassword(ArgumentMatchers.any[UserId], ArgumentMatchers.any[Option[String]])
        )
        .thenReturn(Future.successful(Some(sylvain)))

      Mockito
        .when(userService.anonymize(ArgumentMatchers.any[User]))
        .thenReturn(Future.successful({}))

      Mockito
        .when(oauth2DataHandler.removeTokenByUserId(ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful(1))

      Post("/user/sylvain-user-id/delete", HttpEntity(ContentTypes.`application/json`, fakeRequest))
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  feature("follow user") {
    scenario("unconnected user") {
      Post("/user/sylvain-user-id/follow") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("try to follow unexistant user") {
      Mockito
        .when(userService.getUser(ArgumentMatchers.eq(UserId("non-existant"))))
        .thenReturn(Future.successful(None))

      Post("/user/non-existant/follow")
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    scenario("try to follow a user without public profile") {
      Mockito
        .when(userService.getUser(ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful(Some(fakeUser)))
      Post("/user/ABCD/follow")
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("try to follow a user already followed") {
      Mockito
        .when(userService.getUser(ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful(Some(fakeUser.copy(publicProfile = true))))
      Mockito
        .when(userService.getFollowedUsers(ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful(Seq(fakeUser.userId)))
      Mockito
        .when(userService.followUser(ArgumentMatchers.any[UserId], ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful(fakeUser.userId))
      Post("/user/ABCD/follow")
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    scenario("successfully follow a user") {
      Mockito
        .when(userService.getUser(ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful(Some(fakeUser.copy(publicProfile = true))))
      Mockito
        .when(userService.getFollowedUsers(ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful(Seq.empty))
      Mockito
        .when(userService.followUser(ArgumentMatchers.any[UserId], ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful(fakeUser.userId))
      Post("/user/ABCD/follow")
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  feature("unfollow a user") {
    scenario("unconnected user") {
      Post("/user/sylvain-user-id/unfollow") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("try to unfollow unexistant user") {
      Mockito
        .when(userService.getUser(ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful(None))
      Post("/user/non-existant/unfollow")
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    scenario("try to unfollow a user already unfollowed") {
      Mockito
        .when(userService.getUser(ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful(Some(fakeUser)))
      Mockito
        .when(userService.getFollowedUsers(ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful(Seq.empty))
      Post("/user/ABCD/unfollow")
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    scenario("successfully unfollow a user") {
      Mockito
        .when(userService.getUser(ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful(Some(fakeUser.copy(publicProfile = true))))
      Mockito
        .when(userService.getFollowedUsers(ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful(Seq(UserId("ABCD"))))
      Mockito
        .when(userService.unfollowUser(ArgumentMatchers.any[UserId], ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful(fakeUser.userId))
      Post("/user/ABCD/unfollow")
        .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }
}
