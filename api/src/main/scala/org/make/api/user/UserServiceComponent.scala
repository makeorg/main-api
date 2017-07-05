package org.make.api.user

import java.time.LocalDate

import com.github.t3hnar.bcrypt._
import org.make.api.technical.{DateHelper, IdGeneratorComponent}
import org.make.api.user.UserExceptions.EmailAlreadyRegistredException
import org.make.core.profile.Profile
import org.make.core.user._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait UserServiceComponent { this: IdGeneratorComponent with PersistentUserServiceComponent =>

  def userService: UserService

  class UserService {

    def getUser(uuid: UserId): Future[Option[User]] = {
      persistentUserService.get(uuid)
    }

    def register(email: String,
                 firstName: Option[String],
                 lastName: Option[String],
                 password: String,
                 lastIp: String,
                 dateOfBirth: Option[LocalDate]): Future[User] = {

      val lowerCasedEmail: String = email.toLowerCase()

      persistentUserService.emailExists(lowerCasedEmail).flatMap { result =>
        if (result) {
          Future.failed(EmailAlreadyRegistredException(lowerCasedEmail))
        } else {
          val profile: Option[Profile] = Profile.parseProfile(dateOfBirth = dateOfBirth)

          val salt: String = generateSalt

          val user = User(
            userId = idGenerator.nextUserId(),
            email = lowerCasedEmail,
            firstName = firstName,
            lastName = lastName,
            lastIp = lastIp,
            hashedPassword = password.bcrypt(salt),
            salt = salt,
            enabled = true,
            verified = false,
            lastConnection = DateHelper.now(),
            verificationToken = idGenerator.nextId(),
            roles = Seq(Role.RoleCitizen),
            profile = profile
          )
          persistentUserService.persist(user)
        }
      }
    }
  }
}
