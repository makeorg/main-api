package org.make.api.user

import java.time.{LocalDate, ZonedDateTime}

import org.make.api.DatabaseTest
import org.make.core.profile.{Gender, Profile}
import org.make.core.user.{Role, User, UserId}
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.Future

class PersistentUserServiceIT extends DatabaseTest with PersistentUserServiceComponent {

  override val persistentUserService: PersistentUserService = new PersistentUserService

  behavior.of("Users")

  val before: ZonedDateTime = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")

  it should "create a new user" in { () =>
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
    val futureUser: Future[Option[User]] = persistentUserService
      .persist(
        User(
          userId = UserId("1"),
          createdAt = before,
          updatedAt = before,
          email = "doe@example.com",
          firstName = Some("John"),
          lastName = Some("Doe"),
          lastIp = "0.0.0.0",
          hashedPassword = "ZAEAZE232323SFSSDF",
          salt = "MYSALT",
          enabled = true,
          verified = true,
          lastConnection = before,
          verificationToken = "VERIFTOKEN",
          roles = Seq(Role.RoleAdmin, Role.RoleCitizen),
          profile = Some(profile)
        )
      )
      .flatMap(_ => persistentUserService.get(UserId("1")))(readExecutionContext)

    whenReady(futureUser, timeout(Span(3, Seconds))) { result =>
      val user = result.get
      user shouldBe a[User]
      user.firstName shouldBe "John"
      user.lastName shouldBe "Doe"
      user.verified shouldBe true
      user.roles.map(role => {
        role shouldBe a[Role]
      })
    }
  }
}
