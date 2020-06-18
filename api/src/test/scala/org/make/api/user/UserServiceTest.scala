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

import java.io.File
import java.text.SimpleDateFormat
import java.time.{LocalDate, ZonedDateTime}

import akka.http.scaladsl.model.{ContentType, MediaTypes}
import com.github.t3hnar.bcrypt._
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.proposal.{ProposalService, ProposalServiceComponent, ProposalsResultSeededResponse}
import org.make.api.question.AuthorRequest
import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.api.technical.auth._
import org.make.api.technical.crm.{CrmService, CrmServiceComponent}
import org.make.api.technical.storage.Content.FileContent
import org.make.api.technical.storage.{StorageService, StorageServiceComponent}
import org.make.api.technical._
import org.make.api.user.UserExceptions.{EmailAlreadyRegisteredException, EmailNotAllowed}
import org.make.api.user.social.models.UserInfo
import org.make.api.user.validation.{UserRegistrationValidator, UserRegistrationValidatorComponent}
import org.make.api.userhistory.{UserHistoryCoordinatorService, UserHistoryCoordinatorServiceComponent, _}
import org.make.api.{MakeUnitTest, TestUtils}
import org.make.core.auth.UserRights
import org.make.core.profile.Gender.Female
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.proposal.SearchQuery
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.technical.IdGenerator
import org.make.core.user._
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers.{eq => isEqual, _}
import org.mockito.Mockito.{times, verify}
import org.mockito._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}

