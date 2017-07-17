package org.make.api.user

import java.net.InetAddress
import java.time.{LocalDate, ZonedDateTime}

import akka.http.scaladsl.model.headers.`Remote-Address`
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RemoteAddress, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.circe.generic.auto._
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.api.technical.auth._
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent}
import org.make.api.user.UserExceptions.EmailAlreadyRegistredException
import org.make.api.user.social.{FacebookApi, GoogleApi, SocialService, SocialServiceComponent}
import org.make.api.{MakeApi, MakeTest}
import org.make.core.ValidationError
import org.make.core.user.{ResetPasswordEvent, User, UserId}
import org.mockito.ArgumentMatchers.{any, nullable, eq => matches}
import org.mockito.Mockito._
import org.mockito.{ArgumentMatchers, Mockito}

import scala.concurrent.{ExecutionContext, Future}

class UserApiTest
    extends MakeTest
    with ScalatestRouteTest
    with UserApi
    with SocialServiceComponent
    with IdGeneratorComponent
    with EventBusServiceComponent
    with UserTokenGeneratorComponent
    with OauthTokenGeneratorComponent
    with PersistentTokenServiceComponent
    with PersistentClientServiceComponent
    with PersistentUserServiceComponent
    with UserServiceComponent
    with MakeDataHandlerComponent
    with MakeDBExecutionContextComponent {

  override val userService: UserService = mock[UserService]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val socialService: SocialService = mock[SocialService]
  override val facebookApi: FacebookApi = mock[FacebookApi]
  override val googleApi: GoogleApi = mock[GoogleApi]
  override val persistentTokenService: PersistentTokenService = mock[PersistentTokenService]
  override val persistentClientService: PersistentClientService = mock[PersistentClientService]
  override val readExecutionContext: EC = ECGlobal
  override val writeExecutionContext: EC = ECGlobal
  override val userTokenGenerator: UserTokenGenerator = mock[UserTokenGenerator]
  override val oauthTokenGenerator: OauthTokenGenerator = mock[OauthTokenGenerator]
  override val eventBusService: EventBusService = mock[EventBusService]

  when(idGenerator.nextId()).thenReturn("some-id")

  val routes: Route = Route.seal(handleRejections(MakeApi.rejectionHandler) {
    handleExceptions(MakeApi.exceptionHandler) {
      userRoutes
    }
  })

  feature("register user") {
    scenario("successful register user") {
      Mockito
        .when(
          userService
            .register(
              any[String],
              any[Option[String]],
              any[Option[String]],
              any[Option[String]],
              any[Option[String]],
              any[Option[LocalDate]]
            )(any[ExecutionContext])
        )
        .thenReturn(
          Future.successful(
            User(
              userId = UserId("ABCD"),
              email = "foo@bar.com",
              firstName = Some("olive"),
              lastName = Some("tom"),
              lastIp = Some("127.0.0.1"),
              hashedPassword = Some("passpass"),
              salt = Some("salto"),
              enabled = true,
              verified = false,
              lastConnection = ZonedDateTime.now(),
              verificationToken = Some("token"),
              verificationTokenExpiresAt = Some(ZonedDateTime.now()),
              resetToken = None,
              resetTokenExpiresAt = None,
              roles = Seq.empty,
              profile = None
            )
          )
        )
      val request =
        """
          |{
          | "email": "foo@bar.com",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypass",
          | "dateOfBirth": "1997-12-02"
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")
      Post("/user", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr))) ~> routes ~> check {
        status should be(StatusCodes.Created)
        verify(userService).register(
          matches("foo@bar.com"),
          matches(Some("olive")),
          matches(Some("tom")),
          matches(Some("mypass")),
          matches(Some("192.0.0.1")),
          matches(Some(LocalDate.parse("1997-12-02")))
        )(nullable(classOf[ExecutionContext]))
      }
    }

    scenario("validation failed for existing email") {
      Mockito
        .when(
          userService
            .register(
              any[String],
              any[Option[String]],
              any[Option[String]],
              any[Option[String]],
              any[Option[String]],
              any[Option[LocalDate]]
            )(any[ExecutionContext])
        )
        .thenReturn(Future.failed(EmailAlreadyRegistredException("foo@bar.com")))

      val request =
        """
          |{
          | "email": "foo@bar.com",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypass",
          | "dateOfBirth": "1997-12-02"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(Some(ValidationError("email", "Email foo@bar.com already exist")))
      }
    }

    scenario("validation failed for malformed email") {
      val request =
        """
          |{
          | "email": "foo",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypass",
          | "dateOfBirth": "1997-12-02"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(Some(ValidationError("email", "email is not a valid email")))
      }
    }

    scenario("validation failed for malformed date of birth") {
      pending
      val request =
        """
          |{
          | "email": "foo",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypass",
          | "dateOfBirth": "foo-12-02"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val dateOfBirthError = errors.find(_.field == "dateOfBirth")
        dateOfBirthError should be(Some(ValidationError("dateOfBirth", "date of birth is not valid")))
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
            .login(any[String], any[String], any[Option[String]])
        )
        .thenReturn(
          Future.successful(
            TokenResponse(
              token_type = "Bearer",
              access_token = "access_token",
              expires_in = 1000,
              refresh_token = "refresh_token"
            )
          )
        )
      val request =
        """
          |{
          | "provider": "google",
          | "token": "ABCDEFGHIJK"
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")
      Post("/user/login/social", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr))) ~> routes ~> check {
        status should be(StatusCodes.Created)
        verify(socialService).login(matches("google"), matches("ABCDEFGHIJK"), matches(Some("192.0.0.1")))
      }
    }

    scenario("bad request when login social user") {
      Mockito
        .when(
          socialService
            .login(any[String], any[String], any[Option[String]])
        )
        .thenReturn(
          Future.successful(
            TokenResponse(
              token_type = "Bearer",
              access_token = "access_token",
              expires_in = 1000,
              refresh_token = "refresh_token"
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

    val johnDoeId = UserId("JOHNDOE")
    Mockito
      .when(persistentUserService.findUserIdByEmail("john.doe@example.com"))
      .thenReturn(Future.successful(Some(johnDoeId)))
    Mockito
      .when(persistentUserService.findUserIdByEmail("invalidexample.com"))
      .thenAnswer(_ => throw new IllegalArgumentException("findUserIdByEmail should be called with valid email"))
    when(eventBusService.publish(ArgumentMatchers.any[ResetPasswordEvent]))
      .thenAnswer(
        invocation =>
          if (!invocation.getArgument[ResetPasswordEvent](0).userId.equals(johnDoeId)) {
            throw new IllegalArgumentException("UserId not match")
        }
      )
    Mockito
      .when(persistentUserService.findUserIdByEmail("fake@example.com"))
      .thenReturn(Future.successful(None))

    scenario("Reset a password from an existing email") {
      Given("a registered user with an email john.doe@example.com")
      When("I reset password with john.doe@example.com")
      val request =
        """
          |{
          | "email": "john.doe@example.com"
          |}
        """.stripMargin

      val resetPasswordRoute = Post("/reset-password", HttpEntity(ContentTypes.`application/json`, request)) ~> routes

      Then("The existence of email is checked")
      And("I get a valid response")
      resetPasswordRoute ~> check {
        status should be(StatusCodes.OK)
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

      val resetPasswordRoute = Post("/reset-password", HttpEntity(ContentTypes.`application/json`, request)) ~> routes

      Then("The existence of email is checked")
      And("I get a not found response")
      resetPasswordRoute ~> check {
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

      val resetPasswordRoute = Post("/reset-password", HttpEntity(ContentTypes.`application/json`, request)) ~> routes

      Then("The existence of email is not checked")
      And("I get a bad request response")
      resetPasswordRoute ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(Some(ValidationError("email", "email is not a valid email")))
      }
      And("any user Event ResetPasswordEvent is emitted")

    }
  }
}
