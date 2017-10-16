package org.make.api.user

import java.time.LocalDate

import com.github.t3hnar.bcrypt._
import org.make.api.technical.auth.UserTokenGeneratorComponent
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, ShortenedNames}
import org.make.api.user.UserExceptions.{EmailAlreadyRegisteredException, ResetTokenRequestException}
import org.make.api.user.social.models.UserInfo
import org.make.api.user.social.models.google.{UserInfo => GoogleUserInfo}
import org.make.core.profile.Profile
import org.make.api.userhistory.UserEvent.UserRegisteredEvent
import org.make.core.user._
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

trait UserServiceComponent {
  def userService: UserService
}

trait UserService extends ShortenedNames {
  def getUser(uuid: UserId): Future[Option[User]]
  def getUser(uuid: String): Future[Option[User]]
  def getUsersByUserIds(ids: Seq[UserId]): Future[Seq[User]]
  def register(userRegisterData: UserRegisterData, requestContext: RequestContext): Future[User]
  def getOrCreateUserFromSocial(userInfo: UserInfo, clientIp: Option[String]): Future[User]
  def requestPasswordReset(userId: UserId): Unit
  def updatePassword(userId: UserId, resetToken: String, password: String): Future[Boolean]
  def validateEmail(verificationToken: String): Future[Boolean]
}

case class UserRegisterData(email: String,
                            firstName: Option[String],
                            lastName: Option[String] = None,
                            password: Option[String],
                            lastIp: Option[String],
                            dateOfBirth: Option[LocalDate] = None,
                            profession: Option[String] = None,
                            postalCode: Option[String] = None)

trait DefaultUserServiceComponent extends UserServiceComponent with ShortenedNames {
  this: IdGeneratorComponent
    with UserTokenGeneratorComponent
    with PersistentUserServiceComponent
    with EventBusServiceComponent =>

  val userService: UserService = new UserService {

    val validationTokenExpiresIn: Long = 30.days.toSeconds
    val resetTokenExpiresIn: Long = 1.days.toSeconds

    override def getUser(uuid: UserId): Future[Option[User]] = {
      persistentUserService.get(uuid)
    }

    override def getUser(uuid: String): Future[Option[User]] = {
      persistentUserService.get(UserId(uuid))
    }

    override def getUsersByUserIds(ids: Seq[UserId]): Future[Seq[User]] = {
      persistentUserService.findAllByUserIds(ids)
    }

    private def registerUser(userRegisterData: UserRegisterData,
                             lowerCasedEmail: String,
                             profile: Option[Profile],
                             hashedVerificationToken: String) = {
      val user = User(
        userId = idGenerator.nextUserId(),
        email = lowerCasedEmail,
        firstName = userRegisterData.firstName,
        lastName = userRegisterData.lastName,
        lastIp = userRegisterData.lastIp,
        hashedPassword = userRegisterData.password.map(_.bcrypt),
        enabled = true,
        verified = false,
        lastConnection = DateHelper.now(),
        verificationToken = Some(hashedVerificationToken),
        verificationTokenExpiresAt = Some(DateHelper.now().plusSeconds(validationTokenExpiresIn)),
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq(Role.RoleCitizen),
        profile = profile
      )
      persistentUserService.persist(user)
    }

    private def generateVerificationToken(lowerCasedEmail: String, emailExists: Boolean): Future[String] = {
      if (emailExists) {
        Future.failed(EmailAlreadyRegisteredException(lowerCasedEmail))
      } else {
        userTokenGenerator.generateVerificationToken().map {
          case (_, token) => token
        }
      }
    }

    private def generateResetToken(): Future[String] = {
      userTokenGenerator.generateResetToken().map {
        case (_, token) => token
      }
    }

    override def register(userRegisterData: UserRegisterData, requestContext: RequestContext): Future[User] = {

      val lowerCasedEmail: String = userRegisterData.email.toLowerCase()
      val profile: Option[Profile] =
        Profile.parseProfile(
          dateOfBirth = userRegisterData.dateOfBirth,
          profession = userRegisterData.profession,
          postalCode = userRegisterData.postalCode
        )

      val result = for {
        emailExists             <- persistentUserService.emailExists(lowerCasedEmail)
        hashedVerificationToken <- generateVerificationToken(lowerCasedEmail, emailExists)
        user                    <- registerUser(userRegisterData, lowerCasedEmail, profile, hashedVerificationToken)
      } yield user

      result.onComplete {
        case Success(user) =>
          eventBusService.publish(
            UserRegisteredEvent(
              connectedUserId = Some(user.userId),
              userId = user.userId,
              requestContext = requestContext,
              email = user.email,
              firstName = user.firstName,
              lastName = user.lastName,
              profession = user.profile.flatMap(_.profession),
              dateOfBirth = user.profile.flatMap(_.dateOfBirth),
              postalCode = user.profile.flatMap(_.postalCode)
            )
          )
        case _ =>
      }

      result
    }

    override def getOrCreateUserFromSocial(userInfo: UserInfo, clientIp: Option[String]): Future[User] = {
      val lowerCasedEmail: String = userInfo.email.toLowerCase()

      persistentUserService.findByEmail(lowerCasedEmail).flatMap {
        case Some(user) => Future.successful(user)
        case None =>
          val profile: Option[Profile] =
            Profile.parseProfile(
              facebookId = userInfo.facebookId,
              googleId = userInfo.googleId,
              avatarUrl = userInfo.picture
            )

          // @todo: Add a unit test to check role by domain
          var roles: Seq[Role] = Seq(Role.RoleCitizen)
          if (userInfo.domain.contains(GoogleUserInfo.MODERATOR_DOMAIN)) {
            roles = roles ++ Seq(Role.RoleAdmin, Role.RoleModerator)
          }

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
            roles = roles,
            profile = profile
          )

          persistentUserService.persist(user)
      }
    }

    override def requestPasswordReset(userId: UserId): Unit = {
      val result = for {
        resetToken <- generateResetToken()
        result <- persistentUserService.requestResetPassword(
          userId,
          resetToken,
          Some(DateHelper.now().plusSeconds(resetTokenExpiresIn))
        )
      } yield result

      result.onComplete {
        case Success(_) => Future.successful(true)
        case (_)        => Future.failed(ResetTokenRequestException())
      }
    }

    override def updatePassword(userId: UserId, resetToken: String, password: String): Future[Boolean] = {
      persistentUserService.updatePassword(userId, resetToken, password.bcrypt)
    }

    override def validateEmail(verificationToken: String): Future[Boolean] = {
      persistentUserService.validateEmail(verificationToken)
    }
  }
}
