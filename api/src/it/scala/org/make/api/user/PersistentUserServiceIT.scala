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

import java.time.{LocalDate, ZoneId, ZonedDateTime}

import com.github.t3hnar.bcrypt._
import org.make.api.user.DefaultPersistentUserServiceComponent.UpdateFailed
import org.make.api.{DatabaseTest, TestUtilsIT}
import org.make.core.{DateHelper, Order}
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.user._
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentUserServiceIT extends DatabaseTest with DefaultPersistentUserServiceComponent {

  override protected val cockroachExposedPort: Int = 40002

  val before: ZonedDateTime = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")

  val johnDoeProfile: Some[Profile] = Profile
    .parseProfile(
      dateOfBirth = Some(LocalDate.parse("2000-01-01")),
      avatarUrl = Some("https://www.example.com"),
      profession = Some("profession"),
      phoneNumber = Some("010101"),
      description = Some("Resume of who I am"),
      twitterId = Some("@twitterid"),
      facebookId = Some("facebookid"),
      googleId = Some("googleId"),
      gender = Some(Gender.Male),
      genderName = Some("other"),
      postalCode = Some("93"),
      karmaLevel = Some(2),
      locale = Some("FR_FR"),
      socioProfessionalCategory = Some(SocioProfessionalCategory.Farmers),
      registerQuestionId = Some(QuestionId("the question")),
      optInPartner = Some(true)
    )

  val johnDoe: User = TestUtilsIT.user(
    id = UserId("1"),
    email = "doe@example.com",
    firstName = Some("John"),
    lastName = Some("Doe"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    roles = Seq(Role.RoleAdmin, Role.RoleModerator, Role.RoleCitizen),
    profile = johnDoeProfile,
    anonymousParticipation = true
  )

  val jennaDoo = TestUtilsIT.user(
    id = UserId("2"),
    email = "jennaDoo@example.com",
    firstName = Some("Jenna"),
    lastName = Some("Doo"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before)
  )

  val janeDee = TestUtilsIT.user(
    id = UserId("3"),
    email = "janeDee@example.com",
    firstName = Some("Jane"),
    lastName = Some("Dee"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    roles = Seq(Role.RoleAdmin, Role.RoleModerator)
  )

  val passwordUser = TestUtilsIT.user(
    id = UserId("4"),
    email = "password@example.com",
    firstName = Some("user-with"),
    lastName = Some("Password"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("123456".bcrypt),
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    roles = Seq(Role.RoleAdmin, Role.RoleModerator)
  )

  val socialUser = TestUtilsIT.user(
    id = UserId("5"),
    email = "social@example.com",
    firstName = Some("Social"),
    lastName = Some("User"),
    lastIp = Some("0.0.0.0"),
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    roles = Seq(Role.RoleAdmin, Role.RoleModerator),
    profile = Profile.parseProfile(dateOfBirth = Some(LocalDate.parse("1970-01-01")))
  )

  val userOrganisationDGSE = TestUtilsIT.user(
    id = UserId("DGSE"),
    email = "dgse@secret-agency.com",
    firstName = None,
    lastName = None,
    lastIp = Some("-1.-1.-1.-1"),
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    roles = Seq(Role.RoleActor),
    organisationName = Some("Direction Générale de la Sécurité Extérieure"),
    userType = UserType.UserTypeOrganisation
  )

  val userOrganisationCSIS = TestUtilsIT.user(
    id = UserId("CSIS"),
    email = "csis@secret-agency.com",
    firstName = None,
    lastName = None,
    lastIp = Some("-1.-1.-1.-1"),
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    roles = Seq(Role.RoleActor),
    organisationName = Some("Canadian Security Intelligence Service"),
    userType = UserType.UserTypeOrganisation
  )

  val userOrganisationFSB = TestUtilsIT.user(
    id = UserId("FSB"),
    email = "fsb@secret-agency.com",
    firstName = None,
    lastName = None,
    lastIp = Some("-1.-1.-1.-1"),
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    roles = Seq(Role.RoleActor),
    country = Country("RU"),
    language = Language("ru"),
    organisationName = Some("Federal Security Service"),
    userType = UserType.UserTypeOrganisation
  )

  val johnMailing: User = johnDoe.copy(
    email = "johnmailing@example.com",
    userId = UserId("6"),
    isHardBounce = true,
    lastMailingError =
      Some(MailingErrorLog(error = "my error", date = ZonedDateTime.parse("2018-12-12T12:30:40+01:00[Europe/Paris]")))
  )

  val noRegisterQuestionUser: User = johnDoe.copy(
    email = "noregistration@question.com",
    userId = UserId("no-question"),
    profile = johnDoeProfile.map(_.copy(registerQuestionId = None))
  )

  val userOrganisationCIA = TestUtilsIT.user(
    id = UserId("CIA"),
    email = "cia@secret-agency.com",
    firstName = None,
    lastName = None,
    lastIp = Some("-1.-1.-1.-1"),
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    roles = Seq(Role.RoleActor),
    country = Country("US"),
    language = Language("en"),
    organisationName = Some("Central Intelligence Agency - CIA"),
    userType = UserType.UserTypeOrganisation
  )

  val userOrganisationFBI = TestUtilsIT.user(
    id = UserId("FBI"),
    email = "fbi@secret-agency.com",
    firstName = None,
    lastName = None,
    lastIp = Some("-1.-1.-1.-1"),
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    roles = Seq(Role.RoleActor),
    country = Country("US"),
    language = Language("en"),
    organisationName = Some("Federal Bureau of Investigation - FBI"),
    userType = UserType.UserTypeOrganisation
  )

  val userOrganisationMI5 = TestUtilsIT.user(
    id = UserId("MI5"),
    email = "mi5@secret-agency.com",
    firstName = None,
    lastName = None,
    lastIp = Some("-1.-1.-1.-1"),
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    roles = Seq(Role.RoleActor),
    country = Country("UK"),
    language = Language("en"),
    organisationName = Some("Military Intelligence, Section 5 - MI5"),
    userType = UserType.UserTypeOrganisation
  )

  val updateUser = TestUtilsIT.user(
    id = UserId("update-user-1"),
    email = "foo@example.com",
    firstName = Some("John"),
    lastName = Some("Doe"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    roles = Seq(Role.RoleAdmin, Role.RoleModerator, Role.RoleCitizen),
    profile = johnDoeProfile
  )

  val personalityUser = TestUtilsIT.user(
    id = UserId("personality"),
    email = "personality@example.com",
    firstName = Some("perso"),
    lastName = Some("nality"),
    userType = UserType.UserTypePersonality
  )

  var futureJohnMailing2: Future[User] = Future.failed(new IllegalStateException("I am no ready!!!!"))

  Feature("The app can persist and retrieve users") {
    info("As a programmer")
    info("I want to be able to persist a user")

    Scenario("Persist a user and get the persisted user") {
      Given("""a user John Doe with values:
          |    - firstName: John
          |    - lastName: Doe
          |    - email: doe@example.com
          |    - emailVerified: true
          |    - roles: ROLE_ADMIN, ROLE_MODERATOR, ROLE_CITIZEN
        """.stripMargin)
      And("""a John Doe profile with values:
          |    - gender: Male
          |    - twitterId: @twitterid
          |    - karmaLevel: 2
          |    - socioProfessionalCategory: farmers
          |    - registerQuestionId: "the question"
          |    - optInPartner: true
        """.stripMargin)

      When("I persist John Doe and John Doe profile")
      And("I get the persisted user")
      val futureUser: Future[Option[User]] = persistentUserService
        .persist(johnDoe)
        .flatMap(_ => persistentUserService.get(UserId("1")))(readExecutionContext)

      whenReady(futureUser, Timeout(3.seconds)) { result =>
        Then("result should be an instance of User")
        val user = result.get
        user shouldBe a[User]

        And("the user first name must John")
        user.firstName.get shouldBe "John"

        And("the user last name must Doe")
        user.lastName.get shouldBe "Doe"

        And("the user email should be verified")
        user.emailVerified shouldBe true

        And("the user roles should be instance of Role")
        user.roles.map(role => {
          role shouldBe a[Role]
        })

        And("the user has roles ROLE_ADMIN, ROLE_MODERATOR AND ROLE_CITIZEN")
        user.roles shouldBe Seq(Role.RoleAdmin, Role.RoleModerator, Role.RoleCitizen)

        And("the user gender must be Male")
        user.profile.get.gender.get shouldBe Gender.Male

        And("the user gender must be Male")
        user.profile.get.gender.get shouldBe Gender.Male

        And("the user twitter id must be @twitterid")
        user.profile.get.twitterId.get shouldBe "@twitterid"

        And("the user karmaLevel must be 2")
        user.profile.get.karmaLevel.get shouldBe 2

        And("the user newsletter option must be true")
        user.profile.get.optInNewsletter shouldBe true

        And("the user socio professional category must be farmers")
        user.profile.get.socioProfessionalCategory.get shouldBe SocioProfessionalCategory.Farmers

        And("the user register question id must be 'the question'")
        user.profile.get.registerQuestionId.get.value shouldBe "the question"

        And("the user opt in partner must be true")
        user.profile.get.optInPartner.get shouldBe true

        And("the user anonymous participation must be true")
        user.anonymousParticipation should be(true)

      }
    }

    Scenario("persist multiple users and get by multiple ids") {
      Given("""
          |Two users "Jenna Doo" and "Jane Dee"
        """.stripMargin)
      When("""
          |I persist two users "Jenna Doo" and "Jane Dee"
        """.stripMargin)
      And("I get the persisted users by ids")
      val futureUsers: Future[Seq[User]] =
        for {
          _        <- persistentUserService.persist(jennaDoo)
          _        <- persistentUserService.persist(janeDee)
          allUsers <- persistentUserService.findAllByUserIds(Seq(jennaDoo.userId, janeDee.userId))
        } yield allUsers

      whenReady(futureUsers, Timeout(3.seconds)) { results =>
        Then("results should be an instance of Seq[User]")
        results shouldBe a[Seq[_]]
        results.size should be > 0
        results.head shouldBe a[User]

        And("""results should have at least "Jenna Doo" and "Jane Dee"""")
        results.exists(_.userId.value == jennaDoo.userId.value) shouldBe true
        results.exists(_.userId.value == janeDee.userId.value) shouldBe true
      }
    }

    Scenario("Persist a user with mailing params") {
      Given("""a user John Mailing with values:
              |    - email: johnmailing@example.com
              |    - isHardBounce: true
              |    - lastMailingError: Some(error = "my error", date = "2018-12-12T12:30:40Z[UTC]")
            """.stripMargin)
      When("I persist John Mailing")
      And("I get the persisted user")
      val futureUser: Future[Option[User]] = persistentUserService
        .persist(johnMailing)
        .flatMap(_ => persistentUserService.get(johnMailing.userId))(readExecutionContext)

      whenReady(futureUser, Timeout(3.seconds)) { result =>
        Then("result should be an instance of User")
        val user = result.get
        user shouldBe a[User]

        And("the user hard bounce should be true")
        user.isHardBounce shouldBe true

        And("the user last mailing error should be my error at 12-12-2018T12:30:40")
        user.lastMailingError.get.error shouldBe "my error"
        user.lastMailingError.get.date
          .withZoneSameInstant(ZoneId.of("Europe/Paris"))
          .toString shouldBe "2018-12-12T12:30:40+01:00[Europe/Paris]"

        And("the user newsletter option must be true")
        user.profile.get.optInNewsletter shouldBe true
      }
    }

  }

  Feature("The app can persist a user") {
    info("As a programmer")
    info("I want to be able to find a userId by email")

    Scenario("Retrieve a userId from an existing email") {
      Given("""a persisted user John Doe with values:
              |    - firstName: John
              |    - lastName: Doe
              |    - email: doe@example.com
              |    - emailVerified: true
              |    - roles: ROLE_ADMIN, ROLE_MODERATOR, ROLE_CITIZEN
            """.stripMargin)
      When("I search the userId by email doe@example.com")
      val futureUserId: Future[Option[UserId]] = persistentUserService
        .findUserIdByEmail("doe@example.com")

      whenReady(futureUserId, Timeout(3.seconds)) { result =>
        Then("result should be an instance of UserId")
        val userId = result.get
        userId shouldBe a[UserId]
      }
    }

    Scenario("Retrieve a userId by nonexistent email") {
      Given("a nonexistent email fake@example.com")
      When("I search the userId from email fake@example.com")
      val futureUserId: Future[Option[UserId]] = persistentUserService
        .findUserIdByEmail("fake@example.com")

      whenReady(futureUserId, Timeout(3.seconds)) { result =>
        Then("result should be None")
        result shouldBe None
      }
    }
  }

  Feature("checking user password (findByEmailAndPassword)") {
    Scenario("return valid user") {

      whenReady(persistentUserService.persist(passwordUser), Timeout(3.seconds)) { user =>
        whenReady(persistentUserService.findByEmailAndPassword(user.email, "123456"), Timeout(3.seconds)) { maybeUser =>
          maybeUser.isDefined should be(true)
          maybeUser.map(_.userId) should be(Some(user.userId))
        }

      }
    }
    Scenario("return empty on invalid email") {
      whenReady(
        persistentUserService.findByEmailAndPassword("not-a-known-password@fake.org", "123456"),
        Timeout(3.seconds)
      ) { maybeUser =>
        maybeUser should be(None)
      }

    }
    Scenario("return empty if password is null") {
      whenReady(persistentUserService.persist(socialUser), Timeout(3.seconds)) { user =>
        whenReady(persistentUserService.findByEmailAndPassword(user.email, "123456"), Timeout(3.seconds)) { maybeUser =>
          maybeUser should be(None)
        }

      }
    }
  }

  Feature("find user by id and type") {
    Scenario("return a personality user") {
      whenReady(persistentUserService.persist(personalityUser), Timeout(3.seconds)) { user =>
        whenReady(persistentUserService.findByUserIdAndUserType(user.userId, UserType.UserTypePersonality)) {
          maybeUser =>
            maybeUser.map(_.userId) should contain(UserId("personality"))
        }
      }
    }
  }

  Feature("update an organisation") {
    Scenario("Update the organisation name") {
      whenReady(persistentUserService.persist(userOrganisationDGSE), Timeout(3.seconds)) { organisation =>
        organisation.organisationName should be(Some("Direction Générale de la Sécurité Extérieure"))
        whenReady(
          persistentUserService.modifyOrganisation(userOrganisationDGSE.copy(organisationName = Some("DGSE Updated"))),
          Timeout(3.seconds)
        ) { organisationUpdated =>
          organisationUpdated shouldBe a[Either[_, User]]
          organisationUpdated.isRight shouldBe true
          organisationUpdated.exists(_.organisationName.contains("DGSE Updated")) shouldBe true
        }
      }
    }

    Scenario("Update the organisation name and the email") {
      whenReady(persistentUserService.persist(userOrganisationFSB), Timeout(3.seconds)) { organisation =>
        organisation.organisationName shouldBe Some("Federal Security Service")
        organisation.email shouldBe "fsb@secret-agency.com"
        whenReady(
          persistentUserService.modifyOrganisation(
            userOrganisationDGSE.copy(organisationName = Some("FSB Updated"), email = "fsbupdated@secret-agency.com")
          ),
          Timeout(3.seconds)
        ) { organisationUpdated =>
          organisationUpdated shouldBe a[Either[_, User]]
          organisationUpdated.isRight shouldBe true
          organisationUpdated.exists(_.organisationName.contains("FSB Updated")) shouldBe true
          organisationUpdated.exists(_.email == "fsbupdated@secret-agency.com") shouldBe true
        }
      }
    }

    Scenario("Fail organisation update") {
      whenReady(
        persistentUserService.modifyOrganisation(userOrganisationCSIS.copy(organisationName = Some("CSIS Updated"))),
        Timeout(3.seconds)
      ) { organisationFailUpdate =>
        organisationFailUpdate shouldBe a[Either[UpdateFailed, _]]
        organisationFailUpdate.isLeft shouldBe true
        organisationFailUpdate.left.getOrElse(throw new IllegalStateException("unexpected state")) shouldBe UpdateFailed()
      }
    }
  }

  Feature("find users by organisation") {
    Scenario("find all organisations") {
      val futureOrganisations = for {
        cia <- persistentUserService.persist(userOrganisationCIA)
        fbi <- persistentUserService.persist(userOrganisationFBI)
      } yield (cia, fbi)

      whenReady(futureOrganisations, Timeout(3.seconds)) { _ =>
        whenReady(persistentUserService.findAllOrganisations(), Timeout(3.seconds)) { organisations =>
          organisations.size should be(4)
          organisations.exists(_.userId == UserId("FBI")) should be(true)
          organisations.exists(_.userId == UserId("CIA")) should be(true)
        }
      }
    }

    Scenario("find organisations with params") {
      whenReady(
        persistentUserService
          .findOrganisations(start = 0, end = Some(2), sort = Some("organisation_name"), order = Some(Order.asc), None)
      ) { organisations =>
        organisations.size should be(2)
        organisations.head.userId shouldBe UserId("CIA")
        organisations.last.userId shouldBe UserId("DGSE")
      }
    }

    Scenario(("find organisation with organisation name filter")) {
      whenReady(persistentUserService.findOrganisations(start = 0, end = None, sort = None, order = None, Some("CIA"))) {
        organisations =>
          organisations.size should be(1)
          organisations.head.userId should be(UserId("CIA"))
      }
    }

    Scenario(("find organisation with organisation name filter - check case insensitivity")) {
      whenReady(persistentUserService.findOrganisations(start = 0, end = None, sort = None, order = None, Some("cia"))) {
        organisations =>
          organisations.size should be(1)
          organisations.head.userId should be(UserId("CIA"))
      }
    }
  }

  Feature("find moderators") {
    Scenario("find all moderators") {
      whenReady(
        persistentUserService.adminFindUsers(
          start = 0,
          limit = None,
          sort = None,
          order = None,
          email = None,
          firstName = None,
          lastName = None,
          maybeRole = Some(Role.RoleModerator),
          maybeUserType = None
        ),
        Timeout(3.seconds)
      ) { moderators =>
        moderators.size should be(6)
      }
    }

    Scenario("find moderators with email filter") {
      whenReady(
        persistentUserService
          .adminFindUsers(
            start = 0,
            limit = None,
            sort = None,
            order = None,
            email = Some("doe@example.com"),
            firstName = None,
            lastName = None,
            maybeRole = Some(Role.RoleModerator),
            maybeUserType = None
          ),
        Timeout(3.seconds)
      ) { moderators =>
        moderators.size should be(1)
      }
    }
  }

  Feature("register mailing data") {
    Scenario("update opt in newsletter with user id") {
      Given("a user with a userId(7)")
      When("I update opt in newsletter to true")
      val futureMaybeUserWithTrueValue: Future[Option[User]] = futureJohnMailing2.flatMap { user =>
        persistentUserService
          .updateOptInNewsletter(user.userId, optInNewsletter = true)
          .flatMap(_ => persistentUserService.get(user.userId))(readExecutionContext)
      }
      Then("user opt in newsletter is true")
      whenReady(futureMaybeUserWithTrueValue, Timeout(3.seconds)) { maybeUser =>
        maybeUser.get.profile.get.optInNewsletter shouldBe true
      }

      When("I update opt in newsletter to false")
      val futureMaybeUserWithFalseValue: Future[Option[User]] = futureJohnMailing2.flatMap { user =>
        persistentUserService
          .updateOptInNewsletter(user.userId, optInNewsletter = false)
          .flatMap(_ => persistentUserService.get(user.userId))(readExecutionContext)
      }
      Then("user opt in newsletter is false")
      whenReady(futureMaybeUserWithFalseValue, Timeout(3.seconds)) { maybeUser =>
        maybeUser.get.profile.get.optInNewsletter shouldBe false
      }
    }

    Scenario("update opt in newsletter with user email") {
      Given("a user with an email johnmailing2@example.com")
      When("I update opt in newsletter to true")
      val futureMaybeUserWithTrueValue: Future[Option[User]] = futureJohnMailing2.flatMap { user =>
        persistentUserService
          .updateOptInNewsletter("johnmailing2@example.com", optInNewsletter = true)
          .flatMap(_ => persistentUserService.get(user.userId))(readExecutionContext)
      }
      Then("user opt in newsletter is true")
      whenReady(futureMaybeUserWithTrueValue, Timeout(3.seconds)) { maybeUser =>
        maybeUser.get.profile.get.optInNewsletter shouldBe true
      }

      When("I update opt in newsletter to false")
      val futureMaybeUserWithFalseValue: Future[Option[User]] = futureJohnMailing2.flatMap { user =>
        persistentUserService
          .updateOptInNewsletter("johnmailing2@example.com", optInNewsletter = false)
          .flatMap(_ => persistentUserService.get(user.userId))(readExecutionContext)
      }
      Then("user opt in newsletter is true")
      whenReady(futureMaybeUserWithFalseValue, Timeout(3.seconds)) { maybeUser =>
        maybeUser.get.profile.get.optInNewsletter shouldBe false
      }
    }
    Scenario("update hard bounce with user id") {
      Given("a user with a userId(7)")
      When("I update hard bounce to true")
      val futureMaybeUserWithTrueValue: Future[Option[User]] = futureJohnMailing2.flatMap { user =>
        persistentUserService
          .updateIsHardBounce(user.userId, isHardBounce = true)
          .flatMap(_ => persistentUserService.get(user.userId))(readExecutionContext)
      }
      Then("user hard bounce is true")
      whenReady(futureMaybeUserWithTrueValue, Timeout(3.seconds)) { maybeUser =>
        maybeUser.get.isHardBounce shouldBe true
      }

      When("I update hard bounce to false")
      val futureMaybeUserWithFalseValue: Future[Option[User]] = futureJohnMailing2.flatMap { user =>
        persistentUserService
          .updateIsHardBounce(user.userId, isHardBounce = false)
          .flatMap(_ => persistentUserService.get(user.userId))(readExecutionContext)
      }
      Then("user hard bounce is false")
      whenReady(futureMaybeUserWithFalseValue, Timeout(3.seconds)) { maybeUser =>
        maybeUser.get.isHardBounce shouldBe false
      }
    }
    Scenario("update hard bounce with user email") {
      Given("a user with an email johnmailing2@example.com")
      When("I update hard bounce to true")
      val futureMaybeUserWithTrueValue: Future[Option[User]] = futureJohnMailing2.flatMap { user =>
        persistentUserService
          .updateIsHardBounce("johnmailing2@example.com", isHardBounce = true)
          .flatMap(_ => persistentUserService.get(user.userId))(readExecutionContext)
      }
      Then("user hard bounce is true")
      whenReady(futureMaybeUserWithTrueValue, Timeout(3.seconds)) { maybeUser =>
        maybeUser.get.isHardBounce shouldBe true
      }

      When("I update hard bounce to false")
      val futureMaybeUserWithFalseValue: Future[Option[User]] = futureJohnMailing2.flatMap { user =>
        persistentUserService
          .updateIsHardBounce("johnmailing2@example.com", isHardBounce = false)
          .flatMap(_ => persistentUserService.get(user.userId))(readExecutionContext)
      }
      Then("user hard bounce is false")
      whenReady(futureMaybeUserWithFalseValue, Timeout(3.seconds)) { maybeUser =>
        maybeUser.get.isHardBounce shouldBe false
      }
    }
    Scenario("update mailing error with user id") {
      Given("a user with a userId(7)")
      And("a mailing error my_error at 2018-01-02 12:03:30")
      val mailingError: MailingErrorLog =
        MailingErrorLog(error = "my_error", date = ZonedDateTime.parse("2018-01-02T12:03:30+01:00[Europe/Paris]"))
      When("I update mailing error to my_error at 2018-01-02 12:03:30")
      val futureMaybeUserWithValue: Future[Option[User]] = futureJohnMailing2.flatMap { user =>
        persistentUserService
          .updateLastMailingError(user.userId, Some(mailingError))
          .flatMap(_ => persistentUserService.get(user.userId))(readExecutionContext)
      }
      Then("user mailing error should be my_error at 2018-01-02 12:03:30")
      whenReady(futureMaybeUserWithValue, Timeout(3.seconds)) { maybeUser =>
        maybeUser.get.lastMailingError.get.error shouldBe mailingError.error
        maybeUser.get.lastMailingError.get.date.toEpochSecond shouldBe mailingError.date.toEpochSecond
      }

      When("I update mailing error to None")
      val futureMaybeUserWithNoneValue: Future[Option[User]] = futureJohnMailing2.flatMap { user =>
        persistentUserService
          .updateLastMailingError(user.userId, None)
          .flatMap(_ => persistentUserService.get(user.userId))(readExecutionContext)
      }
      Then("user mailing error is None")
      whenReady(futureMaybeUserWithNoneValue, Timeout(3.seconds)) { maybeUser =>
        maybeUser.get.lastMailingError shouldBe None
      }
    }
    Scenario("update mailing error with user email") {
      Given("a user with an email johnmailing2@example.com")
      And("a mailing error my_error at 2018-01-02 12:03:30")
      val mailingError: MailingErrorLog =
        MailingErrorLog(error = "my_error", date = ZonedDateTime.parse("2018-01-02T12:03:30+01:00[Europe/Paris]"))
      When("I update mailing error to my_error at 2018-01-02 12:03:30")
      val futureMaybeUserWithValue: Future[Option[User]] = futureJohnMailing2.flatMap { user =>
        persistentUserService
          .updateLastMailingError("johnmailing2@example.com", Some(mailingError))
          .flatMap(_ => persistentUserService.get(user.userId))(readExecutionContext)
      }
      Then("user mailing error should be my_error at 2018-01-02 12:03:30")
      whenReady(futureMaybeUserWithValue, Timeout(3.seconds)) { maybeUser =>
        maybeUser.get.lastMailingError.get.error shouldBe mailingError.error
        maybeUser.get.lastMailingError.get.date.toEpochSecond shouldBe mailingError.date.toEpochSecond
      }

      When("I update mailing error to None")
      val futureMaybeUserWithNoneValue: Future[Option[User]] = futureJohnMailing2.flatMap { user =>
        persistentUserService
          .updateLastMailingError("johnmailing2@example.com", None)
          .flatMap(_ => persistentUserService.get(user.userId))(readExecutionContext)
      }
      Then("user mailing error is None")
      whenReady(futureMaybeUserWithNoneValue, Timeout(3.seconds)) { maybeUser =>
        maybeUser.get.lastMailingError shouldBe None
      }
    }

  }

  Feature("update existing social user") {
    Scenario("non existing user") {
      val futureUpdate: Future[Boolean] = for {
        user <- persistentUserService.persist(
          socialUser.copy(userId = UserId("new social"), email = "1" + socialUser.email)
        )
        update <- persistentUserService.updateSocialUser(user)
      } yield update
      whenReady(futureUpdate, Timeout(3.seconds)) { update =>
        update should be(true)
      }
    }

    Scenario("existing user updates") {
      val futureUpdate: Future[Boolean] = for {
        user <- persistentUserService.persist(
          socialUser.copy(userId = UserId("new new social"), email = "2" + socialUser.email)
        )
        update <- persistentUserService.updateSocialUser(
          user.copy(
            email = "to_be@ignored.fake",
            firstName = Some("new firstName"),
            emailVerified = false,
            hashedPassword = Some("passpass"),
            profile = socialUser.profile.map(_.copy(dateOfBirth = Some(LocalDate.parse("2000-01-01"))))
          )
        )
      } yield update
      whenReady(futureUpdate, Timeout(3.seconds)) { update =>
        update should be(true)
        whenReady(persistentUserService.get(UserId("new new social")), Timeout(3.seconds)) { maybeUpdatedUser =>
          maybeUpdatedUser.isDefined should be(true)
          val updatedUser = maybeUpdatedUser.get
          updatedUser.email should be("2" + socialUser.email)
          updatedUser.firstName should be(Some("new firstName"))
          updatedUser.hashedPassword should be(Some("passpass"))
          updatedUser.emailVerified should be(false)
          updatedUser.profile.flatMap(_.dateOfBirth) should be(Some(LocalDate.parse("2000-01-01")))
        }
      }
    }
  }

  Feature("update a user") {
    Scenario("Update the user properties") {
      whenReady(persistentUserService.persist(updateUser), Timeout(3.seconds)) { user =>
        user.userId.value should be("update-user-1")
        whenReady(
          persistentUserService.updateUser(
            updateUser.copy(
              firstName = Some("FooFoo"),
              lastName = Some("BarBar"),
              anonymousParticipation = true,
              profile = Some(
                updateUser.profile.get.copy(
                  gender = Some(Gender.Female),
                  socioProfessionalCategory = Some(SocioProfessionalCategory.Employee)
                )
              )
            )
          ),
          Timeout(3.seconds)
        ) { userUpdated =>
          userUpdated shouldBe a[User]
          userUpdated.firstName shouldBe Some("FooFoo")
          userUpdated.lastName shouldBe Some("BarBar")
          userUpdated.anonymousParticipation shouldBe true
          userUpdated.profile.get.gender shouldBe Some(Gender.Female)
          userUpdated.profile.get.socioProfessionalCategory shouldBe Some(SocioProfessionalCategory.Employee)
        }
      }
    }

    Scenario("Dont update not allowed field") {
      whenReady(
        persistentUserService.updateUser(updateUser.copy(hashedPassword = Some("ABABABABABAB"))),
        Timeout(3.seconds)
      ) { _ =>
        whenReady(persistentUserService.get(updateUser.userId), Timeout(3.seconds)) { userUpdated =>
          userUpdated.get shouldBe a[User]
          userUpdated.get.hashedPassword shouldBe Some("ZAEAZE232323SFSSDF")
        }
      }
    }
  }

  Feature("find user by email and password") {

    Scenario("user with a hashed password") {
      val jennaDooWithHashedPassword =
        jennaDoo.copy(
          userId = UserId("jenna-hashed-password"),
          email = "jennaDoowithhasedpassword@example.com",
          hashedPassword = jennaDoo.hashedPassword.map(_.bcrypt)
        )
      val futureUserWithPassword: Future[Option[User]] = for {
        user         <- persistentUserService.persist(jennaDooWithHashedPassword)
        userResponse <- persistentUserService.findByUserIdAndPassword(user.userId, jennaDoo.hashedPassword)
      } yield userResponse

      whenReady(futureUserWithPassword, Timeout(3.seconds)) { user =>
        user shouldNot be(None)
        user.flatMap(_.firstName) should be(jennaDooWithHashedPassword.firstName)
        user.map(_.email) should be(Some(jennaDooWithHashedPassword.email))
        user.flatMap(_.hashedPassword) should be(jennaDooWithHashedPassword.hashedPassword)
      }
    }

    Scenario("user without password") {
      val jennaDooWithoutPassword =
        jennaDoo.copy(
          userId = UserId("jenna-without-password"),
          email = "jennaDoowithoutpassword@example.com",
          hashedPassword = None
        )
      val futureUserWithPassword: Future[Option[User]] = for {
        user         <- persistentUserService.persist(jennaDooWithoutPassword)
        userResponse <- persistentUserService.findByUserIdAndPassword(user.userId, None)
      } yield userResponse

      whenReady(futureUserWithPassword, Timeout(3.seconds)) { user =>
        user shouldNot be(None)
        user.flatMap(_.firstName) should be(jennaDooWithoutPassword.firstName)
        user.map(_.email) should be(Some(jennaDooWithoutPassword.email))
      }
    }
  }

  Feature("follow user") {
    Scenario("follow user") {
      val johnDoe2 = johnDoe.copy(
        userId = UserId("johnDoe2"),
        email = "doe2@example.com",
        firstName = Some("John"),
        lastName = Some("Doe2")
      )

      val jennaDoo2 =
        jennaDoo.copy(userId = UserId("jennaDoo2"), email = "jennaDoo2@user.com", publicProfile = true)

      val futureFollowUser: Future[Seq[String]] = for {
        user          <- persistentUserService.persist(johnDoe2)
        userToFollow  <- persistentUserService.persist(jennaDoo2)
        _             <- persistentUserService.followUser(userToFollow.userId, user.userId)
        followedUsers <- persistentUserService.getFollowedUsers(user.userId)
      } yield followedUsers

      whenReady(futureFollowUser, Timeout(3.seconds)) { userIds =>
        userIds.length shouldBe 1
        userIds.head shouldBe jennaDoo2.userId.value
      }
    }
  }

  Feature("unfollow user") {
    Scenario("unfollow user") {
      val johnDoe3 = johnDoe.copy(
        userId = UserId("johnDoe3"),
        email = "doe3@example.com",
        firstName = Some("John"),
        lastName = Some("Doe3")
      )

      val jennaDoo3 =
        jennaDoo.copy(userId = UserId("jennaDoo3"), email = "jennaDoo3@user.com", publicProfile = true)

      val futureFollowAndUnfollowUser: Future[(Seq[String], Seq[String])] = for {
        user                        <- persistentUserService.persist(johnDoe3)
        userToFollow                <- persistentUserService.persist(jennaDoo3)
        _                           <- persistentUserService.followUser(userToFollow.userId, user.userId)
        followedUsersBeforeUnfollow <- persistentUserService.getFollowedUsers(user.userId)
        _                           <- persistentUserService.unfollowUser(userToFollow.userId, user.userId)
        followedUsersAfterUnfollow  <- persistentUserService.getFollowedUsers(user.userId)
      } yield (followedUsersBeforeUnfollow, followedUsersAfterUnfollow)

      whenReady(futureFollowAndUnfollowUser, Timeout(3.seconds)) {
        case (userFollowedIds, userUnfollowedIds) =>
          userFollowedIds.length shouldBe 1
          userFollowedIds.head shouldBe jennaDoo3.userId.value
          userUnfollowedIds.isEmpty shouldBe true
      }
    }
  }

  Feature("available questions") {
    Scenario("persist retrieve and update available questions") {

      var user =
        johnDoe.copy(
          userId = UserId("available-questions"),
          email = "available-questions@make.org",
          availableQuestions = Seq(QuestionId("my-question"))
        )

      whenReady(
        persistentUserService.persist(user).flatMap(u => persistentUserService.get(u.userId)),
        Timeout(3.seconds)
      ) { maybeUser =>
        maybeUser.toSeq.flatMap(_.availableQuestions) should be(Seq(QuestionId("my-question")))
        user = maybeUser.get
      }

      val modifiedUser = user.copy(availableQuestions = Seq(QuestionId("first"), QuestionId("second")))

      whenReady(
        persistentUserService.updateUser(modifiedUser).flatMap(u => persistentUserService.get(u.userId)),
        Timeout(3.seconds)
      ) { maybeUser =>
        maybeUser.toSeq.flatMap(_.availableQuestions) should be(Seq(QuestionId("first"), QuestionId("second")))
      }

    }
  }

  Feature("get users without operation creation") {
    Scenario("get users without operation creation") {

      val usersWithoutRegisterQuestion: Future[Seq[User]] = for {
        _    <- persistentUserService.persist(noRegisterQuestionUser)
        user <- persistentUserService.findUsersWithoutRegisterQuestion
      } yield user

      whenReady(usersWithoutRegisterQuestion, Timeout(3.seconds)) { users =>
        users.map(_.userId).contains(noRegisterQuestionUser.userId) should be(true)
      }
    }
  }

  Feature("update reconnect token") {
    Scenario("update reconnect token") {
      val user = johnDoe.copy(userId = UserId("reconnect-token"), email = "reconnect-token@make.org")

      val userReconnectTokenUpdate: Future[Boolean] = for {
        _      <- persistentUserService.persist(user)
        result <- persistentUserService.updateReconnectToken(user.userId, "reconnectToken", DateHelper.now())
      } yield result

      whenReady(userReconnectTokenUpdate, Timeout(3.seconds)) { result =>
        result should be(true)
      }
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    futureJohnMailing2 =
      persistentUserService.persist(johnDoe.copy(email = "johnmailing2@example.com", userId = UserId("7")))
  }
}
