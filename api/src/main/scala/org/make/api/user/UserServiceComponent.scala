package org.make.api.user

import java.time.{LocalDate, ZonedDateTime}

import com.github.t3hnar.bcrypt._
import org.make.api.technical.auth.UserTokenGeneratorComponent
import org.make.api.technical.{DateHelper, IdGeneratorComponent, ShortenedNames}
import org.make.api.user.UserExceptions.EmailAlreadyRegistredException
import org.make.api.user.social.models.UserInfo
import org.make.core.profile.Profile
import org.make.core.user._

import scala.concurrent.Future
import scala.concurrent.duration._

trait UserServiceComponent {
  def userService: UserService
}

trait UserService extends ShortenedNames {
  def getUser(uuid: UserId): Future[Option[User]]
  def getUser(uuid: String): Future[Option[User]]
  def register(email: String,
               firstName: Option[String],
               lastName: Option[String],
               password: Option[String],
               lastIp: Option[String],
               dateOfBirth: Option[LocalDate])(implicit ctx: EC = ECGlobal): Future[User]
  def getOrCreateUserFromSocial(userInfo: UserInfo, clientIp: Option[String])(implicit ctx: EC = ECGlobal): Future[User]
}

trait DefaultUserServiceComponent extends UserServiceComponent with ShortenedNames {
  this: IdGeneratorComponent with UserTokenGeneratorComponent with PersistentUserServiceComponent =>

  val userService = new UserService {

    val validationTokenExpiresIn: Long = 30.days.toSeconds

    override def getUser(uuid: UserId): Future[Option[User]] = {
      persistentUserService.get(uuid)
    }

    override def getUser(uuid: String): Future[Option[User]] = {
      persistentUserService.get(UserId(uuid))
    }

    override def register(email: String,
                          firstName: Option[String],
                          lastName: Option[String],
                          password: Option[String],
                          lastIp: Option[String],
                          dateOfBirth: Option[LocalDate])(implicit ctx: EC = ECGlobal): Future[User] = {

      val lowerCasedEmail: String = email.toLowerCase()

      persistentUserService.emailExists(lowerCasedEmail).flatMap { result =>
        if (result) {
          Future.failed(EmailAlreadyRegistredException(lowerCasedEmail))
        } else {
          val profile: Option[Profile] = Profile.parseProfile(dateOfBirth = dateOfBirth)

          val futureVerificationToken: Future[(String, String)] = userTokenGenerator.generateVerificationToken()
          futureVerificationToken.flatMap { tokens =>
            val (_, hashedVerificationToken) = tokens
            val user = User(
              userId = idGenerator.nextUserId(),
              email = lowerCasedEmail,
              firstName = firstName,
              lastName = lastName,
              lastIp = lastIp,
              hashedPassword = password.map(_.bcrypt),
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

    override def getOrCreateUserFromSocial(userInfo: UserInfo,
                                           clientIp: Option[String])(implicit ctx: EC = ECGlobal): Future[User] = {
      val lowerCasedEmail: String = userInfo.email.toLowerCase()

      persistentUserService.findByEmail(lowerCasedEmail).flatMap {
        case Some(user) => Future.successful(user)
        case None =>
          val profile: Option[Profile] =
            Profile.parseProfile(facebookId = userInfo.facebookId, googleId = userInfo.googleId)
          val user = User(
            userId = idGenerator.nextUserId(),
            email = lowerCasedEmail,
            firstName = Some(userInfo.firstName),
            lastName = Some(userInfo.lastName),
            lastIp = clientIp,
            hashedPassword = None,
            enabled = true,
            verified = true,
            lastConnection = DateHelper.now(),
            verificationToken = None,
            verificationTokenExpiresAt = None,
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
