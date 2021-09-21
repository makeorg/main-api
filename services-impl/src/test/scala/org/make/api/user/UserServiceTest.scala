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

import akka.http.scaladsl.model.{ContentType, MediaTypes}
import com.github.t3hnar.bcrypt._
import org.make.api.extensions.{
  MailJetConfiguration,
  MailJetConfigurationComponent,
  MakeSettings,
  MakeSettingsComponent
}
import org.make.api.proposal.PublishedProposalEvent.ReindexProposal
import org.make.api.proposal.{
  ProposalResponse,
  ProposalService,
  ProposalServiceComponent,
  ProposalsResultSeededResponse
}
import org.make.api.question.AuthorRequest
import org.make.api.technical._
import org.make.api.technical.auth._
import org.make.api.technical.crm._
import org.make.api.technical.job.{JobCoordinatorService, JobCoordinatorServiceComponent}
import org.make.api.technical.storage.Content.FileContent
import org.make.api.technical.storage.{StorageService, StorageServiceComponent}
import org.make.api.user.UserExceptions.{EmailAlreadyRegisteredException, EmailNotAllowed}
import org.make.api.user.social.models.UserInfo
import org.make.api.user.validation.{UserRegistrationValidator, UserRegistrationValidatorComponent}
import org.make.api.userhistory._
import org.make.api.{EmptyActorSystemComponent, MakeUnitTest, TestUtils}
import org.make.core.auth.UserRights
import org.make.core.profile.Gender.Female
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.proposal.{ProposalId, SearchQuery}
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.technical.IdGenerator
import org.make.core.user._
import org.make.core.{DateHelper, DateHelperComponent, RequestContext}
import org.mockito.Mockito.clearInvocations
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import scalaoauth2.provider.{AccessToken, AuthInfo}

import java.io.File
import java.text.SimpleDateFormat
import java.time.{LocalDate, ZonedDateTime}
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}

