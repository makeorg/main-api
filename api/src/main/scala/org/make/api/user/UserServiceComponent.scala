package org.make.api.user

import java.time.LocalDate
import java.util.UUID

import com.github.t3hnar.bcrypt._
import org.make.api.technical.{DateHelper, IdGeneratorComponent}
import org.make.core.profile.Profile
import org.make.core.user._

import scala.concurrent.Future

trait UserServiceComponent { this: IdGeneratorComponent with PersistentUserServiceComponent =>

  def userService: UserService

  class UserService() {

    def getUser(id: UserId): Future[Option[User]] = {
      persistentUserService.get(id)
    }

    def register(email: String,
                 firstName: String,
                 lastName: String,
                 password: String,
                 lastIp: String,
                 dateOfBirth: LocalDate): Future[User] = {

      val profile = Profile.parseProfile(dateOfBirth = Option(dateOfBirth))

      val salt: String = generateSalt

      val user = User(
        userId = idGenerator.nextUserId(),
        createdAt = DateHelper.now(),
        updatedAt = DateHelper.now(),
        email = email,
        firstName = firstName,
        lastName = lastName,
        lastIp = lastIp,
        hashedPassword = password.bcrypt(salt),
        salt = salt,
        enabled = true,
        verified = false,
        lastConnection = DateHelper.now(),
        verificationToken = UUID.randomUUID().toString(),
        roles = Seq(Role.RoleCitizen),
        profile = profile
      )

      persistentUserService.persist(user)
    }
  }
}
