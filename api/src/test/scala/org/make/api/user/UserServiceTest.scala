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

import java.time.{LocalDate, ZonedDateTime}

import org.make.api.MakeUnitTest
import org.make.api.technical.auth._
import org.make.api.technical.{EventBusService, EventBusServiceComponent, IdGenerator, IdGeneratorComponent}
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user.UserUpdateEvent.{UserCreatedEvent, UserUpdatedEvent}
import org.make.api.user.social.models.UserInfo
import org.make.api.userhistory.UserEvent.UserValidatedAccountEvent
import org.make.api.userhistory.{UserHistoryCoordinatorService, UserHistoryCoordinatorServiceComponent}
import org.make.core.profile.Gender.Female
import org.make.core.profile.{Gender, Profile}
import org.make.core.user.Role.RoleCitizen
import org.make.core.user.{MailingErrorLog, Role, User, UserId}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify}
import org.mockito._
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class UserServiceTest
    extends MakeUnitTest
    with DefaultUserServiceComponent
    with IdGeneratorComponent
    with UserTokenGeneratorComponent
    with UserHistoryCoordinatorServiceComponent
    with PersistentUserServiceComponent
    with EventBusServiceComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val userTokenGenerator: UserTokenGenerator = mock[UserTokenGenerator]
  override val userHistoryCoordinatorService: UserHistoryCoordinatorService = mock[UserHistoryCoordinatorService]
  override val eventBusService: EventBusService = mock[EventBusService]

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.clearInvocations(eventBusService)
  }

  val zonedDateTimeInThePast: ZonedDateTime = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")
  val fooProfile = Profile(
    dateOfBirth = Some(LocalDate.parse("2000-01-01")),
    avatarUrl = Some("https://www.example.com"),
    profession = Some("profession"),
    phoneNumber = Some("010101"),
    twitterId = Some("@twitterid"),
    facebookId = Some("facebookid"),
    googleId = Some("googleId"),
    gender = Some(Gender.Male),
    genderName = Some("other"),
    postalCode = Some("93"),
    karmaLevel = Some(2),
    locale = Some("fr_FR")
  )
  val fooUser = User(
    userId = UserId("1"),
    email = "foo@example.com",
    firstName = Some("Foo"),
    lastName = Some("John"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    enabled = true,
    emailVerified = true,
    lastConnection = zonedDateTimeInThePast,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(zonedDateTimeInThePast),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleAdmin, Role.RoleCitizen),
    country = "FR",
    language = "fr",
    profile = Some(fooProfile),
    createdAt = Some(zonedDateTimeInThePast)
  )

  Mockito.when(userTokenGenerator.generateVerificationToken()).thenReturn(Future.successful(("TOKEN", "HASHED_TOKEN")))
  Mockito.when(userTokenGenerator.generateResetToken()).thenReturn(Future.successful(("TOKEN", "HASHED_TOKEN")))

  class MatchRegisterEvents(maybeUserId: Option[UserId]) extends ArgumentMatcher[AnyRef] {
    override def matches(argument: AnyRef): Boolean =
      argument match {
        case i: UserCreatedEvent if maybeUserId == i.userId                 => true
        case i: UserValidatedAccountEvent if maybeUserId.contains(i.userId) => true
        case _                                                              => false
      }
  }

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
        emailVerified = false,
        lastConnection = DateHelper.now(),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(DateHelper.now()),
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq(RoleCitizen),
        country = "FR",
        language = "fr",
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
          dateOfBirth = Some(LocalDate.parse("1984-10-11")),
          country = "FR",
          language = "fr"
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

    scenario("successful register user from social") {
      Mockito.reset(eventBusService)
      Mockito.when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(None))

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        lastName = Some("user"),
        country = "FR",
        language = "fr",
        gender = None,
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("facebook.com/picture")
      )

      val futureUser = userService.createOrUpdateUserFromSocial(info, Some("127.0.0.1"), RequestContext.empty)

      val returnedProfile = Profile(
        dateOfBirth = Some(LocalDate.parse("1984-10-11")),
        avatarUrl = Some("facebook.com/picture"),
        profession = None,
        phoneNumber = None,
        twitterId = None,
        facebookId = Some("444444"),
        googleId = None,
        gender = None,
        genderName = None,
        postalCode = None,
        karmaLevel = None,
        locale = None
      )

      val returnedUser = User(
        userId = UserId("AAA-BBB-CCC-DDD"),
        email = info.email.getOrElse(""),
        firstName = info.firstName,
        lastName = info.lastName,
        lastIp = Some("127.0.0.1"),
        hashedPassword = Some("passpass"),
        enabled = true,
        emailVerified = true,
        lastConnection = DateHelper.now(),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(DateHelper.now()),
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq(RoleCitizen),
        country = "FR",
        language = "fr",
        profile = Some(returnedProfile)
      )

      Mockito
        .when(
          persistentUserService
            .persist(any[User])
        )
        .thenReturn(Future.successful(returnedUser))

      whenReady(futureUser, Timeout(2.seconds)) { user =>
        user shouldBe a[User]
        user.email should be(info.email.getOrElse(""))
        user.firstName should be(info.firstName)
        user.lastName should be(info.lastName)
        user.profile.get.facebookId should be(info.facebookId)

        verify(eventBusService, times(2))
          .publish(
            ArgumentMatchers
              .argThat(new MatchRegisterEvents(Some(returnedUser.userId)))
          )

      }
    }

    scenario("successful register user from social with gender") {
      Mockito.when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(None))
      Mockito.reset(eventBusService)
      val infoWithGender = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        lastName = Some("user"),
        gender = Some("female"),
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("facebook.com/picture"),
        country = "FR",
        language = "fr"
      )

      val returnedProfileWithGender = Profile(
        dateOfBirth = Some(LocalDate.parse("1984-10-11")),
        avatarUrl = Some("facebook.com/picture"),
        profession = None,
        phoneNumber = None,
        twitterId = None,
        facebookId = Some("444444"),
        googleId = None,
        gender = Some(Female),
        genderName = Some("female"),
        postalCode = None,
        karmaLevel = None,
        locale = None
      )

      val returnedUserWithGender = User(
        userId = UserId("AAA-BBB-CCC-DDD-EEE"),
        email = infoWithGender.email.getOrElse(""),
        firstName = infoWithGender.firstName,
        lastName = infoWithGender.lastName,
        lastIp = Some("127.0.0.1"),
        hashedPassword = Some("passpass"),
        enabled = true,
        emailVerified = true,
        lastConnection = DateHelper.now(),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(DateHelper.now()),
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq(RoleCitizen),
        country = "FR",
        language = "fr",
        profile = Some(returnedProfileWithGender)
      )

      Mockito
        .when(
          persistentUserService
            .persist(any[User])
        )
        .thenReturn(Future.successful(returnedUserWithGender))

      val futureUserWithGender =
        userService.createOrUpdateUserFromSocial(infoWithGender, Some("127.0.0.1"), RequestContext.empty)

      whenReady(futureUserWithGender, Timeout(2.seconds)) { user =>
        user shouldBe a[User]
        user.email should be(infoWithGender.email.getOrElse(""))
        user.firstName should be(infoWithGender.firstName)
        user.lastName should be(infoWithGender.lastName)
        user.profile.get.facebookId should be(infoWithGender.facebookId)
        user.profile.get.gender should be(Some(Female))
        user.profile.get.genderName should be(Some("female"))
        verify(eventBusService, times(2))
          .publish(
            ArgumentMatchers
              .argThat(new MatchRegisterEvents(Some(returnedUserWithGender.userId)))
          )

      }
    }

    scenario("successful update user from social") {
      Mockito.reset(eventBusService)
      Mockito.reset(persistentUserService)

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        lastName = Some("user"),
        country = "FR",
        language = "fr",
        gender = None,
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("facebook.com/picture")
      )

      val returnedProfile = Profile(
        dateOfBirth = Some(LocalDate.parse("1984-10-11")),
        avatarUrl = Some("facebook.com/picture"),
        profession = None,
        phoneNumber = None,
        twitterId = None,
        facebookId = Some("444444"),
        googleId = None,
        gender = None,
        genderName = None,
        postalCode = None,
        karmaLevel = None,
        locale = None
      )

      val returnedUser = User(
        userId = UserId("AAA-BBB-CCC-DDD"),
        email = info.email.getOrElse(""),
        firstName = info.firstName,
        lastName = info.lastName,
        lastIp = Some("127.0.0.1"),
        hashedPassword = Some("passpass"),
        enabled = true,
        emailVerified = true,
        lastConnection = DateHelper.now(),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(DateHelper.now()),
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq(RoleCitizen),
        country = "FR",
        language = "fr",
        profile = Some(returnedProfile)
      )

      Mockito.when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(Some(returnedUser)))
      Mockito.when(persistentUserService.updateSocialUser(any[User])).thenReturn(Future.successful(true))
      val futureUser = userService.createOrUpdateUserFromSocial(info, Some("NEW 127.0.0.1"), RequestContext.empty)

      whenReady(futureUser, Timeout(2.seconds)) { user =>
        user shouldBe a[User]
        user.email should be(info.email.getOrElse(""))
        user.firstName should be(info.firstName)
        user.lastName should be(info.lastName)
        user.profile.get.facebookId should be(info.facebookId)

        verify(persistentUserService, times(1)).updateSocialUser(ArgumentMatchers.any[User])
        user.lastIp should be(Some("NEW 127.0.0.1"))

      }
    }

    scenario("successful update user without difference from social") {
      Mockito.reset(eventBusService)
      Mockito.reset(persistentUserService)

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        lastName = Some("user"),
        country = "FR",
        language = "fr",
        gender = None,
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("facebook.com/picture")
      )

      val returnedProfile = Profile(
        dateOfBirth = Some(LocalDate.parse("1984-10-11")),
        avatarUrl = Some("facebook.com/picture"),
        profession = None,
        phoneNumber = None,
        twitterId = None,
        facebookId = Some("444444"),
        googleId = None,
        gender = None,
        genderName = None,
        postalCode = None,
        karmaLevel = None,
        locale = None
      )

      val returnedUser = User(
        userId = UserId("AAA-BBB-CCC-DDD"),
        email = info.email.getOrElse(""),
        firstName = info.firstName,
        lastName = info.lastName,
        lastIp = Some("127.0.0.1"),
        hashedPassword = Some("passpass"),
        enabled = true,
        emailVerified = true,
        lastConnection = DateHelper.now(),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(DateHelper.now()),
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq(RoleCitizen),
        country = "FR",
        language = "fr",
        profile = Some(returnedProfile)
      )

      Mockito.when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(Some(returnedUser)))
      val futureUser = userService.createOrUpdateUserFromSocial(info, returnedUser.lastIp, RequestContext.empty)

      whenReady(futureUser, Timeout(2.seconds)) { user =>
        user shouldBe a[User]
        user.email should be(info.email.getOrElse(""))
        user.firstName should be(info.firstName)
        user.lastName should be(info.lastName)
        user.profile.get.facebookId should be(info.facebookId)

        verify(persistentUserService, times(0)).updateSocialUser(ArgumentMatchers.any[User])

      }
    }

    scenario("email already registred") {
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(true))
      Mockito.reset(eventBusService)
      val futureUser = userService.register(
        UserRegisterData(
          email = "exist@mail.com",
          firstName = Some("tom"),
          lastName = Some("olive"),
          password = Some("passopasso"),
          lastIp = Some("127.0.0.1"),
          dateOfBirth = Some(LocalDate.parse("1984-10-11")),
          country = "FR",
          language = "fr"
        ),
        RequestContext.empty
      )

      whenReady(futureUser.failed, Timeout(3.seconds)) { exception =>
        exception shouldBe a[EmailAlreadyRegisteredException]
        exception.asInstanceOf[EmailAlreadyRegisteredException].email should be("exist@mail.com")
      }

      val user = User(
        userId = UserId("AAA-BBB-CCC-DDD-EEE"),
        email = "existing-user@gmail.com",
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
        roles = Seq(),
        country = "FR",
        language = "fr",
        profile = None
      )

      Mockito.when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(Some(user)))

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        lastName = Some("user"),
        country = "FR",
        language = "fr",
        gender = None,
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("facebook.com/picture")
      )

      userService.createOrUpdateUserFromSocial(info, None, RequestContext.empty)

      verify(eventBusService, Mockito.never())
        .publish(
          ArgumentMatchers
            .argThat(new MatchRegisterEvents(Some(user.userId)))
        )

    }
  }

  feature("password recovery") {
    scenario("successfully reset password") {

      val userId = UserId("AAA-BBB-CCC-DDD-EEE")

      Mockito
        .when(persistentUserService.requestResetPassword(any[UserId], any[String], any[Option[ZonedDateTime]]))
        .thenReturn(Future.successful(true))

      val futureResetPassword = userService.requestPasswordReset(userId)

      whenReady(futureResetPassword, Timeout(3.seconds)) { result =>
        result shouldBe true
      }
    }
  }

  feature("update opt in newsletter") {
    scenario("update opt in newsletter using userId") {
      Given("a user")
      When("I update opt in newsletter using UserId")
      Then("opt in value is updated")
      Mockito
        .when(persistentUserService.updateOptInNewsletter(any[UserId], ArgumentMatchers.eq(true)))
        .thenReturn(Future.successful(true))

      val futureBoolean = userService.updateOptInNewsletter(UserId("update-opt-in-user"), optInNewsletter = true)

      whenReady(futureBoolean, Timeout(3.seconds)) { result =>
        result shouldBe true
      }
    }
    scenario("update opt in newsletter using email") {
      Given("a user")
      When("I update opt in newsletter using user email")
      Then("opt in value is updated")
      Mockito
        .when(
          persistentUserService
            .updateOptInNewsletter(ArgumentMatchers.eq("user@example.com"), ArgumentMatchers.eq(true))
        )
        .thenReturn(Future.successful(true))

      val futureBoolean = userService.updateOptInNewsletter("user@example.com", optInNewsletter = true)

      whenReady(futureBoolean, Timeout(3.seconds)) { result =>
        result shouldBe true
      }
    }
  }

  feature("update hard bounce") {
    scenario("update hard bounce using userId") {
      Given("a user")
      When("I update hard bounce using UserId")
      Then("hard bounce is updated")
      Mockito
        .when(persistentUserService.updateIsHardBounce(any[UserId], ArgumentMatchers.eq(true)))
        .thenReturn(Future.successful(true))

      val futureBoolean = userService.updateIsHardBounce(UserId("update-opt-in-user"), isHardBounce = true)

      whenReady(futureBoolean, Timeout(3.seconds)) { result =>
        result shouldBe true
      }
    }
    scenario("update hard bounce using email") {
      Given("a user")
      When("I update hard bounce using user email")
      Then("hard bounce is updated")
      Mockito
        .when(
          persistentUserService
            .updateIsHardBounce(ArgumentMatchers.eq("user@example.com"), ArgumentMatchers.eq(true))
        )
        .thenReturn(Future.successful(true))

      val futureBoolean = userService.updateIsHardBounce("user@example.com", isHardBounce = true)

      whenReady(futureBoolean, Timeout(3.seconds)) { result =>
        result shouldBe true
      }
    }
  }

  feature("update mailing error") {
    scenario("update mailing error using userId") {
      Given("a user")
      When("I update mailing error using UserId")
      Then("mailing error is updated")
      Mockito
        .when(persistentUserService.updateLastMailingError(any[UserId], any[Option[MailingErrorLog]]))
        .thenReturn(Future.successful(true))

      val futureBoolean = userService.updateLastMailingError(
        UserId("update-opt-in-user"),
        Some(MailingErrorLog(error = "my_error", date = ZonedDateTime.now()))
      )

      whenReady(futureBoolean, Timeout(3.seconds)) { result =>
        result shouldBe true
      }
    }
    scenario("update mailing error using email") {
      Given("a user")
      When("I update mailing error using user email")
      Then("mailing error is updated")
      Mockito
        .when(
          persistentUserService
            .updateLastMailingError(ArgumentMatchers.eq("user@example.com"), any[Option[MailingErrorLog]])
        )
        .thenReturn(Future.successful(true))

      val futureBoolean = userService.updateLastMailingError(
        "user@example.com",
        Some(MailingErrorLog(error = "my_error", date = ZonedDateTime.now()))
      )

      whenReady(futureBoolean, Timeout(3.seconds)) { result =>
        result shouldBe true
      }
    }
  }

  feature("update user") {
    scenario("update a user") {
      Given("a user")
      When("I update fields")
      Then("fields are updated into user and UserUpdatedEvent is published")

      Mockito
        .when(persistentUserService.updateUser(ArgumentMatchers.eq(fooUser)))
        .thenReturn(Future.successful(fooUser))

      val futureUser = userService.update(fooUser, RequestContext.empty)

      whenReady(futureUser, Timeout(3.seconds)) { result =>
        result shouldBe a[User]
        val captor: ArgumentCaptor[UserUpdatedEvent] = ArgumentCaptor
          .forClass(classOf[UserUpdatedEvent])
        verify(eventBusService, times(1))
          .publish(captor.capture())

        captor.getValue().userId should be(Some(result.userId))
      }
    }
  }

}
