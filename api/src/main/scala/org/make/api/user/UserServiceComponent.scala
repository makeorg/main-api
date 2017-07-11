package org.make.api.user

import java.time.{LocalDate, ZonedDateTime}

import com.github.t3hnar.bcrypt._
import org.make.api.technical.auth.UserTokenGeneratorComponent
import org.make.api.technical.{DateHelper, IdGeneratorComponent, ShortenedNames}
import org.make.api.user.UserExceptions.EmailAlreadyRegistredException
import org.make.core.profile.Profile
import org.make.core.user._

import scala.concurrent.Future
import scala.concurrent.duration._

trait UserServiceComponent {
  def userService: UserService
}

trait UserService extends ShortenedNames {
  def getUser(uuid: UserId): Future[Option[User]]
  def register(email: String,
               firstName: Option[String],
               lastName: Option[String],
               password: String,
               lastIp: String,
               dateOfBirth: Option[LocalDate])(implicit ctx: EC = ECGlobal): Future[User]
}

trait DefaultUserServiceComponent extends UserServiceComponent with ShortenedNames {
  this: IdGeneratorComponent with UserTokenGeneratorComponent with PersistentUserServiceComponent =>

  val userService = new UserService {

    val validationTokenExpiresIn: Long = 30.days.toSeconds

    override def getUser(uuid: UserId): Future[Option[User]] = {
      persistentUserService.get(uuid)
    }

    override def register(email: String,
                          firstName: Option[String],
                          lastName: Option[String],
                          password: String,
                          lastIp: String,
                          dateOfBirth: Option[LocalDate])(implicit ctx: EC = ECGlobal): Future[User] = {

      val lowerCasedEmail: String = email.toLowerCase()

      persistentUserService.emailExists(lowerCasedEmail).flatMap { result =>
        if (result) {
          Future.failed(EmailAlreadyRegistredException(lowerCasedEmail))
        } else {
          val profile: Option[Profile] = Profile.parseProfile(dateOfBirth = dateOfBirth)

          val salt: String = generateSalt

          val futureVerificationToken: Future[(String, String)] = userTokenGenerator.generateVerificationToken()
          futureVerificationToken.flatMap { tokens =>
            val (_, hashedVerificationToken) = tokens
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
              verificationToken = Some(hashedVerificationToken),
              verificationTokenExpiresAt = Some(ZonedDateTime.now().plusSeconds(validationTokenExpiresIn)),
              resetToken = None,
              resetTokenExpiresAt = None,
              roles = Seq(Role.RoleCitizen),
              profile = profile
            )
            persistentUserService.persist(user)
          }
        }
      }
    }
  }
}