class UserServiceTest
    extends MakeUnitTest
    with DefaultUserServiceComponent
    with EmptyActorSystemComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with UserTokenGeneratorComponent
    with UserHistoryCoordinatorServiceComponent
    with PersistentUserServiceComponent
    with PersistentUserToAnonymizeServiceComponent
    with PersistentCrmUserServiceComponent
    with ProposalServiceComponent
    with CrmServiceComponent
    with TokenGeneratorComponent
    with EventBusServiceComponent
    with MakeSettingsComponent
    with UserRegistrationValidatorComponent
    with StorageServiceComponent
    with DownloadServiceComponent
    with JobCoordinatorServiceComponent
    with MailJetConfigurationComponent
    with DateHelperComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val userTokenGenerator: UserTokenGenerator = mock[UserTokenGenerator]
  override val userHistoryCoordinatorService: UserHistoryCoordinatorService = mock[UserHistoryCoordinatorService]
  override val proposalService: ProposalService = mock[ProposalService]
  override val crmService: CrmService = mock[CrmService]
  override val persistentUserToAnonymizeService: PersistentUserToAnonymizeService =
    mock[PersistentUserToAnonymizeService]
  override val persistentCrmUserService: PersistentCrmUserService = mock[PersistentCrmUserService]
  override val eventBusService: EventBusService = mock[EventBusService]
  override val tokenGenerator: TokenGenerator = mock[TokenGenerator]
  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val userRegistrationValidator: UserRegistrationValidator = mock[UserRegistrationValidator]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val storageService: StorageService = mock[StorageService]
  override val downloadService: DownloadService = mock[DownloadService]
  override val jobCoordinatorService: JobCoordinatorService = mock[JobCoordinatorService]
  override val mailJetConfiguration: MailJetConfiguration = mock[MailJetConfiguration]

  val currentDate: ZonedDateTime = DateHelper.now()

  override val dateHelper: DateHelper = mock[DateHelper]

  when(dateHelper.now()).thenReturn(currentDate)
  when(dateHelper.computeBirthDate(any[Int])).thenReturn(LocalDate.parse("1970-01-01"))

  when(makeSettings.defaultUserAnonymousParticipation).thenReturn(false)
  when(makeSettings.validationTokenExpiresIn).thenReturn(Duration("30 days"))
  when(makeSettings.resetTokenExpiresIn).thenReturn(Duration("1 days"))
  when(makeSettings.resetTokenB2BExpiresIn).thenReturn(Duration("3 days"))

  when(mailJetConfiguration.userListBatchSize).thenReturn(1000)

  when(persistentUserToAnonymizeService.create(any[String])).thenReturn(Future.unit)

  when(persistentUserService.updateUser(any[User])).thenAnswer { user: User =>
    Future.successful(user)
  }

  when(persistentUserService.persist(any[User])).thenAnswer { user: User =>
    Future.successful(user)
  }

  when(userTokenGenerator.generateVerificationToken())
    .thenReturn(Future.successful(("TOKEN", "HASHED_TOKEN")))

  when(userRegistrationValidator.canRegister(any[UserRegisterData])).thenReturn(Future.successful(true))

  override protected def afterEach(): Unit = {
    super.afterEach()
    clearInvocations(eventBusService)
  }

  val zonedDateTimeInThePast: Option[ZonedDateTime] = Some(ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]"))
  val fooProfile: Some[Profile] = Profile
    .parseProfile(
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
      politicalParty = Some("PP"),
      legalMinorConsent = Some(true),
      legalAdvisorApproval = Some(true)
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
    verificationTokenExpiresAt = zonedDateTimeInThePast,
    roles = Seq(Role.RoleAdmin, Role.RoleCitizen),
    profile = fooProfile,
    createdAt = zonedDateTimeInThePast
  )

  val johnDoeProfile: Some[Profile] = Profile
    .parseProfile(
      dateOfBirth = Some(LocalDate.parse("1984-10-11")),
      avatarUrl = Some("facebook.com/picture"),
      facebookId = Some("444444")
    )

  val johnDoeUser: User = TestUtils.user(
    id = UserId("AAA-BBB-CCC-DDD"),
    email = "johndoe@example.com",
    firstName = Some("john"),
    lastName = Some("doe"),
    lastIp = Some("127.0.0.1"),
    hashedPassword = Some("passpass".bcrypt),
    lastConnection = Some(DateHelper.now()),
    verificationToken = Some("Token"),
    verificationTokenExpiresAt = Some(DateHelper.now()),
    profile = johnDoeProfile
  )

  val returnedPersonality: User = TestUtils.user(
    id = UserId("AAA-BBB-CCC"),
    firstName = Some("perso"),
    lastName = Some("nality"),
    email = "personality@mail.com",
    hashedPassword = Some("passpass"),
    userType = UserType.UserTypePersonality
  )

  when(persistentUserService.get(fooUser.userId)).thenReturn(Future.successful(Some(fooUser)))

  Feature("Get personality") {
    Scenario("get personality") {
      when(persistentUserService.findByUserIdAndUserType(any[UserId], any[UserType]))
        .thenReturn(Future.successful(Some(returnedPersonality)))

      whenReady(userService.getPersonality(UserId("AAA-BBB-CCC")), Timeout(2.seconds)) { user =>
        user.isDefined shouldBe true
      }
    }

    Scenario("trying to get wrong personality") {
      when(persistentUserService.findByUserIdAndUserType(any[UserId], any[UserType]))
        .thenReturn(Future.successful(None))

      whenReady(userService.getPersonality(UserId("AAA-BBB-CCC-DDD")), Timeout(2.seconds)) { user =>
        user.isDefined shouldBe false
      }
    }
  }

  Feature("register user") {
    Scenario("successful register user") {
      when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(false))

      clearInvocations(persistentUserService)

      val futureUser = userService.register(
        UserRegisterData(
          email = "any@mail.com",
          firstName = Some("olive"),
          lastName = Some("tom"),
          password = Some("passopasso"),
          lastIp = Some("127.0.0.1"),
          dateOfBirth = Some(LocalDate.parse("1984-10-11")),
          country = Country("FR"),
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

        verify(persistentUserService).persist(argThat[User](!_.anonymousParticipation))
      }
    }

    Scenario("successful register user from social") {
      clearInvocations(eventBusService)
      when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(None))

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        gender = None,
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("facebook.com/picture"),
        dateOfBirth = None
      )

      val returnedProfile = Profile
        .parseProfile(
          dateOfBirth = Some(LocalDate.parse("1984-10-11")),
          avatarUrl = Some("facebook.com/picture"),
          facebookId = Some("444444"),
          registerQuestionId = Some(QuestionId("question"))
        )

      val returnedUser = TestUtils.user(
        id = UserId("AAA-BBB-CCC-DDD"),
        email = info.email.getOrElse(""),
        firstName = info.firstName,
        lastIp = Some("127.0.0.1"),
        hashedPassword = Some("passpass"),
        lastConnection = Some(DateHelper.now()),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(DateHelper.now()),
        profile = returnedProfile,
        privacyPolicyApprovalDate = Some(DateHelper.now())
      )

      when(persistentUserService.persist(any[User]))
        .thenReturn(Future.successful(returnedUser))

      clearInvocations(persistentUserService)

      val futureUser = userService.createOrUpdateUserFromSocial(
        info,
        Some(QuestionId("question")),
        Country("FR"),
        RequestContext.empty,
        Some(DateHelper.now())
      )

      whenReady(futureUser, Timeout(2.seconds)) {
        case (user, accountCreation) =>
          user.email should be(info.email.getOrElse(""))
          user.firstName should be(info.firstName)
          user.profile.get.facebookId should be(info.facebookId)
          user.profile.flatMap(_.registerQuestionId) should be(Some(QuestionId("question")))
          user.privacyPolicyApprovalDate shouldBe defined
          accountCreation should be(true)

          verify(eventBusService, times(1))
            .publish(argThat[AnyRef] {
              case event: UserRegisteredEvent => event.userId == returnedUser.userId
              case _                          => false
            })

          verify(persistentUserService).persist(argThat[User](!_.anonymousParticipation))
      }
    }

    Scenario("successful register user from social with gender") {
      when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(None))
      clearInvocations(eventBusService)
      val infoWithGender = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        gender = Some("female"),
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("facebook.com/picture"),
        dateOfBirth = None
      )

      val returnedProfileWithGender = Profile
        .parseProfile(
          dateOfBirth = Some(LocalDate.parse("1984-10-11")),
          avatarUrl = Some("facebook.com/picture"),
          facebookId = Some("444444"),
          gender = Some(Female),
          genderName = Some("female")
        )

      val returnedUserWithGender = TestUtils.user(
        id = UserId("AAA-BBB-CCC-DDD-EEE"),
        email = infoWithGender.email.getOrElse(""),
        firstName = infoWithGender.firstName,
        lastIp = Some("127.0.0.1"),
        hashedPassword = Some("passpass"),
        lastConnection = Some(DateHelper.now()),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(DateHelper.now()),
        profile = returnedProfileWithGender,
        privacyPolicyApprovalDate = Some(DateHelper.now())
      )

      when(persistentUserService.persist(any[User]))
        .thenReturn(Future.successful(returnedUserWithGender))

      val futureUserWithGender =
        userService.createOrUpdateUserFromSocial(
          infoWithGender,
          None,
          Country("FR"),
          RequestContext.empty,
          Some(DateHelper.now())
        )

      whenReady(futureUserWithGender, Timeout(2.seconds)) {
        case (user, accountCreation) =>
          user.email should be(infoWithGender.email.getOrElse(""))
          user.firstName should be(infoWithGender.firstName)
          user.profile.get.facebookId should be(infoWithGender.facebookId)
          user.profile.get.gender should be(Some(Female))
          user.profile.get.genderName should be(Some("female"))
          user.privacyPolicyApprovalDate shouldBe defined
          accountCreation should be(true)

          verify(eventBusService, times(1))
            .publish(argThat[AnyRef] {
              case event: UserRegisteredEvent => event.userId == returnedUserWithGender.userId
              case _                          => false
            })
      }
    }

    Scenario("successful update user from social without updating privacy policy date") {
      clearInvocations(eventBusService)
      clearInvocations(persistentUserService)

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        gender = None,
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("facebook.com/picture"),
        dateOfBirth = None
      )

      val returnedProfile = Profile
        .parseProfile(
          dateOfBirth = Some(LocalDate.parse("1984-10-11")),
          avatarUrl = Some("facebook.com/picture"),
          facebookId = Some("444444")
        )

      val returnedUser = TestUtils.user(
        id = UserId("AAA-BBB-CCC-DDD"),
        email = info.email.getOrElse(""),
        firstName = info.firstName,
        lastIp = Some("127.0.0.1"),
        hashedPassword = Some("passpass"),
        lastConnection = Some(DateHelper.now()),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(DateHelper.now()),
        profile = returnedProfile,
        privacyPolicyApprovalDate = Some(DateHelper.now().minusMonths(1))
      )

      when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(Some(returnedUser)))
      when(persistentUserService.updateSocialUser(any[User])).thenReturn(Future.successful(true))
      val futureUser = userService.createOrUpdateUserFromSocial(
        info,
        None,
        (Country("FR")),
        RequestContext.empty.copy(ipAddress = Some("NEW 127.0.0.1")),
        None
      )

      whenReady(futureUser, Timeout(2.seconds)) {
        case (user, accountCreation) =>
          user.email should be(info.email.getOrElse(""))
          user.firstName should be(info.firstName)
          user.profile.get.facebookId should be(info.facebookId)
          accountCreation should be(false)

          verify(persistentUserService, times(1)).updateSocialUser(any[User])
          user.privacyPolicyApprovalDate should be(returnedUser.privacyPolicyApprovalDate)
          user.profile.map(_.dateOfBirth) should be(returnedProfile.map(_.dateOfBirth))
          user.lastIp should be(Some("NEW 127.0.0.1"))

      }
    }

    Scenario("successful update user from social with privacy policy date updated") {
      clearInvocations(eventBusService)
      clearInvocations(persistentUserService)

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        gender = None,
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("facebook.com/picture"),
        dateOfBirth = None
      )

      val returnedProfile = Profile
        .parseProfile(
          dateOfBirth = Some(LocalDate.parse("1984-10-11")),
          avatarUrl = Some("facebook.com/picture"),
          facebookId = Some("444444")
        )

      val returnedUser = TestUtils.user(
        id = UserId("AAA-BBB-CCC-DDD"),
        email = info.email.getOrElse(""),
        firstName = info.firstName,
        lastIp = Some("127.0.0.1"),
        hashedPassword = Some("passpass"),
        lastConnection = Some(DateHelper.now()),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(DateHelper.now()),
        profile = returnedProfile,
        privacyPolicyApprovalDate = Some(DateHelper.now().minusMonths(1))
      )

      when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(Some(returnedUser)))
      when(persistentUserService.updateSocialUser(any[User])).thenReturn(Future.successful(true))
      val futureUser = userService.createOrUpdateUserFromSocial(
        info,
        None,
        Country("FR"),
        RequestContext.empty.copy(ipAddress = Some("NEW 127.0.0.1")),
        Some(DateHelper.now())
      )

      whenReady(futureUser, Timeout(2.seconds)) {
        case (user, accountCreation) =>
          user.email should be(info.email.getOrElse(""))
          user.firstName should be(info.firstName)
          user.profile.get.facebookId should be(info.facebookId)
          accountCreation should be(false)

          verify(persistentUserService, times(1)).updateSocialUser(any[User])
          user.privacyPolicyApprovalDate should not be returnedUser.privacyPolicyApprovalDate
          user.privacyPolicyApprovalDate should be > returnedUser.privacyPolicyApprovalDate
          user.profile.map(_.dateOfBirth) should be(returnedProfile.map(_.dateOfBirth))
          user.lastIp should be(Some("NEW 127.0.0.1"))

      }
    }

    Scenario("successful update user from social with date of birth") {
      clearInvocations(eventBusService)
      clearInvocations(persistentUserService)

      val now = LocalDate.now
      val info = UserInfo(email = Some("facebook@make.org"), firstName = Some("facebook"), dateOfBirth = Some(now))

      val returnedProfileNoDoB = Profile.parseProfile()
      val returnedUserNoDoB = TestUtils.user(id = UserId("BBB-CCC-DDD-EEEE"), profile = returnedProfileNoDoB)

      when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(Some(returnedUserNoDoB)))
      when(persistentUserService.updateSocialUser(any[User])).thenReturn(Future.successful(true))
      val futureUserNoDoB =
        userService.createOrUpdateUserFromSocial(info, None, Country("FR"), RequestContext.empty, None)

      whenReady(futureUserNoDoB, Timeout(2.seconds)) {
        case (user, _) =>
          user.profile.get.dateOfBirth should be(info.dateOfBirth)

          verify(persistentUserService, times(1)).updateSocialUser(argThat[User] { user: User =>
            user.profile.flatMap(_.dateOfBirth).contains(now)
          })
      }
    }

    Scenario("successful update user from social without date of birth") {
      clearInvocations(eventBusService)
      clearInvocations(persistentUserService)

      val info =
        UserInfo(email = Some("facebook@make.org"), firstName = Some("facebook"), dateOfBirth = Some(LocalDate.now))

      val currentDateOfBirth = LocalDate.parse("1984-10-11")
      val returnedProfile = Profile.parseProfile(dateOfBirth = Some(currentDateOfBirth))
      val returnedUser = TestUtils.user(id = UserId("AAA-BBB-CCC-DDD"), profile = returnedProfile)

      when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(Some(returnedUser)))
      when(persistentUserService.updateSocialUser(any[User])).thenReturn(Future.successful(true))
      val futureUser = userService.createOrUpdateUserFromSocial(info, None, Country("FR"), RequestContext.empty, None)

      whenReady(futureUser, Timeout(2.seconds)) {
        case (user, _) =>
          user.profile.get.dateOfBirth should be(returnedProfile.flatMap(_.dateOfBirth))

          verify(persistentUserService, times(1)).updateSocialUser(argThat[User] { user: User =>
            user.profile.flatMap(_.dateOfBirth).contains(currentDateOfBirth)
          })
      }
    }

    Scenario("successful update user without difference from social") {
      clearInvocations(eventBusService)
      clearInvocations(persistentUserService)

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        gender = None,
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("facebook.com/picture"),
        dateOfBirth = None
      )

      val returnedProfile = Profile
        .parseProfile(
          dateOfBirth = Some(LocalDate.parse("1984-10-11")),
          avatarUrl = Some("facebook.com/picture"),
          facebookId = Some("444444")
        )

      val returnedUser = TestUtils.user(
        id = UserId("AAA-BBB-CCC-DDD"),
        email = info.email.getOrElse(""),
        firstName = info.firstName,
        lastIp = Some("127.0.0.1"),
        hashedPassword = Some("passpass"),
        lastConnection = Some(DateHelper.now()),
        verificationToken = Some("Token"),
        verificationTokenExpiresAt = Some(DateHelper.now()),
        profile = returnedProfile
      )

      when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(Some(returnedUser)))
      when(persistentUserService.updateSocialUser(any[User])).thenReturn(Future.successful(true))

      val futureUser = userService.createOrUpdateUserFromSocial(
        info,
        None,
        Country("FR"),
        RequestContext.empty.copy(ipAddress = returnedUser.lastIp),
        None
      )

      whenReady(futureUser, Timeout(2.seconds)) {
        case (user, accountCreation) =>
          user.email should be(info.email.getOrElse(""))
          user.firstName should be(info.firstName)
          user.profile.get.facebookId should be(info.facebookId)
          accountCreation should be(false)
      }
    }

    Scenario("email already registred") {
      when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(true))
      clearInvocations(eventBusService)
      val futureUser = userService.register(
        UserRegisterData(
          email = "exist@mail.com",
          firstName = Some("tom"),
          lastName = Some("olive"),
          password = Some("passopasso"),
          lastIp = Some("127.0.0.1"),
          dateOfBirth = Some(LocalDate.parse("1984-10-11")),
          country = Country("FR")
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

      when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(Some(user)))
      when(persistentUserService.updateSocialUser(any[User])).thenReturn(Future.successful(true))

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        gender = None,
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("facebook.com/picture"),
        dateOfBirth = None
      )

      whenReady(
        userService.createOrUpdateUserFromSocial(info, None, Country("FR"), RequestContext.empty, None),
        Timeout(3.seconds)
      ) {
        case (user, accountCreation) =>
          user.emailVerified shouldBe true
          user.hashedPassword shouldBe Some("hashedPassword")
          accountCreation should be(false)
      }

      verify(eventBusService, never)
        .publish(any[AnyRef])

    }

    Scenario("email not verified already registred") {
      when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(true))
      clearInvocations(eventBusService)

      val user = TestUtils.user(
        id = UserId("AAA-BBB-CCC-DDD-EEE"),
        email = "existing-user@gmail.com",
        firstName = None,
        lastName = None,
        hashedPassword = Some("hashedPassword"),
        emailVerified = false
      )

      when(persistentUserService.findByEmail(any[String])).thenReturn(Future.successful(Some(user)))
      when(persistentUserService.updateSocialUser(any[User])).thenReturn(Future.successful(true))

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        gender = None,
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("facebook.com/picture"),
        dateOfBirth = None
      )

      whenReady(
        userService.createOrUpdateUserFromSocial(info, None, Country("FR"), RequestContext.empty, None),
        Timeout(3.seconds)
      ) {
        case (user, accountCreation) =>
          user.emailVerified shouldBe true
          user.hashedPassword shouldBe None
          accountCreation should be(false)
      }

      verify(eventBusService, never)
        .publish(any[AnyRef])

    }

    Scenario("email not allowed") {
      when(persistentUserService.emailExists("notvalidated@make.org"))
        .thenReturn(Future.successful(false))

      when(userRegistrationValidator.canRegister(argThat[UserRegisterData](_.email == "notvalidated@make.org")))
        .thenReturn(Future.successful(false))

      val register = userService.register(
        UserRegisterData(
          email = "notvalidated@make.org",
          firstName = None,
          password = None,
          lastIp = None,
          country = Country("FR")
        ),
        RequestContext.empty
      )

      whenReady(register.failed, Timeout(2.seconds)) {
        case EmailNotAllowed(_) =>
        case other              => fail(s"${other.getClass.getName} is not the expected EmailNotAllowed exception")
      }
    }
  }

  Feature("register personality") {
    Scenario("successful register personality") {
      clearInvocations(persistentUserService)

      when(userTokenGenerator.generateResetToken()).thenReturn(Future.successful(("TOKEN", "HASHED_TOKEN")))

      when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(false))

      when(persistentUserService.persist(any[User])).thenReturn(Future.successful(returnedPersonality))

      val futurePersonality = userService.registerPersonality(
        PersonalityRegisterData(
          email = "personality@mail.com",
          firstName = Some("perso"),
          lastName = Some("nality"),
          country = Country("FR"),
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
        .publish(argThat[AnyRef] {
          case event: PersonalityRegisteredEvent => event.userId == returnedPersonality.userId
          case _                                 => false
        })
    }
  }

  Feature("password recovery") {
    Scenario("successfully reset password") {

      when(persistentUserService.requestResetPassword(any[UserId], any[String], any[Option[ZonedDateTime]]))
        .thenReturn(Future.successful(true))

      when(userTokenGenerator.generateResetToken()).thenReturn(Future.successful(("TOKEN", "HASHED_TOKEN")))

      val userId = UserId("user-reset-password-successfully")

      whenReady(userService.requestPasswordReset(userId), Timeout(3.seconds)) { result =>
        result shouldBe true
      }
    }
  }

  Feature("validate email") {
    Scenario("Send access token when validate email") {
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

      when(oauth2DataHandler.createAccessToken(any[AuthInfo[UserRights]]))
        .thenReturn(Future.successful(accessToken))

      when(persistentUserService.validateEmail(any[String])).thenReturn(Future.successful(true))

      whenReady(userService.validateEmail(fooUser, "verificationToken"), Timeout(2.seconds)) { tokenResponse =>
        tokenResponse should be(a[TokenResponse])
        tokenResponse.accessToken should be(accessTokenValue)
        tokenResponse.refreshToken should contain(refreshTokenValue)
        tokenResponse.tokenType should be("Bearer")
      }
    }
  }

  Feature("update opt in newsletter") {
    Scenario("update opt in newsletter using userId") {
      Given("a user")
      val userIdOptinNewsletter: UserId = UserId("update-opt-in-user")
      When("I update opt in newsletter using UserId")
      Then("opt in value is updated")
      when(persistentUserService.updateOptInNewsletter(any[UserId], eqTo(true)))
        .thenReturn(Future.successful(true))

      val futureBoolean = userService.updateOptInNewsletter(userIdOptinNewsletter, optInNewsletter = true)

      whenReady(futureBoolean, Timeout(3.seconds)) { result =>
        result shouldBe true
      }
    }
    Scenario("update opt in newsletter using email") {
      Given("a user")
      When("I update opt in newsletter using user email")
      Then("opt in value is updated")

      clearInvocations(eventBusService)
      when(
        persistentUserService
          .updateOptInNewsletter(eqTo("user@example.com"), eqTo(true))
      ).thenReturn(Future.successful(true))

      val futureBoolean = userService.updateOptInNewsletter("user@example.com", optInNewsletter = true)

      whenReady(futureBoolean, Timeout(3.seconds)) { result =>
        result shouldBe true

        verify(eventBusService, times(1))
          .publish(argThat[UserUpdatedOptInNewsletterEvent](_.optInNewsletter))
      }
    }
  }

  Feature("update hard bounce") {
    Scenario("update hard bounce using userId") {
      Given("a user")
      When("I update hard bounce using UserId")
      Then("hard bounce is updated")
      when(persistentUserService.updateIsHardBounce(any[UserId], eqTo(true)))
        .thenReturn(Future.successful(true))

      val futureBoolean = userService.updateIsHardBounce(UserId("update-opt-in-user"), isHardBounce = true)

      whenReady(futureBoolean, Timeout(3.seconds)) { result =>
        result shouldBe true
      }
    }
    Scenario("update hard bounce using email") {
      Given("a user")
      When("I update hard bounce using user email")
      Then("hard bounce is updated")
      when(
        persistentUserService
          .updateIsHardBounce(eqTo("user@example.com"), eqTo(true))
      ).thenReturn(Future.successful(true))

      val futureBoolean = userService.updateIsHardBounce("user@example.com", isHardBounce = true)

      whenReady(futureBoolean, Timeout(3.seconds)) { result =>
        result shouldBe true
      }
    }
  }

  Feature("update mailing error") {
    Scenario("update mailing error using userId") {
      Given("a user")
      When("I update mailing error using UserId")
      Then("mailing error is updated")
      when(persistentUserService.updateLastMailingError(any[UserId], any[Option[MailingErrorLog]]))
        .thenReturn(Future.successful(true))

      val futureBoolean = userService.updateLastMailingError(
        UserId("update-opt-in-user"),
        Some(MailingErrorLog(error = "my_error", date = DateHelper.now()))
      )

      whenReady(futureBoolean, Timeout(3.seconds)) { result =>
        result shouldBe true
      }
    }
    Scenario("update mailing error using email") {
      Given("a user")
      When("I update mailing error using user email")
      Then("mailing error is updated")
      when(
        persistentUserService
          .updateLastMailingError(eqTo("user@example.com"), any[Option[MailingErrorLog]])
      ).thenReturn(Future.successful(true))

      val futureBoolean = userService.updateLastMailingError(
        "user@example.com",
        Some(MailingErrorLog(error = "my_error", date = DateHelper.now()))
      )

      whenReady(futureBoolean, Timeout(3.seconds)) { result =>
        result shouldBe true
      }
    }
  }

  Feature("update user") {
    Scenario("update a user") {
      Given("a user")
      When("I update fields")
      Then("fields are updated into user")

      val proposals = Seq(
        ProposalResponse(indexedProposal(ProposalId("update a user 1")), myProposal = true, None, "none"),
        ProposalResponse(indexedProposal(ProposalId("update a user 2")), myProposal = true, None, "none"),
        ProposalResponse(indexedProposal(ProposalId("update a user 3")), myProposal = true, None, "none")
      )

      when(proposalService.searchForUser(eqTo(Some(fooUser.userId)), any[SearchQuery], any[RequestContext]))
        .thenReturn(Future.successful(ProposalsResultSeededResponse(0, proposals, None)))

      val futureUser = userService.update(fooUser, RequestContext.empty)

      whenReady(futureUser, Timeout(3.seconds)) { _ =>
        proposals.foreach(
          p =>
            verify(eventBusService).publish(argThat[AnyRef] {
              case ReindexProposal(p.id, `currentDate`, RequestContext.empty, _) => true
              case _                                                             => false
            })
        )
      }
    }

    Scenario("change user email") {
      Given("a user")
      val userId = UserId("change user email")
      val user = fooUser.copy(userId = userId, email = "original-email@example.com")

      when(proposalService.searchForUser(eqTo(Some(userId)), any[SearchQuery], any[RequestContext]))
        .thenReturn(Future.successful(ProposalsResultSeededResponse(0, Seq.empty, None)))

      when(persistentUserService.get(userId)).thenReturn(Future.successful(Some(user)))

      When("I update email")
      val newEmail = "modified-email@example.com"
      val futureUser = userService.update(user.copy(email = newEmail), RequestContext.empty)

      Then("email is added to usersToAnonymize")
      whenReady(futureUser, Timeout(3.seconds)) { _ =>
        verify(persistentUserToAnonymizeService).create(user.email)
      }
    }
  }

  Feature("update personality user") {
    Scenario("update a personality") {
      Given("a personality")
      When("I update fields")
      Then("fields are updated into user")

      clearInvocations(eventBusService)
      val userId = UserId("update a personality")

      when(proposalService.searchForUser(eqTo(Some(userId)), any[SearchQuery], any[RequestContext]))
        .thenReturn(Future.successful(ProposalsResultSeededResponse(0, Seq.empty, None)))

      val futureUser = userService.updatePersonality(
        personality = fooUser.copy(email = "fooUpdated@example.com", userId = userId),
        moderatorId = Some(UserId("moderaor-id")),
        oldEmail = fooUser.email,
        requestContext = RequestContext.empty
      )

      whenReady(futureUser, Timeout(3.seconds)) { user =>
        user.email should be("fooUpdated@example.com")
      }
      verify(eventBusService, times(1))
        .publish(any[PersonalityEmailChangedEvent])
    }
  }

  Feature("change password without token") {
    Scenario("update existing password") {
      val johnChangePassword: User =
        johnDoeUser.copy(userId = UserId("userchangepasswordid"), hashedPassword = Some("mypassword".bcrypt))
      val newPassword: String = "mypassword2"

      clearInvocations(eventBusService)
      when(persistentUserService.updatePassword(eqTo(johnChangePassword.userId), eqTo(None), argThat[String] { pass =>
        newPassword.isBcrypted(pass)
      }))
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

  Feature("anonymize user") {
    Scenario("anonymize user") {
      clearInvocations(eventBusService)

      when(persistentUserService.removeAnonymizedUserFromFollowedUserTable(any[UserId]))
        .thenReturn(Future.unit)
      when(userHistoryCoordinatorService.delete(johnDoeUser.userId)).thenReturn(Future.unit)
      when(proposalService.searchForUser(any, any, any))
        .thenReturn(Future.successful(ProposalsResultSeededResponse.empty))

      Given("a user")
      When("I anonymize this user")
      val adminId = UserId("admin")
      val futureAnonymizeUser =
        userService.anonymize(johnDoeUser, adminId, RequestContext.empty, Anonymization.Explicit)

      Then("an event is sent")
      whenReady(futureAnonymizeUser, Timeout(3.seconds)) { _ =>
        verify(eventBusService, times(1))
          .publish(argThat[UserAnonymizedEvent] { event =>
            event.userId == johnDoeUser.userId && event.adminId == adminId
          })
      }
    }
  }

  Feature("anonymize inactive users") {
    Scenario("anonymize inactive users") {
      clearInvocations(eventBusService)
      val fooCrmUser = PersistentCrmUser(
        userId = "1",
        email = "foo@example.com",
        fullName = "Foo John",
        firstname = "Foo",
        zipcode = None,
        dateOfBirth = None,
        emailValidationStatus = true,
        emailHardbounceStatus = false,
        unsubscribeStatus = false,
        accountCreationDate = None,
        accountCreationSource = None,
        accountCreationOrigin = None,
        accountCreationOperation = None,
        accountCreationCountry = None,
        accountCreationLocation = None,
        countriesActivity = None,
        lastCountryActivity = Some("FR"),
        totalNumberProposals = Some(42),
        totalNumberVotes = Some(1337),
        firstContributionDate = None,
        lastContributionDate = None,
        operationActivity = None,
        sourceActivity = None,
        daysOfActivity = None,
        daysOfActivity30d = None,
        userType = None,
        accountType = None,
        daysBeforeDeletion = None,
        lastActivityDate = None,
        sessionsCount = None,
        eventsCount = None
      )

      val johnDoeCrmUser = PersistentCrmUser(
        userId = "AAA-BBB-CCC-DDD",
        email = "johndoe@example.com",
        fullName = "john doe",
        firstname = "john",
        zipcode = None,
        dateOfBirth = None,
        emailValidationStatus = true,
        emailHardbounceStatus = false,
        unsubscribeStatus = false,
        accountCreationDate = None,
        accountCreationSource = None,
        accountCreationOrigin = None,
        accountCreationOperation = None,
        accountCreationCountry = None,
        accountCreationLocation = None,
        countriesActivity = None,
        lastCountryActivity = Some("FR"),
        totalNumberProposals = Some(42),
        totalNumberVotes = Some(1337),
        firstContributionDate = None,
        lastContributionDate = None,
        operationActivity = None,
        sourceActivity = None,
        daysOfActivity = None,
        daysOfActivity30d = None,
        userType = None,
        accountType = None,
        daysBeforeDeletion = None,
        lastActivityDate = None,
        sessionsCount = None,
        eventsCount = None
      )

      when(persistentCrmUserService.findInactiveUsers(any[Int], any[Int]))
        .thenReturn(Future.successful(Seq(fooCrmUser, johnDoeCrmUser)))
      when(persistentUserService.findAllByUserIds(Seq(UserId(fooCrmUser.userId), UserId(johnDoeCrmUser.userId))))
        .thenReturn(Future.successful(Seq(fooUser, johnDoeUser)))
      when(persistentUserService.removeAnonymizedUserFromFollowedUserTable(any[UserId]))
        .thenReturn(Future.unit)
      when(userHistoryCoordinatorService.delete(fooUser.userId)).thenReturn(Future.unit)
      when(userHistoryCoordinatorService.delete(johnDoeUser.userId)).thenReturn(Future.unit)

      When("I anonymize inactive users")
      val adminId = UserId("admin")
      val futureAnonymizeUser =
        userService.anonymize(fooUser, adminId, RequestContext.empty, Anonymization.Automatic)

      Then("an event is sent")
      whenReady(futureAnonymizeUser, Timeout(3.seconds)) { _ =>
        verify(eventBusService, times(1))
          .publish(argThat[UserAnonymizedEvent] { event =>
            event.userId == fooUser.userId && event.adminId == adminId
          })
      }
    }
  }

  Feature("follow user") {
    Scenario("follow user") {
      clearInvocations(eventBusService)
      when(persistentUserService.followUser(any[UserId], any[UserId]))
        .thenReturn(Future.unit)

      val futureFollowOrganisation =
        userService.followUser(UserId("user-id"), UserId("me"), RequestContext.empty)

      whenReady(futureFollowOrganisation, Timeout(2.seconds)) { _ =>
        verify(eventBusService, times(1)).publish(any[UserFollowEvent])
      }
    }
  }

  Feature("unfollow user") {
    Scenario("unfollow user") {
      clearInvocations(eventBusService)
      when(persistentUserService.unfollowUser(any[UserId], any[UserId]))
        .thenReturn(Future.unit)

      val futureFollowOrganisation =
        userService.unfollowUser(UserId("make-org"), UserId("me"), RequestContext.empty)

      whenReady(futureFollowOrganisation, Timeout(2.seconds)) { _ =>
        verify(eventBusService, times(1)).publish(any[UserUnfollowEvent])
      }
    }
  }

  Feature("Create or retrieve virtual user") {
    Scenario("Existing user") {
      val request: AuthorRequest =
        AuthorRequest(
          age = Some(20),
          firstName = "who cares anyway",
          lastName = None,
          postalCode = None,
          profession = None
        )

      when(tokenGenerator.tokenToHash(eqTo(request.toString))).thenReturn("some-hash")
      val user = TestUtils.user(
        id = UserId("existing-user-id"),
        email = "yopmail+some-hash@make.org",
        firstName = Some(request.firstName),
        lastName = None,
        enabled = false,
        emailVerified = false
      )
      when(persistentUserService.findByEmail("yopmail+some-hash@make.org"))
        .thenReturn(Future.successful(Some(user)))

      val result = userService.retrieveOrCreateVirtualUser(request, Country("FR"))

      whenReady(result, Timeout(5.seconds)) { resultUser =>
        resultUser should be(user)
      }

    }
    Scenario("New user") {
      val request: AuthorRequest =
        AuthorRequest(
          age = Some(20),
          firstName = "who cares anyway",
          lastName = None,
          postalCode = None,
          profession = None
        )

      when(tokenGenerator.tokenToHash(eqTo(request.toString))).thenReturn("some-other-hash")

      when(persistentUserService.findByEmail("yopmail+some-other-hash@make.org"))
        .thenReturn(Future.successful(None))

      when(persistentUserService.persist(any[User])).thenAnswer { user: User =>
        Future.successful(user)
      }

      when(persistentUserService.emailExists(eqTo("yopmail+some-other-hash@make.org")))
        .thenReturn(Future.successful(false))

      val result = userService.retrieveOrCreateVirtualUser(request, Country("FR"))

      whenReady(result, Timeout(5.seconds)) { resultUser =>
        resultUser.email should be("yopmail+some-other-hash@make.org")
      }
    }
  }

  Feature("get reconnect info") {
    Scenario("reconnect info") {

      when(persistentUserService.get(UserId("userId"))).thenReturn(Future.successful(Some(fooUser)))
      when(userTokenGenerator.generateReconnectToken()).thenReturn(Future.successful(("token", "hashedToken")))
      when(persistentUserService.updateReconnectToken(any[UserId], any[String], any[ZonedDateTime]))
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

  Feature("changeEmailVerificationTokenIfNeeded") {
    Scenario("verification token changed more than 10 minutes ago") {
      clearInvocations(persistentUserService)

      when(persistentUserService.get(UserId("old-verification-token")))
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
    Scenario("no verification token expiration") {
      clearInvocations(persistentUserService)

      when(persistentUserService.get(UserId("no-verification-token")))
        .thenReturn(Future.successful(Some(fooUser.copy(emailVerified = false, verificationTokenExpiresAt = None))))

      whenReady(userService.changeEmailVerificationTokenIfNeeded(UserId("no-verification-token")), Timeout(2.seconds)) {
        maybeToken =>
          maybeToken should be(defined)
          verify(persistentUserService).updateUser(any[User])
      }
    }
    Scenario("no verification token expiration but email verified") {
      clearInvocations(persistentUserService)

      when(persistentUserService.get(UserId("no-verification-token-but-verified")))
        .thenReturn(Future.successful(Some(fooUser.copy(verificationTokenExpiresAt = None, emailVerified = true))))

      whenReady(
        userService.changeEmailVerificationTokenIfNeeded(UserId("no-verification-token-but-verified")),
        Timeout(2.seconds)
      ) { maybeToken =>
        maybeToken should be(None)
        verify(persistentUserService, never).updateUser(any[User])
      }
    }
    Scenario("verification token changed less than 10 minutes ago") {
      clearInvocations(persistentUserService)

      when(persistentUserService.get(UserId("young-verification-token")))
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
        verify(persistentUserService, never).updateUser(any[User])
      }
    }
  }

  Feature("upload avatar") {
    val nowDate = DateHelper.now()
    Scenario("image url") {
      val imageUrl = "https://i.picsum.photos/id/352/200/200.jpg"
      val file = File.createTempFile("tmp", ".jpeg")
      file.deleteOnExit()
      when(downloadService.downloadImage(eqTo(imageUrl), any[ContentType => File], any[Int]))
        .thenReturn(Future.successful((ContentType(MediaTypes.`image/jpeg`), file)))
      when(storageService.uploadUserAvatar(eqTo("jpeg"), eqTo(MediaTypes.`image/jpeg`.value), any[FileContent]))
        .thenReturn(Future.successful("path/to/upload-avatar-image-url/image"))
      when(persistentUserService.get(UserId("upload-avatar-image-url")))
        .thenReturn(Future.successful(Some(fooUser.copy(userId = UserId("upload-avatar-image-url")))))
      when(
        proposalService
          .searchForUser(eqTo(Some(UserId("upload-avatar-image-url"))), any[SearchQuery], eqTo(RequestContext.empty))
      ).thenReturn(Future.successful(ProposalsResultSeededResponse(0, Seq.empty, None)))

      whenReady(
        userService.changeAvatarForUser(UserId("upload-avatar-image-url"), imageUrl, RequestContext.empty, nowDate),
        Timeout(2.seconds)
      ) { _ =>
        verify(persistentUserService)
          .updateUser(
            argThat[User](user => user.profile.flatMap(_.avatarUrl).contains("path/to/upload-avatar-image-url/image"))
          )
        verify(userHistoryCoordinatorService)
          .logHistory(argThat[LogUserUploadedAvatarEvent] { event =>
            event.userId.value == "upload-avatar-image-url"
          })
      }
    }

    Scenario("image url unavailable") {
      val imageUrl = "https://i.picsum.photos/id/352/200/200.jpg"
      val file = File.createTempFile("tmp", ".jpeg")
      file.deleteOnExit()
      when(downloadService.downloadImage(eqTo(imageUrl), any[ContentType => File], any[Int]))
        .thenReturn(Future.failed(ImageUnavailable("path/url")))
      when(persistentUserService.get(UserId("upload-avatar-image-url-unavailable")))
        .thenReturn(Future.successful(Some(fooUser.copy(userId = UserId("upload-avatar-image-url-unavailable")))))
      when(
        proposalService.searchForUser(
          eqTo(Some(UserId("upload-avatar-image-url-unavailable"))),
          any[SearchQuery],
          eqTo(RequestContext.empty)
        )
      ).thenReturn(Future.successful(ProposalsResultSeededResponse(0, Seq.empty, None)))

      whenReady(
        userService
          .changeAvatarForUser(UserId("upload-avatar-image-url-unavailable"), imageUrl, RequestContext.empty, nowDate),
        Timeout(2.seconds)
      ) { _ =>
        verify(persistentUserService)
          .updateUser(argThat[User](user => user.profile.flatMap(_.avatarUrl).isEmpty))
        verify(userHistoryCoordinatorService)
          .logHistory(argThat[LogUserUploadedAvatarEvent] { event =>
            event.userId.value == "upload-avatar-image-url-unavailable"
          })
      }
    }

    Scenario("download failed") {
      val imageUrl = "https://www.google.fr"
      val file = File.createTempFile("tmp", ".pdf")
      file.deleteOnExit()
      when(downloadService.downloadImage(eqTo(imageUrl), any[ContentType => File], any[Int]))
        .thenReturn(Future.failed(new IllegalArgumentException(s"URL does not refer to an image: $imageUrl")))

      whenReady(
        userService.changeAvatarForUser(UserId("user-id"), imageUrl, RequestContext.empty, nowDate).failed,
        Timeout(2.seconds)
      ) { exception =>
        exception shouldBe a[IllegalArgumentException]
      }
    }
  }

  Feature("update user email") {
    Scenario("it works") {
      val user = fooUser.copy(email = "foo+ineedauniqueemail@example.com")
      whenReady(userService.adminUpdateUserEmail(user, "bar@example.com"), Timeout(2.seconds)) { _ =>
        verify(persistentUserService).updateUser(user.copy(email = "bar@example.com"))
        verify(persistentUserToAnonymizeService).create("foo+ineedauniqueemail@example.com")
      }
    }
  }
}
