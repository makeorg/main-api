package org.make.api.user

import java.time.LocalDate

import org.make.api.MakeUnitTest
import org.make.api.technical.auth._
import org.make.api.technical.{EventBusService, EventBusServiceComponent, IdGenerator, IdGeneratorComponent}
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.userhistory.{UserHistoryService, UserHistoryServiceComponent}
import org.make.core.profile.Profile
import org.make.core.user.Role.RoleCitizen
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

class UserServiceTest
    extends MakeUnitTest
    with DefaultUserServiceComponent
    with IdGeneratorComponent
    with UserTokenGeneratorComponent
    with UserHistoryServiceComponent
    with PersistentUserServiceComponent
    with EventBusServiceComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val userTokenGenerator: UserTokenGenerator = mock[UserTokenGenerator]
  override val userHistoryService: UserHistoryService = mock[UserHistoryService]
  override val eventBusService: EventBusService = mock[EventBusService]

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
        postalCode = None,
        karmaLevel = None,
        locale = None
      )

      val returnedUser = User(
        userId = UserId("AAA-BBB-CCC"),
        email = "any@mail.com",
        firstName = Some("olive"),
        lastName = Some("tom"),
        lastIp = Some("127.0.0.1"),
        hashedPassword = Some("passpass"),
        enabled = true,
        verified = false,
        lastConnection = DateHelper.now(),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(DateHelper.now()),
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
        UserRegisterData(
          email = "any@mail.com",
          firstName = Some("tom"),
          lastName = Some("olive"),
          password = Some("passopasso"),
          lastIp = Some("127.0.0.1"),
          dateOfBirth = Some(LocalDate.parse("1984-10-11"))
        ),
        RequestContext.empty
      )

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
        UserRegisterData(
          email = "exist@mail.com",
          firstName = Some("tom"),
          lastName = Some("olive"),
          password = Some("passopasso"),
          lastIp = Some("127.0.0.1"),
          dateOfBirth = Some(LocalDate.parse("1984-10-11"))
        ),
        RequestContext.empty
      )

      whenReady(futureUser.failed, Timeout(3.seconds)) { exception =>
        exception shouldBe a[EmailAlreadyRegisteredException]
        exception.asInstanceOf[EmailAlreadyRegisteredException].email should be("exist@mail.com")
      }
    }
  }

}
