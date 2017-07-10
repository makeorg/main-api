package org.make.api.user

import java.time.{LocalDate, ZonedDateTime}

import org.make.api.DatabaseTest
import org.make.core.profile.{Gender, Profile}
import org.make.core.user.{Role, User, UserId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import scala.concurrent.duration._

import scala.concurrent.Future

class PersistentUserServiceIT extends DatabaseTest with PersistentUserServiceComponent {

  override val persistentUserService: PersistentUserService = new PersistentUserService

  val before: ZonedDateTime = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")

  feature("The app can persist a user") {
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
        departmentNumber = Some("93"),
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
        salt = Some("MYSALT"),
        enabled = true,
        verified = true,
        lastConnection = before,
        verificationToken = Some("VERIFTOKEN"),
        verificationTokenExpiresAt = Some(before),
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq(Role.RoleAdmin, Role.RoleCitizen),
        profile = Some(profile)
      )

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
      }
    }
  }
}
