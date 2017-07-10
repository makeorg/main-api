package org.make.api.user

import java.net.InetAddress
import java.time.{LocalDate, ZonedDateTime}

import akka.http.scaladsl.model.headers.`Remote-Address`
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RemoteAddress, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.circe.generic.auto._
import org.make.api.MakeApi
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.api.technical.auth._
import org.make.api.user.UserExceptions.EmailAlreadyRegistredException
import org.make.api.user.social.{FacebookApi, GoogleApi, SocialService, SocialServiceComponent}
import org.make.core.ValidationError
import org.make.core.user.{User, UserId}
import org.mockito.ArgumentMatchers.{any, nullable, eq => matches}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FeatureSpec, Matchers}

import scala.concurrent.{ExecutionContext, Future}

class UserApiTest
    extends FeatureSpec
    with Matchers
    with ScalatestRouteTest
    with MockitoSugar
    with UserApi
    with UserServiceComponent
    with PersistentUserServiceComponent
    with SocialServiceComponent
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with PersistentTokenServiceComponent
    with PersistentClientServiceComponent
    with UserTokenGeneratorComponent
    with OauthTokenGeneratorComponent
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

    scenario("validation failed for existant email") {
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

    scenario("validation failed for malformatted email") {
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

    scenario("validation failed for malformatted date of birth") {
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
}