class UserServiceTest
    extends MakeUnitTest
    with DefaultUserServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with UserTokenGeneratorComponent
    with UserHistoryCoordinatorServiceComponent
    with PersistentUserServiceComponent
    with PersistentUserToAnonymizeServiceComponent
    with ProposalServiceComponent
    with CrmServiceComponent
    with TokenGeneratorComponent
    with EventBusServiceComponent
    with MakeSettingsComponent
    with UserRegistrationValidatorComponent
    with StorageServiceComponent
    with DownloadServiceComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val userTokenGenerator: UserTokenGenerator = mock[UserTokenGenerator]
  override val userHistoryCoordinatorService: UserHistoryCoordinatorService = mock[UserHistoryCoordinatorService]
  override val proposalService: ProposalService = mock[ProposalService]
  override val crmService: CrmService = mock[CrmService]
  override val persistentUserToAnonymizeService: PersistentUserToAnonymizeService =
    mock[PersistentUserToAnonymizeService]
  override val eventBusService: EventBusService = mock[EventBusService]
  override val tokenGenerator: TokenGenerator = mock[TokenGenerator]
  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val userRegistrationValidator: UserRegistrationValidator = mock[UserRegistrationValidator]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val storageService: StorageService = mock[StorageService]
  override val downloadService: DownloadService = mock[DownloadService]

  Mockito.when(makeSettings.defaultUserAnonymousParticipation).thenReturn(false)
  Mockito.when(makeSettings.validationTokenExpiresIn).thenReturn(Duration("30 days"))
  Mockito.when(makeSettings.resetTokenExpiresIn).thenReturn(Duration("1 days"))
  Mockito.when(makeSettings.resetTokenB2BExpiresIn).thenReturn(Duration("3 days"))

  Mockito
    .when(persistentUserService.updateUser(any[User]))
    .thenAnswer(invocation => Future.successful(invocation.getArgument[User](0)))

  Mockito
    .when(persistentUserService.persist(any[User]))
    .thenAnswer(invocation => Future.successful(invocation.getArgument[User](0)))

  Mockito
    .when(userTokenGenerator.generateVerificationToken())
    .thenReturn(Future.successful(("TOKEN", "HASHED_TOKEN")))

  Mockito.when(userRegistrationValidator.canRegister(any[UserRegisterData])).thenReturn(Future.successful(true))

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.clearInvocations(eventBusService)
  }

  val zonedDateTimeInThePast: ZonedDateTime = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")
  val fooProfile: Profile = Profile(
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
    socioProfessionalCategory = Some(SocioProfessionalCategory.Farmers),
    website = Some("http://example.com"),
    politicalParty = Some("PP")
  )
  val fooUser: User = TestUtils.user(
    id = UserId("1"),
    email = "foo@example.com",
    firstName = Some("Foo"),
    lastName = Some("John"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    lastConnection = zonedDateTimeInThePast,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(zonedDateTimeInThePast),
    roles = Seq(Role.RoleAdmin, Role.RoleCitizen),
    profile = Some(fooProfile),
    createdAt = Some(zonedDateTimeInThePast)
  )

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
    socioProfessionalCategory = None,
    website = None,
    politicalParty = None
  )

  val johnDoeUser: User = TestUtils.user(
    id = UserId("AAA-BBB-CCC-DDD"),
    email = "johndoe@example.com",
    firstName = Some("john"),
    lastName = Some("doe"),
    lastIp = Some("127.0.0.1"),
    hashedPassword = Some("passpass".bcrypt),
    lastConnection = DateHelper.now(),
    verificationToken = Some("Token"),
    verificationTokenExpiresAt = Some(DateHelper.now()),
    profile = Some(johnDoeProfile)
  )

  val returnedPersonality: User = TestUtils.user(
    id = UserId("AAA-BBB-CCC"),
    firstName = Some("perso"),
    lastName = Some("nality"),
    email = "personality@mail.com",
    hashedPassword = Some("passpass"),
    userType = UserType.UserTypePersonality
  )

  feature("Get personality") {
    scenario("get personality") {
      Mockito
        .when(persistentUserService.findByUserIdAndUserType(any[UserId], any[UserType]))
        .thenReturn(Future.successful(Some(returnedPersonality)))

      whenReady(userService.getPersonality(UserId("AAA-BBB-CCC")), Timeout(2.seconds)) { user =>
        user.isDefined shouldBe true
      }
    }

    scenario("trying to get wrong personality") {
      Mockito
        .when(persistentUserService.findByUserIdAndUserType(any[UserId], any[UserType]))
        .thenReturn(Future.successful(None))

      whenReady(userService.getPersonality(UserId("AAA-BBB-CCC-DDD")), Timeout(2.seconds)) { user =>
        user.isDefined shouldBe false
      }
    }
  }

  feature("register user") {
    scenario("successful register user") {
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(false))

      Mockito.clearInvocations(persistentUserService)

      val futureUser = userService.register(
        UserRegisterData(
          email = "any@mail.com",
          firstName = Some("olive"),
          lastName = Some("tom"),
          password = Some("passopasso"),
          lastIp = Some("127.0.0.1"),
          dateOfBirth = Some(LocalDate.parse("1984-10-11")),
          country = Country("FR"),
          language = Language("fr"),
          gender = Some(Gender.Male),
          socioProfessionalCategory = Some(SocioProfessionalCategory.Employee),
          questionId = Some(QuestionId("thequestionid"))
        ),
        RequestContext.empty
      )

      whenReady(futureUser, Timeout(2.seconds)) { user =>
        user.email should be("any@mail.com")
        user.firstName should contain("olive")
        user.lastName should contain("tom")
        user.profile.flatMap(_.dateOfBirth) should contain(LocalDate.parse("1984-10-11"))
        user.profile.flatMap(_.gender) should contain(Gender.Male)
        user.profile.flatMap(_.socioProfessionalCategory) should be(Some(SocioProfessionalCategory.Employee))
        user.profile.flatMap(_.optInPartner) should be(None)
        user.profile.flatMap(_.registerQuestionId) should contain(QuestionId("thequestionid"))

        verify(persistentUserService).persist(ArgumentMatchers.argThat[User](!_.anonymousParticipation))
      }
    }

    scenario("successful register user from social") {
      Mockito.clearInvocations(eventBusService)
      Mockito.when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(None))

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
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
        locale = None,
        socioProfessionalCategory = None,
        registerQuestionId = Some(QuestionId("question")),
        website = None,
        politicalParty = None
      )

      val returnedUser = TestUtils.user(
        id = UserId("AAA-BBB-CCC-DDD"),
        email = info.email.getOrElse(""),
        firstName = info.firstName,
        lastIp = Some("127.0.0.1"),
        hashedPassword = Some("passpass"),
        lastConnection = DateHelper.now(),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(DateHelper.now()),
        profile = Some(returnedProfile)
      )

      Mockito
        .when(persistentUserService.persist(any[User]))
        .thenReturn(Future.successful(returnedUser))

      Mockito.clearInvocations(persistentUserService)

      val futureUser = userService.createOrUpdateUserFromSocial(
        info,
        Some("127.0.0.1"),
        Some(QuestionId("question")),
        RequestContext.empty
      )

      whenReady(futureUser, Timeout(2.seconds)) {
        case (user, accountCreation) =>
          user shouldBe a[User]
          user.email should be(info.email.getOrElse(""))
          user.firstName should be(info.firstName)
          user.profile.get.facebookId should be(info.facebookId)
          user.profile.flatMap(_.registerQuestionId) should be(Some(QuestionId("question")))
          accountCreation should be(true)

          verify(eventBusService, times(1))
            .publish(ArgumentMatchers.argThat[AnyRef] {
              case event: UserRegisteredEvent => event.userId == returnedUser.userId
              case _                          => false
            })

          verify(persistentUserService).persist(ArgumentMatchers.argThat[User](!_.anonymousParticipation))
      }
    }

    scenario("successful register user from social with gender") {
      Mockito.when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(None))
      Mockito.clearInvocations(eventBusService)
      val infoWithGender = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
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
        locale = None,
        website = None,
        politicalParty = None
      )

      val returnedUserWithGender = TestUtils.user(
        id = UserId("AAA-BBB-CCC-DDD-EEE"),
        email = infoWithGender.email.getOrElse(""),
        firstName = infoWithGender.firstName,
        lastIp = Some("127.0.0.1"),
        hashedPassword = Some("passpass"),
        lastConnection = DateHelper.now(),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(DateHelper.now()),
        profile = Some(returnedProfileWithGender)
      )

      Mockito
        .when(persistentUserService.persist(any[User]))
        .thenReturn(Future.successful(returnedUserWithGender))

      val futureUserWithGender =
        userService.createOrUpdateUserFromSocial(infoWithGender, Some("127.0.0.1"), None, RequestContext.empty)

      whenReady(futureUserWithGender, Timeout(2.seconds)) {
        case (user, accountCreation) =>
          user shouldBe a[User]
          user.email should be(infoWithGender.email.getOrElse(""))
          user.firstName should be(infoWithGender.firstName)
          user.profile.get.facebookId should be(infoWithGender.facebookId)
          user.profile.get.gender should be(Some(Female))
          user.profile.get.genderName should be(Some("female"))
          accountCreation should be(true)

          verify(eventBusService, times(1))
            .publish(ArgumentMatchers.argThat[AnyRef] {
              case event: UserRegisteredEvent => event.userId == returnedUserWithGender.userId
              case _                          => false
            })
      }
    }

    scenario("successful update user from social") {
      Mockito.clearInvocations(eventBusService)
      Mockito.clearInvocations(persistentUserService)

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
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
        locale = None,
        website = None,
        politicalParty = None
      )

      val returnedUser = TestUtils.user(
        id = UserId("AAA-BBB-CCC-DDD"),
        email = info.email.getOrElse(""),
        firstName = info.firstName,
        lastIp = Some("127.0.0.1"),
        hashedPassword = Some("passpass"),
        lastConnection = DateHelper.now(),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(DateHelper.now()),
        profile = Some(returnedProfile)
      )

      Mockito.when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(Some(returnedUser)))
      Mockito.when(persistentUserService.updateSocialUser(any[User])).thenReturn(Future.successful(true))
      val futureUser = userService.createOrUpdateUserFromSocial(info, Some("NEW 127.0.0.1"), None, RequestContext.empty)

      whenReady(futureUser, Timeout(2.seconds)) {
        case (user, accountCreation) =>
          user shouldBe a[User]
          user.email should be(info.email.getOrElse(""))
          user.firstName should be(info.firstName)
          user.profile.get.facebookId should be(info.facebookId)
          accountCreation should be(false)

          verify(persistentUserService, times(1)).updateSocialUser(ArgumentMatchers.any[User])
          user.lastIp should be(Some("NEW 127.0.0.1"))

      }
    }

    scenario("successful update user without difference from social") {
      Mockito.clearInvocations(eventBusService)
      Mockito.clearInvocations(persistentUserService)

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
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
        locale = None,
        website = None,
        politicalParty = None
      )

      val returnedUser = TestUtils.user(
        id = UserId("AAA-BBB-CCC-DDD"),
        email = info.email.getOrElse(""),
        firstName = info.firstName,
        lastIp = Some("127.0.0.1"),
        hashedPassword = Some("passpass"),
        lastConnection = DateHelper.now(),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(DateHelper.now()),
        profile = Some(returnedProfile)
      )

      Mockito.when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(Some(returnedUser)))
      Mockito.when(persistentUserService.updateSocialUser(any[User])).thenReturn(Future.successful(true))

      val futureUser = userService.createOrUpdateUserFromSocial(info, returnedUser.lastIp, None, RequestContext.empty)

      whenReady(futureUser, Timeout(2.seconds)) {
        case (user, accountCreation) =>
          user shouldBe a[User]
          user.email should be(info.email.getOrElse(""))
          user.firstName should be(info.firstName)
          user.profile.get.facebookId should be(info.facebookId)
          accountCreation should be(false)
      }
    }

    scenario("email already registred") {
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(true))
      Mockito.clearInvocations(eventBusService)
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

      val user = TestUtils.user(
        id = UserId("AAA-BBB-CCC-DDD-EEE"),
        email = "existing-user@gmail.com",
        firstName = None,
        lastName = None,
        hashedPassword = Some("hashedPassword")
      )

      Mockito.when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(Some(user)))
      Mockito.when(persistentUserService.updateSocialUser(any[User])).thenReturn(Future.successful(true))

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        country = "FR",
        language = "fr",
        gender = None,
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("facebook.com/picture")
      )

      whenReady(userService.createOrUpdateUserFromSocial(info, None, None, RequestContext.empty), Timeout(3.seconds)) {
        case (user, accountCreation) =>
          user.emailVerified shouldBe true
          user.hashedPassword shouldBe Some("hashedPassword")
          accountCreation should be(false)
      }

      verify(eventBusService, Mockito.never())
        .publish(ArgumentMatchers.any[AnyRef])

    }

    scenario("email not verified already registred") {
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(true))
      Mockito.clearInvocations(eventBusService)

      val user = TestUtils.user(
        id = UserId("AAA-BBB-CCC-DDD-EEE"),
        email = "existing-user@gmail.com",
        firstName = None,
        lastName = None,
        hashedPassword = Some("hashedPassword"),
        emailVerified = false
      )

      Mockito.when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(Some(user)))
      Mockito.when(persistentUserService.updateSocialUser(any[User])).thenReturn(Future.successful(true))

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        country = "FR",
        language = "fr",
        gender = None,
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("facebook.com/picture")
      )

      whenReady(userService.createOrUpdateUserFromSocial(info, None, None, RequestContext.empty), Timeout(3.seconds)) {
        case (user, accountCreation) =>
          user.emailVerified shouldBe true
          user.hashedPassword shouldBe None
          accountCreation should be(false)
      }

      verify(eventBusService, Mockito.never())
        .publish(ArgumentMatchers.any[AnyRef])

    }

    scenario("email not allowed") {
      Mockito
        .when(persistentUserService.emailExists("notvalidated@make.org"))
        .thenReturn(Future.successful(false))

      Mockito
        .when(userRegistrationValidator.canRegister(argThat[UserRegisterData](_.email == "notvalidated@make.org")))
        .thenReturn(Future.successful(false))

      val register = userService.register(
        UserRegisterData(
          email = "notvalidated@make.org",
          firstName = None,
          password = None,
          lastIp = None,
          country = Country("FR"),
          language = Language("fr")
        ),
        RequestContext.empty
      )

      whenReady(register.failed, Timeout(2.seconds)) {
        case EmailNotAllowed(_) =>
        case other              => fail(s"${other.getClass.getName} is not the expected EmailNotAllowed exception")
      }
    }
  }

  feature("register personality") {
    scenario("successful register personality") {
      Mockito.clearInvocations(persistentUserService)

      Mockito.when(userTokenGenerator.generateResetToken()).thenReturn(Future.successful(("TOKEN", "HASHED_TOKEN")))

      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(false))

      Mockito.when(persistentUserService.persist(any[User])).thenReturn(Future.successful(returnedPersonality))

      val futurePersonality = userService.registerPersonality(
        PersonalityRegisterData(
          email = "personality@mail.com",
          firstName = Some("perso"),
          lastName = Some("nality"),
          country = Country("FR"),
          language = Language("fr"),
          gender = Some(Gender.Male),
          genderName = None,
          description = None,
          avatarUrl = None,
          website = None,
          politicalParty = None
        ),
        RequestContext.empty
      )

      whenReady(futurePersonality, Timeout(2.seconds)) { user =>
        user.email should be("personality@mail.com")
        user.firstName should contain("perso")
        user.lastName should contain("nality")
      }

      verify(eventBusService, times(1))
        .publish(ArgumentMatchers.argThat[AnyRef] {
          case event: PersonalityRegisteredEvent => event.userId == returnedPersonality.userId
          case _                                 => false
        })
    }
  }

  feature("password recovery") {
    scenario("successfully reset password") {

      Mockito
        .when(persistentUserService.requestResetPassword(any[UserId], any[String], any[Option[ZonedDateTime]]))
        .thenReturn(Future.successful(true))

      Mockito.when(userTokenGenerator.generateResetToken()).thenReturn(Future.successful(("TOKEN", "HASHED_TOKEN")))

      val userId = UserId("user-reset-password-successfully")

      whenReady(userService.requestPasswordReset(userId), Timeout(3.seconds)) { result =>
        result shouldBe true
      }
    }
  }

  feature("validate email") {
    scenario("Send access token when validate email") {
      val expireInSeconds = 123000
      val refreshTokenValue = "my_refresh_token"
      val accessTokenValue = "my_access_token"

      val accessToken = AccessToken(
        accessTokenValue,
        Some(refreshTokenValue),
        None,
        Some(expireInSeconds),
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2018-07-13 12:00:00"),
        Map.empty
      )

      Mockito
        .when(oauth2DataHandler.createAccessToken(any[AuthInfo[UserRights]]))
        .thenReturn(Future.successful(accessToken))

      Mockito.when(persistentUserService.validateEmail(any[String])).thenReturn(Future.successful(true))

      whenReady(userService.validateEmail(fooUser, "verificationToken"), Timeout(2.seconds)) { tokenResponse =>
        tokenResponse should be(a[TokenResponse])
        tokenResponse.access_token should be(accessTokenValue)
        tokenResponse.refresh_token should be(refreshTokenValue)
        tokenResponse.token_type should be("Bearer")
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

      Mockito.clearInvocations(eventBusService)
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
          .publish(ArgumentMatchers.argThat[UserUpdatedOptInNewsletterEvent](_.optInNewsletter))
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
        Some(MailingErrorLog(error = "my_error", date = DateHelper.now()))
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
        Some(MailingErrorLog(error = "my_error", date = DateHelper.now()))
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
      Then("fields are updated into user")

      Mockito
        .when(proposalService.searchForUser(any[Option[UserId]], any[SearchQuery], any[RequestContext]))
        .thenReturn(Future.successful(ProposalsResultSeededResponse(0, Seq.empty, None)))

      val futureUser = userService.update(fooUser, RequestContext.empty)

      whenReady(futureUser, Timeout(3.seconds)) { result =>
        result shouldBe a[User]
      }
    }
  }

  feature("update personality user") {
    scenario("update a personality") {
      Given("a personality")
      When("I update fields")
      Then("fields are updated into user")

      val futureUser = userService.updatePersonality(
        personality = fooUser.copy(email = "fooUpdated@example.com"),
        moderatorId = Some(UserId("moderaor-id")),
        oldEmail = fooUser.email,
        requestContext = RequestContext.empty
      )

      whenReady(futureUser, Timeout(3.seconds)) { user =>
        user.email should be("fooUpdated@example.com")
      }
      verify(eventBusService, times(1))
        .publish(ArgumentMatchers.any(classOf[PersonalityEmailChangedEvent]))
    }
  }

  feature("change password without token") {
    scenario("update existing password") {
      val johnChangePassword: User =
        johnDoeUser.copy(userId = UserId("userchangepasswordid"), hashedPassword = Some("mypassword".bcrypt))
      val newPassword: String = "mypassword2"

      Mockito.clearInvocations(eventBusService)
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
        result shouldBe true
      }
    }
  }

  feature("anonymize user") {
    Mockito.when(persistentUserToAnonymizeService.create(any[String])).thenReturn(Future.successful({}))
    scenario("anonymize user") {
      Mockito.when(proposalService.anonymizeByUserId(ArgumentMatchers.any[UserId])).thenReturn(Future.successful({}))
      Mockito
        .when(persistentUserService.removeAnonymizedUserFromFollowedUserTable(ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful({}))

      Given("a user")
      When("I anonymize this user")
      val adminId = UserId("admin")
      val futureAnonymizeUser = userService.anonymize(johnDoeUser, adminId, RequestContext.empty)

      Then("an event is sent")
      whenReady(futureAnonymizeUser, Timeout(3.seconds)) { _ =>
        Mockito
          .verify(eventBusService, Mockito.times(1))
          .publish(ArgumentMatchers.argThat[UserAnonymizedEvent] { event =>
            event.userId == johnDoeUser.userId && event.adminId == adminId
          })
      }
    }
  }

  feature("follow user") {
    scenario("follow user") {
      Mockito.clearInvocations(eventBusService)
      Mockito
        .when(persistentUserService.followUser(any[UserId], any[UserId]))
        .thenReturn(Future.successful({}))

      val futureFollowOrganisation =
        userService.followUser(UserId("user-id"), UserId("me"), RequestContext.empty)

      whenReady(futureFollowOrganisation, Timeout(2.seconds)) { _ =>
        verify(eventBusService, times(1)).publish(any[UserFollowEvent])
      }
    }
  }

  feature("unfollow user") {
    scenario("unfollow user") {
      Mockito.clearInvocations(eventBusService)
      Mockito
        .when(persistentUserService.unfollowUser(any[UserId], any[UserId]))
        .thenReturn(Future.successful({}))

      val futureFollowOrganisation =
        userService.unfollowUser(UserId("make-org"), UserId("me"), RequestContext.empty)

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
          firstName = "who cares anyway",
          lastName = None,
          postalCode = None,
          profession = None
        )

      Mockito.when(tokenGenerator.tokenToHash(isEqual(request.toString))).thenReturn("some-hash")
      val user = TestUtils.user(
        id = UserId("existing-user-id"),
        email = "yopmail+some-hash@make.org",
        firstName = Some(request.firstName),
        lastName = None,
        enabled = false,
        emailVerified = false
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
          firstName = "who cares anyway",
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

  feature("get reconnect info") {
    scenario("reconnect info") {

      Mockito.when(persistentUserService.get(any[UserId])).thenReturn(Future.successful(Some(fooUser)))
      Mockito.when(userTokenGenerator.generateReconnectToken()).thenReturn(Future.successful(("token", "hashedToken")))
      Mockito
        .when(persistentUserService.updateReconnectToken(any[UserId], any[String], any[ZonedDateTime]))
        .thenReturn(Future.successful(true))

      val result = userService.reconnectInfo(UserId("userId"))
      whenReady(result, Timeout(5.seconds)) { resultReconnect =>
        resultReconnect.foreach { reconnect =>
          reconnect.reconnectToken should be("hashedToken")
          reconnect.firstName should be(fooUser.firstName)
          reconnect.avatarUrl should be(fooUser.profile.flatMap(_.avatarUrl))
          reconnect.hiddenMail should be("f*o@example.com")
          reconnect.connectionMode should contain(ConnectionMode.Mail)
          reconnect.connectionMode should contain(ConnectionMode.Facebook)
          reconnect.connectionMode should contain(ConnectionMode.Google)
        }
      }
    }
  }

  feature("changeEmailVerificationTokenIfNeeded") {
    scenario("verification token changed more than 10 minutes ago") {
      Mockito.clearInvocations(persistentUserService)

      Mockito
        .when(persistentUserService.get(UserId("old-verification-token")))
        .thenReturn(
          Future.successful(
            Some(
              fooUser.copy(
                emailVerified = false,
                verificationTokenExpiresAt = Some(DateHelper.now().plusDays(30).minusMinutes(11))
              )
            )
          )
        )

      whenReady(userService.changeEmailVerificationTokenIfNeeded(UserId("old-verification-token")), Timeout(2.seconds)) {
        maybeToken =>
          maybeToken should be(defined)
          verify(persistentUserService).updateUser(any[User])
      }
    }
    scenario("no verification token expiration") {
      Mockito.clearInvocations(persistentUserService)

      Mockito
        .when(persistentUserService.get(UserId("no-verification-token")))
        .thenReturn(Future.successful(Some(fooUser.copy(emailVerified = false, verificationTokenExpiresAt = None))))

      whenReady(userService.changeEmailVerificationTokenIfNeeded(UserId("no-verification-token")), Timeout(2.seconds)) {
        maybeToken =>
          maybeToken should be(defined)
          verify(persistentUserService).updateUser(any[User])
      }
    }
    scenario("no verification token expiration but email verified") {
      Mockito.clearInvocations(persistentUserService)

      Mockito
        .when(persistentUserService.get(UserId("no-verification-token-but-verified")))
        .thenReturn(Future.successful(Some(fooUser.copy(verificationTokenExpiresAt = None, emailVerified = true))))

      whenReady(
        userService.changeEmailVerificationTokenIfNeeded(UserId("no-verification-token-but-verified")),
        Timeout(2.seconds)
      ) { maybeToken =>
        maybeToken should be(None)
        verify(persistentUserService, Mockito.never()).updateUser(any[User])
      }
    }
    scenario("verification token changed less than 10 minutes ago") {
      Mockito.clearInvocations(persistentUserService)

      Mockito
        .when(persistentUserService.get(UserId("young-verification-token")))
        .thenReturn(
          Future.successful(
            Some(fooUser.copy(emailVerified = false, verificationTokenExpiresAt = Some(DateHelper.now().plusDays(30))))
          )
        )

      whenReady(
        userService.changeEmailVerificationTokenIfNeeded(UserId("young-verification-token")),
        Timeout(2.seconds)
      ) { maybeToken =>
        maybeToken should be(None)
        verify(persistentUserService, Mockito.never()).updateUser(any[User])
      }
    }
  }

  feature("upload avatar") {
    val nowDate = DateHelper.now()
    scenario("image url") {
      val imageUrl = "https://i.picsum.photos/id/352/200/200.jpg"
      val file = File.createTempFile("tmp", ".jpeg")
      file.deleteOnExit()
      Mockito
        .when(
          downloadService.downloadImage(
            ArgumentMatchers.eq(imageUrl),
            ArgumentMatchers.any[ContentType => File](),
            ArgumentMatchers.any[Int]
          )
        )
        .thenReturn(Future.successful((ContentType(MediaTypes.`image/jpeg`), file)))
      Mockito
        .when(
          storageService.uploadUserAvatar(
            ArgumentMatchers.eq(UserId("upload-avatar-image-url")),
            ArgumentMatchers.eq("jpeg"),
            ArgumentMatchers.eq(MediaTypes.`image/jpeg`.value),
            ArgumentMatchers.any[FileContent]
          )
        )
        .thenReturn(Future.successful("path/to/upload-avatar-image-url/image"))
      Mockito
        .when(persistentUserService.get(UserId("upload-avatar-image-url")))
        .thenReturn(Future.successful(Some(fooUser.copy(userId = UserId("upload-avatar-image-url")))))
      Mockito
        .when(
          proposalService.searchForUser(
            ArgumentMatchers.eq(Some(UserId("upload-avatar-image-url"))),
            ArgumentMatchers.any[SearchQuery],
            ArgumentMatchers.eq(RequestContext.empty)
          )
        )
        .thenReturn(Future.successful(ProposalsResultSeededResponse(0, Seq.empty, None)))

      whenReady(
        userService.changeAvatarForUser(UserId("upload-avatar-image-url"), imageUrl, RequestContext.empty, nowDate),
        Timeout(2.seconds)
      ) { _ =>
        Mockito
          .verify(persistentUserService)
          .updateUser(
            argThat[User](user => user.profile.flatMap(_.avatarUrl).contains("path/to/upload-avatar-image-url/image"))
          )
        Mockito
          .verify(userHistoryCoordinatorService)
          .logHistory(argThat[LogUserUploadedAvatarEvent] { event =>
            event.userId.value == "upload-avatar-image-url"
          })
      }
    }

    scenario("image url unavailable") {
      val imageUrl = "https://i.picsum.photos/id/352/200/200.jpg"
      val file = File.createTempFile("tmp", ".jpeg")
      file.deleteOnExit()
      Mockito
        .when(
          downloadService.downloadImage(
            ArgumentMatchers.eq(imageUrl),
            ArgumentMatchers.any[ContentType => File](),
            ArgumentMatchers.any[Int]
          )
        )
        .thenReturn(Future.failed(ImageUnavailable("path/url")))
      Mockito
        .when(persistentUserService.get(UserId("upload-avatar-image-url-unavailable")))
        .thenReturn(Future.successful(Some(fooUser.copy(userId = UserId("upload-avatar-image-url-unavailable")))))
      Mockito
        .when(
          proposalService.searchForUser(
            ArgumentMatchers.eq(Some(UserId("upload-avatar-image-url-unavailable"))),
            ArgumentMatchers.any[SearchQuery],
            ArgumentMatchers.eq(RequestContext.empty)
          )
        )
        .thenReturn(Future.successful(ProposalsResultSeededResponse(0, Seq.empty, None)))

      whenReady(
        userService
          .changeAvatarForUser(UserId("upload-avatar-image-url-unavailable"), imageUrl, RequestContext.empty, nowDate),
        Timeout(2.seconds)
      ) { _ =>
        Mockito
          .verify(persistentUserService)
          .updateUser(argThat[User](user => user.profile.flatMap(_.avatarUrl).isEmpty))
        Mockito
          .verify(userHistoryCoordinatorService)
          .logHistory(argThat[LogUserUploadedAvatarEvent] { event =>
            event.userId.value == "upload-avatar-image-url-unavailable"
          })
      }
    }

    scenario("download failed") {
      val imageUrl = "https://www.google.fr"
      val file = File.createTempFile("tmp", ".pdf")
      file.deleteOnExit()
      Mockito
        .when(
          downloadService.downloadImage(
            ArgumentMatchers.eq(imageUrl),
            ArgumentMatchers.any[ContentType => File](),
            ArgumentMatchers.any[Int]
          )
        )
        .thenReturn(Future.failed(new IllegalArgumentException(s"URL does not refer to an image: $imageUrl")))

      whenReady(
        userService.changeAvatarForUser(UserId("user-id"), imageUrl, RequestContext.empty, nowDate).failed,
        Timeout(2.seconds)
      ) { exception =>
        exception shouldBe a[IllegalArgumentException]
      }
    }
  }

  feature("update user email") {
    scenario("it works") {
      val user = fooUser.copy(email = "foo+ineedauniqueemail@example.com")
      Mockito
        .when(persistentUserToAnonymizeService.create("foo+ineedauniqueemail@example.com"))
        .thenReturn(Future.successful({}))
      whenReady(userService.adminUpdateUserEmail(user, "bar@example.com"), Timeout(2.seconds)) { _ =>
        Mockito.verify(persistentUserService).updateUser(user.copy(email = "bar@example.com"))
        Mockito.verify(persistentUserToAnonymizeService).create("foo+ineedauniqueemail@example.com")
      }
    }
  }
}
