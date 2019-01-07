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

import com.github.t3hnar.bcrypt._
import org.make.api.MakeUnitTest
import org.make.api.proposal.{ProposalService, ProposalServiceComponent, ProposalsResultSeededResponse}
import org.make.api.question.AuthorRequest
import org.make.api.technical.auth._
import org.make.api.technical.crm.{CrmService, CrmServiceComponent}
import org.make.api.technical.{EventBusService, EventBusServiceComponent, IdGenerator, IdGeneratorComponent}
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user.UserUpdateEvent._
import org.make.api.user.social.models.UserInfo
import org.make.api.userhistory.UserEvent.UserRegisteredEvent
import org.make.api.userhistory.{UserHistoryCoordinatorService, UserHistoryCoordinatorServiceComponent}
import org.make.core.profile.Gender.Female
import org.make.core.reference.{Country, Language}
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.proposal.SearchQuery
import org.make.core.question.QuestionId
import org.make.core.user.Role.RoleCitizen
import org.make.core.user.{MailingErrorLog, Role, User, UserId}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.{times, verify}
import org.mockito._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.mockito.ArgumentMatchers.{eq => isEqual}
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class UserServiceTest
    extends MakeUnitTest
    with DefaultUserServiceComponent
    with IdGeneratorComponent
    with UserTokenGeneratorComponent
    with UserHistoryCoordinatorServiceComponent
    with PersistentUserServiceComponent
    with ProposalServiceComponent
    with CrmServiceComponent
    with TokenGeneratorComponent
    with EventBusServiceComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val userTokenGenerator: UserTokenGenerator = mock[UserTokenGenerator]
  override val userHistoryCoordinatorService: UserHistoryCoordinatorService = mock[UserHistoryCoordinatorService]
  override val proposalService: ProposalService = mock[ProposalService]
  override val crmService: CrmService = mock[CrmService]
  override val eventBusService: EventBusService = mock[EventBusService]
  override val tokenGenerator: TokenGenerator = mock[TokenGenerator]

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
    description = None,
    twitterId = Some("@twitterid"),
    facebookId = Some("facebookid"),
    googleId = Some("googleId"),
    gender = Some(Gender.Male),
    genderName = Some("other"),
    postalCode = Some("93"),
    karmaLevel = Some(2),
    locale = Some("fr_FR"),
    socioProfessionalCategory = Some(SocioProfessionalCategory.Farmers)
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
    country = Country("FR"),
    language = Language("fr"),
    profile = Some(fooProfile),
    createdAt = Some(zonedDateTimeInThePast),
    availableQuestions = Seq.empty
  )

  Mockito.when(userTokenGenerator.generateVerificationToken()).thenReturn(Future.successful(("TOKEN", "HASHED_TOKEN")))
  Mockito.when(userTokenGenerator.generateResetToken()).thenReturn(Future.successful(("TOKEN", "HASHED_TOKEN")))

  val johnDoeProfile: Profile = Profile(
    dateOfBirth = Some(LocalDate.parse("1984-10-11")),
    avatarUrl = Some("facebook.com/picture"),
    profession = None,
    phoneNumber = None,
    description = None,
    twitterId = None,
    facebookId = Some("444444"),
    googleId = None,
    gender = None,
    genderName = None,
    postalCode = None,
    karmaLevel = None,
    locale = None,
    socioProfessionalCategory = None
  )

  val johnDoeUser: User = User(
    userId = UserId("AAA-BBB-CCC-DDD"),
    email = "johndoe@example.com",
    firstName = Some("john"),
    lastName = Some("doe"),
    lastIp = Some("127.0.0.1"),
    hashedPassword = Some("passpass".bcrypt),
    enabled = true,
    emailVerified = true,
    lastConnection = DateHelper.now(),
    verificationToken = Some("Token"),
    verificationTokenExpiresAt = Some(DateHelper.now()),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleCitizen),
    country = Country("FR"),
    language = Language("fr"),
    profile = Some(johnDoeProfile),
    availableQuestions = Seq.empty
  )

  feature("register user") {
    scenario("successful register user") {
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(false))

      val returnedProfile = Profile(
        dateOfBirth = Some(LocalDate.parse("1984-10-11")),
        avatarUrl = None,
        profession = None,
        phoneNumber = None,
        description = None,
        twitterId = None,
        facebookId = None,
        googleId = None,
        gender = Some(Gender.Male),
        genderName = Some(Gender.Male.toString),
        postalCode = None,
        karmaLevel = None,
        locale = None,
        socioProfessionalCategory = Some(SocioProfessionalCategory.Employee),
        optInPartner = Some(true),
        registerQuestionId = Some(QuestionId("thequestionid"))
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
        country = Country("FR"),
        language = Language("fr"),
        profile = Some(returnedProfile),
        availableQuestions = Seq.empty
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
          country = Country("FR"),
          language = Language("fr"),
          gender = Some(Gender.Male),
          socioProfessionalCategory = Some(SocioProfessionalCategory.Employee)
        ),
        RequestContext.empty
      )

      whenReady(futureUser, Timeout(2.seconds)) { user =>
        user shouldBe a[User]
        user.email should be("any@mail.com")
        user.firstName should be(Some("olive"))
        user.lastName should be(Some("tom"))
        user.profile.get.dateOfBirth should be(Some(LocalDate.parse("1984-10-11")))
        user.profile.get.gender should be(Some(Gender.Male))
        user.profile.get.genderName should be(Some(Gender.Male.toString))
        user.profile.get.socioProfessionalCategory should be(Some(SocioProfessionalCategory.Employee))
        user.profile.get.optInPartner should be(Some(true))
        user.profile.get.registerQuestionId should be(Some(QuestionId("thequestionid")))
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
        description = None,
        twitterId = None,
        facebookId = Some("444444"),
        googleId = None,
        gender = None,
        genderName = None,
        postalCode = None,
        karmaLevel = None,
        locale = None,
        socioProfessionalCategory = None
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
        country = Country("FR"),
        language = Language("fr"),
        profile = Some(returnedProfile),
        availableQuestions = Seq.empty
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
          .publish(ArgumentMatchers.argThat[AnyRef] {
            case event: UserRegisteredEvent => event.userId == returnedUser.userId
            case event: UserCreatedEvent    => event.userId.contains(returnedUser.userId)
            case _                          => false
          })

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
        description = None,
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
        country = Country("FR"),
        language = Language("fr"),
        profile = Some(returnedProfileWithGender),
        availableQuestions = Seq.empty
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
          .publish(ArgumentMatchers.argThat[AnyRef] {
            case event: UserRegisteredEvent => event.userId == returnedUserWithGender.userId
            case event: UserCreatedEvent    => event.userId.contains(returnedUserWithGender.userId)
            case _                          => false
          })
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
        description = None,
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
        country = Country("FR"),
        language = Language("fr"),
        profile = Some(returnedProfile),
        availableQuestions = Seq.empty
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
        description = None,
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
        country = Country("FR"),
        language = Language("fr"),
        profile = Some(returnedProfile),
        availableQuestions = Seq.empty
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
          country = Country("FR"),
          language = Language("fr")
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
        country = Country("FR"),
        language = Language("fr"),
        profile = None,
        availableQuestions = Seq.empty
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
        .publish(ArgumentMatchers.any[AnyRef])

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
      val userIdOptinNewsletter: UserId = UserId("update-opt-in-user")
      When("I update opt in newsletter using UserId")
      Then("opt in value is updated")
      Mockito
        .when(persistentUserService.updateOptInNewsletter(any[UserId], ArgumentMatchers.eq(true)))
        .thenReturn(Future.successful(true))

      val futureBoolean = userService.updateOptInNewsletter(userIdOptinNewsletter, optInNewsletter = true)

      whenReady(futureBoolean, Timeout(3.seconds)) { result =>
        result shouldBe true
      }
    }
    scenario("update opt in newsletter using email") {
      Given("a user")
      When("I update opt in newsletter using user email")
      Then("opt in value is updated")

      Mockito.reset(eventBusService)
      Mockito
        .when(
          persistentUserService
            .updateOptInNewsletter(ArgumentMatchers.eq("user@example.com"), ArgumentMatchers.eq(true))
        )
        .thenReturn(Future.successful(true))

      val futureBoolean = userService.updateOptInNewsletter("user@example.com", optInNewsletter = true)

      whenReady(futureBoolean, Timeout(3.seconds)) { result =>
        result shouldBe true

        Mockito
          .verify(eventBusService, Mockito.times(1))
          .publish(ArgumentMatchers.argThat[UserUpdatedOptInNewsletterEvent] { event =>
            event.email.contains("user@example.com")
          })
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
      Mockito
        .when(proposalService.searchForUser(any[Option[UserId]], any[SearchQuery], any[RequestContext]))
        .thenReturn(Future.successful(ProposalsResultSeededResponse(0, Seq.empty, None)))

      val futureUser = userService.update(fooUser, RequestContext.empty)

      whenReady(futureUser, Timeout(3.seconds)) { result =>
        result shouldBe a[User]
        val captor: ArgumentCaptor[UserUpdatedEvent] = ArgumentCaptor
          .forClass(classOf[UserUpdatedEvent])
        verify(eventBusService, times(1))
          .publish(captor.capture())

        captor.getValue.userId should be(Some(result.userId))
      }
    }
  }

  feature("change password without token") {
    scenario("update existing password") {
      val johnChangePassword: User =
        johnDoeUser.copy(userId = UserId("userchangepasswordid"), hashedPassword = Some("mypassword".bcrypt))
      val newPassword: String = "mypassword2"

      Mockito.reset(eventBusService)
      Mockito
        .when(
          persistentUserService.updatePassword(
            ArgumentMatchers.eq(johnChangePassword.userId),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.argThat[String] { pass =>
              newPassword.isBcrypted(pass)
            }
          )
        )
        .thenReturn(Future.successful(true))

      Given("a user with a password defined")
      When("I Update password ")
      val futureBoolean: Future[Boolean] = userService.updatePassword(johnChangePassword.userId, None, newPassword)

      Then("The update success")
      whenReady(futureBoolean, Timeout(3.seconds)) { result =>
        Mockito
          .verify(eventBusService, Mockito.times(1))
          .publish(ArgumentMatchers.argThat[UserUpdatedPasswordEvent] { event =>
            event.userId.contains(johnChangePassword.userId)
          })
        result shouldBe true
      }
    }
  }

  feature("anonymize user") {
    scenario("anonymize user") {
      Mockito
        .when(persistentUserService.updateUser(ArgumentMatchers.any[User]))
        .thenReturn(Future.successful(johnDoeUser))
      Mockito.when(proposalService.anonymizeByUserId(ArgumentMatchers.any[UserId])).thenReturn(Future.successful({}))
      Mockito
        .when(persistentUserService.removeAnonymizedUserFromFollowedUserTable(ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful({}))

      Given("a user")
      When("I anonymize this user")
      val futureAnonymizeUser = userService.anonymize(johnDoeUser)

      Then("an event is sent")
      whenReady(futureAnonymizeUser, Timeout(3.seconds)) { _ =>
        Mockito
          .verify(eventBusService, Mockito.times(1))
          .publish(ArgumentMatchers.argThat[UserAnonymizedEvent] { event =>
            event.userId.contains(johnDoeUser.userId)
          })
      }
    }
  }

  feature("follow user") {
    scenario("follow user") {
      Mockito.reset(eventBusService)
      Mockito
        .when(persistentUserService.followUser(any[UserId], any[UserId]))
        .thenReturn(Future.successful({}))

      val futureFollowOrganisation =
        userService.followUser(UserId("user-id"), UserId("me"))

      whenReady(futureFollowOrganisation, Timeout(2.seconds)) { _ =>
        verify(eventBusService, times(1)).publish(any[UserFollowEvent])
      }
    }
  }

  feature("unfollow user") {
    scenario("unfollow user") {
      Mockito.reset(eventBusService)
      Mockito
        .when(persistentUserService.unfollowUser(any[UserId], any[UserId]))
        .thenReturn(Future.successful({}))

      val futureFollowOrganisation =
        userService.unfollowUser(UserId("make-org"), UserId("me"))

      whenReady(futureFollowOrganisation, Timeout(2.seconds)) { _ =>
        verify(eventBusService, times(1)).publish(any[UserUnfollowEvent])
      }
    }
  }

  feature("Create or retrieve virtual user") {
    scenario("Existing user") {
      val request: AuthorRequest =
        AuthorRequest(
          age = Some(20),
          firstName = Some("who cares anyway"),
          lastName = None,
          postalCode = None,
          profession = None
        )

      Mockito.when(tokenGenerator.tokenToHash(isEqual(request.toString))).thenReturn("some-hash")
      val user = User(
        userId = UserId("existing-user-id"),
        email = "yopmail+some-hash@make.org",
        firstName = request.firstName,
        lastName = None,
        lastIp = None,
        hashedPassword = None,
        enabled = false,
        emailVerified = false,
        lastConnection = DateHelper.now(),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq.empty,
        country = Country("FR"),
        language = Language("fr"),
        profile = None,
        createdAt = None,
        updatedAt = None,
        availableQuestions = Seq.empty
      )
      Mockito
        .when(persistentUserService.findByEmail("yopmail+some-hash@make.org"))
        .thenReturn(Future.successful(Some(user)))

      val result = userService.retrieveOrCreateVirtualUser(request, Country("FR"), Language("fr"))

      whenReady(result, Timeout(5.seconds)) { resultUser =>
        resultUser should be(user)
      }

    }
    scenario("New user") {
      val request: AuthorRequest =
        AuthorRequest(
          age = Some(20),
          firstName = Some("who cares anyway"),
          lastName = None,
          postalCode = None,
          profession = None
        )

      Mockito.when(tokenGenerator.tokenToHash(isEqual(request.toString))).thenReturn("some-other-hash")

      Mockito
        .when(persistentUserService.findByEmail("yopmail+some-other-hash@make.org"))
        .thenReturn(Future.successful(None))

      Mockito
        .when(persistentUserService.persist(any[User]))
        .thenAnswer(invocation => Future.successful(invocation.getArgument[User](0)))

      Mockito
        .when(persistentUserService.emailExists(isEqual("yopmail+some-other-hash@make.org")))
        .thenReturn(Future.successful(false))

      val result = userService.retrieveOrCreateVirtualUser(request, Country("FR"), Language("fr"))

      whenReady(result, Timeout(5.seconds)) { resultUser =>
        resultUser.email should be("yopmail+some-other-hash@make.org")
      }
    }
  }

}
