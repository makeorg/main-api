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
import org.make.api.DatabaseTest
import org.make.api.user.DefaultPersistentUserServiceComponent.UpdateFailed
import org.make.core.DateHelper
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.user.{MailingErrorLog, Role, User, UserId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentUserServiceIT extends DatabaseTest with DefaultPersistentUserServiceComponent {

  override protected val cockroachExposedPort: Int = 40002

  val before: ZonedDateTime = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")

  val profile = Profile(
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

  val johnDoe = User(
    userId = UserId("1"),
    email = "doe@example.com",
    firstName = Some("John"),
    lastName = Some("Doe"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    enabled = true,
    emailVerified = true,
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleAdmin, Role.RoleModerator, Role.RoleCitizen),
    country = Country("FR"),
    language = Language("fr"),
    profile = Some(profile),
    availableQuestions = Seq.empty
  )

  val jennaDoo = User(
    userId = UserId("2"),
    email = "jennaDoo@example.com",
    firstName = Some("Jenna"),
    lastName = Some("Doo"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    enabled = true,
    emailVerified = true,
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleCitizen),
    country = Country("FR"),
    language = Language("fr"),
    profile = None,
    availableQuestions = Seq.empty
  )

  val janeDee = User(
    userId = UserId("3"),
    email = "janeDee@example.com",
    firstName = Some("Jane"),
    lastName = Some("Dee"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    enabled = true,
    emailVerified = true,
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleAdmin, Role.RoleModerator),
    country = Country("FR"),
    language = Language("fr"),
    profile = None,
    availableQuestions = Seq.empty
  )

  val passwordUser = User(
    userId = UserId("4"),
    email = "password@example.com",
    firstName = Some("user-with"),
    lastName = Some("Password"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("123456".bcrypt),
    enabled = true,
    emailVerified = true,
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleAdmin, Role.RoleModerator),
    country = Country("FR"),
    language = Language("fr"),
    profile = None,
    availableQuestions = Seq.empty
  )

  val socialUser = User(
    userId = UserId("5"),
    email = "social@example.com",
    firstName = Some("Social"),
    lastName = Some("User"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = None,
    enabled = true,
    emailVerified = true,
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleAdmin, Role.RoleModerator),
    country = Country("FR"),
    language = Language("fr"),
    profile = None,
    availableQuestions = Seq.empty
  )

  val userOrganisationDGSE = User(
    userId = UserId("DGSE"),
    email = "dgse@secret-agency.com",
    firstName = None,
    lastName = None,
    lastIp = Some("-1.-1.-1.-1"),
    hashedPassword = None,
    enabled = true,
    emailVerified = true,
    isOrganisation = true,
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleActor),
    country = Country("FR"),
    language = Language("fr"),
    profile = None,
    organisationName = Some("Direction Générale de la Sécurité Extérieure"),
    availableQuestions = Seq.empty
  )

  val userOrganisationCSIS = User(
    userId = UserId("CSIS"),
    email = "csis@secret-agency.com",
    firstName = None,
    lastName = None,
    lastIp = Some("-1.-1.-1.-1"),
    hashedPassword = None,
    enabled = true,
    emailVerified = true,
    isOrganisation = true,
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleActor),
    country = Country("FR"),
    language = Language("fr"),
    profile = None,
    organisationName = Some("Canadian Security Intelligence Service"),
    availableQuestions = Seq.empty
  )

  val userOrganisationFSB = User(
    userId = UserId("FSB"),
    email = "fsb@secret-agency.com",
    firstName = None,
    lastName = None,
    lastIp = Some("-1.-1.-1.-1"),
    hashedPassword = None,
    enabled = true,
    emailVerified = true,
    isOrganisation = true,
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleActor),
    country = Country("RU"),
    language = Language("ru"),
    profile = None,
    organisationName = Some("Federal Security Service"),
    availableQuestions = Seq.empty
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
    profile = Some(profile.copy(registerQuestionId = None))
  )

  val userOrganisationCIA = User(
    userId = UserId("CIA"),
    email = "cia@secret-agency.com",
    firstName = None,
    lastName = None,
    lastIp = Some("-1.-1.-1.-1"),
    hashedPassword = None,
    enabled = true,
    emailVerified = true,
    isOrganisation = true,
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleActor),
    country = Country("US"),
    language = Language("en"),
    profile = None,
    organisationName = Some("Central Intelligence Agency - CIA"),
    availableQuestions = Seq.empty
  )

  val userOrganisationFBI = User(
    userId = UserId("FBI"),
    email = "fbi@secret-agency.com",
    firstName = None,
    lastName = None,
    lastIp = Some("-1.-1.-1.-1"),
    hashedPassword = None,
    enabled = true,
    emailVerified = true,
    isOrganisation = true,
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleActor),
    country = Country("US"),
    language = Language("en"),
    profile = None,
    organisationName = Some("Federal Bureau of Investigation - FBI"),
    availableQuestions = Seq.empty
  )

  val userOrganisationMI5 = User(
    userId = UserId("MI5"),
    email = "mi5@secret-agency.com",
    firstName = None,
    lastName = None,
    lastIp = Some("-1.-1.-1.-1"),
    hashedPassword = None,
    enabled = true,
    emailVerified = true,
    isOrganisation = true,
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleActor),
    country = Country("UK"),
    language = Language("en"),
    profile = None,
    organisationName = Some("Military Intelligence, Section 5 - MI5"),
    availableQuestions = Seq.empty
  )

  val updateUser = User(
    userId = UserId("update-user-1"),
    email = "foo@example.com",
    firstName = Some("John"),
    lastName = Some("Doe"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    enabled = true,
    emailVerified = true,
    lastConnection = before,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(before),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleAdmin, Role.RoleModerator, Role.RoleCitizen),
    country = Country("FR"),
    language = Language("fr"),
    profile = Some(profile),
    createdAt = Some(DateHelper.now()),
    availableQuestions = Seq.empty
  )

  var futureJohnMailing2: Future[User] = Future.failed(new IllegalStateException("I am no ready!!!!"))

  feature("The app can persist and retrieve users") {
    info("As a programmer")
    info("I want to be able to persist a user")

    scenario("Persist a user and get the persisted user") {
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

      }
    }

    scenario("persist multiple users and get by multiple ids") {
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

    scenario("Persist a user with mailing params") {
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

  feature("The app can persist a user") {
    info("As a programmer")
    info("I want to be able to find a userId by email")

    scenario("Retrieve a userId from an existing email") {
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

    scenario("Retrieve a userId by nonexistent email") {
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

  feature("checking user password (findByEmailAndPassword)") {
    scenario("return valid user") {

      whenReady(persistentUserService.persist(passwordUser), Timeout(3.seconds)) { user =>
        whenReady(persistentUserService.findByEmailAndPassword(user.email, "123456"), Timeout(3.seconds)) { maybeUser =>
          maybeUser.isDefined should be(true)
          maybeUser.map(_.userId) should be(Some(user.userId))
        }

      }
    }
    scenario("return empty on invalid email") {
      whenReady(
        persistentUserService.findByEmailAndPassword("not-a-known-password@fake.org", "123456"),
        Timeout(3.seconds)
      ) { maybeUser =>
        maybeUser should be(None)
      }

    }
    scenario("return empty if password is null") {
      whenReady(persistentUserService.persist(socialUser), Timeout(3.seconds)) { user =>
        whenReady(persistentUserService.findByEmailAndPassword(user.email, "123456"), Timeout(3.seconds)) { maybeUser =>
          maybeUser should be(None)
        }

      }
    }
  }

  feature("update an organisation") {
    scenario("Update the organisation name") {
      whenReady(persistentUserService.persist(userOrganisationDGSE), Timeout(3.seconds)) { organisation =>
        organisation.organisationName should be(Some("Direction Générale de la Sécurité Extérieure"))
        whenReady(
          persistentUserService.modify(userOrganisationDGSE.copy(organisationName = Some("DGSE Updated"))),
          Timeout(3.seconds)
        ) { organisationUpdated =>
          organisationUpdated shouldBe a[Either[_, User]]
          organisationUpdated.isRight shouldBe true
          organisationUpdated.exists(_.organisationName.contains("DGSE Updated")) shouldBe true
        }
      }
    }

    scenario("Update the organisation name and the email") {
      whenReady(persistentUserService.persist(userOrganisationFSB), Timeout(3.seconds)) { organisation =>
        organisation.organisationName shouldBe Some("Federal Security Service")
        organisation.email shouldBe "fsb@secret-agency.com"
        whenReady(
          persistentUserService.modify(
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

    scenario("Fail organisation update") {
      whenReady(
        persistentUserService.modify(userOrganisationCSIS.copy(organisationName = Some("CSIS Updated"))),
        Timeout(3.seconds)
      ) { organisationFailUpdate =>
        organisationFailUpdate shouldBe a[Either[UpdateFailed, _]]
        organisationFailUpdate.isLeft shouldBe true
        organisationFailUpdate.left.get shouldBe UpdateFailed()
      }
    }
  }

  feature("find users by organisation") {
    scenario("find all organisations") {
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

    scenario("find organisations with params") {
      whenReady(
        persistentUserService
          .findOrganisations(start = 0, end = Some(2), sort = Some("organisation_name"), order = Some("ASC"))
      ) { organisations =>
        organisations.size should be(2)
        organisations.head.userId shouldBe UserId("CIA")
        organisations.last.userId shouldBe UserId("DGSE")
      }
    }
  }

  feature("find moderators") {
    scenario("find all moderators") {
      whenReady(
        persistentUserService.adminFindUsers(0, None, None, None, None, None, Some(Role.RoleModerator)),
        Timeout(3.seconds)
      ) { moderators =>
        moderators.size should be(6)
      }
    }

    scenario("find moderators with email filter") {
      whenReady(
        persistentUserService
          .adminFindUsers(0, None, None, None, Some("doe@example.com"), None, None),
        Timeout(3.seconds)
      ) { moderators =>
        moderators.size should be(1)
      }
    }
  }

  feature("register mailing data") {
    scenario("update opt in newsletter with user id") {
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

    scenario("update opt in newsletter with user email") {
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
    scenario("update hard bounce with user id") {
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
    scenario("update hard bounce with user email") {
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
    scenario("update mailing error with user id") {
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
    scenario("update mailing error with user email") {
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

  feature("update existing social user") {
    scenario("non existing user") {
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

    scenario("existing user updates") {
      val futureUpdate: Future[Boolean] = for {
        user <- persistentUserService.persist(
          socialUser.copy(userId = UserId("new new social"), email = "2" + socialUser.email)
        )
        update <- persistentUserService.updateSocialUser(
          user.copy(email = "to_be@ignored.fake", firstName = Some("new firstName"))
        )
      } yield update
      whenReady(futureUpdate, Timeout(3.seconds)) { update =>
        update should be(true)
        whenReady(persistentUserService.get(UserId("new new social")), Timeout(3.seconds)) { maybeUpdatedUser =>
          maybeUpdatedUser.isDefined should be(true)
          val updatedUser = maybeUpdatedUser.get
          updatedUser.email should be("2" + socialUser.email)
          updatedUser.firstName should be(Some("new firstName"))
        }
      }
    }
  }

  feature("update a user") {
    scenario("Update the user properties") {
      whenReady(persistentUserService.persist(updateUser), Timeout(3.seconds)) { user =>
        user.userId.value should be("update-user-1")
        whenReady(
          persistentUserService.updateUser(
            updateUser.copy(
              firstName = Some("FooFoo"),
              lastName = Some("BarBar"),
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
          userUpdated.profile.get.gender shouldBe Some(Gender.Female)
          userUpdated.profile.get.socioProfessionalCategory shouldBe Some(SocioProfessionalCategory.Employee)
        }
      }
    }

    scenario("Dont update not allowed field") {
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

  feature("find user by email and password") {

    scenario("user with a hashed password") {
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

    scenario("user without password") {
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

  feature("follow user") {
    scenario("follow user") {
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

  feature("unfollow user") {
    scenario("unfollow user") {
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

  feature("available questions") {
    scenario("persist retrieve and update available questions") {

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

  feature("get users without operation creation") {
    scenario("get users without operation creation") {

      val usersWithoutRegisterQuestion: Future[Seq[User]] = for {
        _    <- persistentUserService.persist(noRegisterQuestionUser)
        user <- persistentUserService.findUsersWithoutRegisterQuestion
      } yield user

      whenReady(usersWithoutRegisterQuestion, Timeout(3.seconds)) { users =>
        users.map(_.userId).contains(noRegisterQuestionUser.userId) should be(true)
      }
    }
  }

  feature("update reconnect token") {
    scenario("update reconnect token") {
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

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    futureJohnMailing2 =
      persistentUserService.persist(johnDoe.copy(email = "johnmailing2@example.com", userId = UserId("7")))
  }
}
