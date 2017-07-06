package org.make.api.user

import java.time.{LocalDate, ZonedDateTime}

import org.make.api.MakeUnitTest
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.{PersistentTokenServiceComponent, UserTokenGenerator, UserTokenGeneratorComponent}
import org.make.api.user.UserExceptions.EmailAlreadyRegistredException
import org.make.core.profile.Profile
import org.make.core.user.Role.RoleCitizen
import org.make.core.user.{User, UserId}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class UserServiceTest
    extends MakeUnitTest
    with UserServiceComponent
    with IdGeneratorComponent
    with UserTokenGeneratorComponent
    with PersistentUserServiceComponent
    with PersistentTokenServiceComponent {

  override val userService: UserService = new UserService
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val persistentTokenService: PersistentTokenService = mock[PersistentTokenService]
  override val persistentClientService: PersistentClientService = mock[PersistentClientService]
  override val readExecutionContext: ExecutionContext = ExecutionContext.Implicits.global
  override val writeExecutionContext: ExecutionContext = ExecutionContext.Implicits.global
  override val userTokenGenerator: UserTokenGenerator = mock[UserTokenGenerator]

  Mockito.when(userTokenGenerator.generateVerificationToken()).thenReturn(Future.successful(("TOKEN", "HASHED_TOKEN")))

  feature("register user") {
    scenario("successful register user") {
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(false))

      val returnedProfile = Profile(
        dateOfBirth = Some(LocalDate.parse("1984-10-11")),
        avatarUrl = None,
        profession = None,
        phoneNumber = None,
        twitterId = None,
        facebookId = None,
        googleId = None,
        gender = None,
        genderName = None,
        departmentNumber = None,
        karmaLevel = None,
        locale = None
      )

      val returnedUser = User(
        userId = UserId("AAA-BBB-CCC"),
        email = "any@mail.com",
        firstName = Some("olive"),
        lastName = Some("tom"),
        lastIp = "127.0.0.1",
        hashedPassword = "passpass",
        salt = "salto",
        enabled = true,
        verified = false,
        lastConnection = ZonedDateTime.now(),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(ZonedDateTime.now),
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq(RoleCitizen),
        profile = Some(returnedProfile)
      )

      Mockito
        .when(
          persistentUserService
            .persist(any[User])
        )
        .thenReturn(Future.successful(returnedUser))

      val futureUser = userService.register(
        "any@mail.com",
        Some("tom"),
        Some("olive"),
        "passopasso",
        "127.0.0.1",
        Some(LocalDate.parse("1984-10-11"))
      )()

      whenReady(futureUser, Timeout(2.seconds)) { user =>
        user shouldBe a[User]
        user.email should be("any@mail.com")
        user.firstName should be(Some("olive"))
        user.lastName should be(Some("tom"))
        user.profile.get.dateOfBirth should be(Some(LocalDate.parse("1984-10-11")))
      }
    }

    scenario("email already registred") {
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(true))

      val futureUser = userService.register(
        "exist@mail.com",
        Some("tom"),
        Some("olive"),
        "passopasso",
        "127.0.0.1",
        Some(LocalDate.parse("1984-10-11"))
      )

      whenReady(futureUser.failed, Timeout(3.seconds)) { exception =>
        exception shouldBe a[EmailAlreadyRegistredException]
        exception.asInstanceOf[EmailAlreadyRegistredException].email should be("exist@mail.com")
      }
    }
  }
}
