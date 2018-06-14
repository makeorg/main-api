package org.make.api.user

import java.time.{LocalDate, ZoneId, ZonedDateTime}

import com.github.t3hnar.bcrypt._
import org.make.api.DatabaseTest
import org.make.api.user.DefaultPersistentUserServiceComponent.UpdateFailed
import org.make.core.profile.{Gender, Profile}
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
    twitterId = Some("@twitterid"),
    facebookId = Some("facebookid"),
    googleId = Some("googleId"),
    gender = Some(Gender.Male),
    genderName = Some("other"),
    postalCode = Some("93"),
    karmaLevel = Some(2),
    locale = Some("FR_FR")
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
    roles = Seq(Role.RoleAdmin, Role.RoleCitizen),
    country = "FR",
    language = "fr",
    profile = Some(profile)
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
    country = "FR",
    language = "fr",
    profile = None
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
    roles = Seq(Role.RoleAdmin),
    country = "FR",
    language = "fr",
    profile = None
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
    roles = Seq(Role.RoleAdmin),
    country = "FR",
    language = "fr",
    profile = None
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
    roles = Seq(Role.RoleAdmin),
    country = "FR",
    language = "fr",
    profile = None
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
    country = "FR",
    language = "fr",
    profile = None,
    organisationName = Some("Direction Générale de la Sécurité Extérieure")
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
    country = "FR",
    language = "fr",
    profile = None,
    organisationName = Some("Canadian Security Intelligence Service")
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
    country = "RU",
    language = "ru",
    profile = None,
    organisationName = Some("Federal Security Service")
  )

  val johnMailing: User = johnDoe.copy(
    email = "johnmailing@example.com",
    userId = UserId("6"),
    isHardBounce = true,
    lastMailingError =
      Some(MailingErrorLog(error = "my error", date = ZonedDateTime.parse("2018-12-12T12:30:40+01:00[Europe/Paris]")))
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
    country = "US",
    language = "en",
    profile = None,
    organisationName = Some("Central Intelligence Agency - CIA")
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
    country = "US",
    language = "en",
    profile = None,
    organisationName = Some("Federal Bureau of Investigation - FBI")
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
    country = "UK",
    language = "en",
    profile = None,
    organisationName = Some("Military Intelligence, Section 5 - MI5")
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
          |    - roles: ROLE_ADMIN, ROLE_CITIZEN
        """.stripMargin)
      And("""a John Doe profile with values:
          |    - gender: Male
          |    - twitterId: @twitterid
          |    - karmaLevel: 2
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

        And("the user has roles ROLE_ADMIN AND ROLE_CITIZEN")
        user.roles shouldBe Seq(Role.RoleAdmin, Role.RoleCitizen)

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
              |    - roles: ROLE_ADMIN, ROLE_CITIZEN
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

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    futureJohnMailing2 =
      persistentUserService.persist(johnDoe.copy(email = "johnmailing2@example.com", userId = UserId("7")))
  }
}
