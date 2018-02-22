package org.make.api.user

import java.time.{LocalDate, ZonedDateTime}

import org.make.api.DatabaseTest
import org.make.core.profile.{Gender, Profile}
import org.make.core.user.{Role, User, UserId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import com.github.t3hnar.bcrypt._
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentUserServiceIT extends DatabaseTest with DefaultPersistentUserServiceComponent {

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
    verified = true,
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
    verified = true,
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
    verified = true,
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
    verified = true,
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
    verified = true,
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

  feature("The app can persist and retrieve users") {
    info("As a programmer")
    info("I want to be able to persist a user")

    scenario("Persist a user and get the persisted user") {
      Given("""a user John Doe with values:
          |    - firstName: John
          |    - lastName: Doe
          |    - email: doe@example.com
          |    - verified: true
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

        And("the user should be verified")
        user.verified shouldBe true

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
  }

  feature("The app can persist a user") {
    info("As a programmer")
    info("I want to be able to find a userId by email")

    scenario("Retrieve a userId from an existing email") {
      Given("""a persisted user John Doe with values:
              |    - firstName: John
              |    - lastName: Doe
              |    - email: doe@example.com
              |    - verified: true
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

}
