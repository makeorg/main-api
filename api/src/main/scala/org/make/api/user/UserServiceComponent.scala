package org.make.api.user

import java.time.LocalDate

import com.github.t3hnar.bcrypt._
import com.typesafe.scalalogging.StrictLogging
import org.make.api.technical.auth.UserTokenGeneratorComponent
import org.make.api.technical.businessconfig.BusinessConfig
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, ShortenedNames}
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user.UserUpdateEvent._
import org.make.api.user.social.models.UserInfo
import org.make.api.user.social.models.google.{UserInfo => GoogleUserInfo}
import org.make.api.userhistory.UserEvent._
import org.make.core.profile.Gender.{Female, Male, Other}
import org.make.core.profile.Profile
import org.make.core.user._
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Success

trait UserServiceComponent {
  def userService: UserService
}

trait UserService extends ShortenedNames {
  def getUser(id: UserId): Future[Option[User]]
  def getUserByEmail(email: String): Future[Option[User]]
  def getUsersByUserIds(ids: Seq[UserId]): Future[Seq[User]]
  def register(userRegisterData: UserRegisterData, requestContext: RequestContext): Future[User]
  def getOrCreateUserFromSocial(userInfo: UserInfo,
                                clientIp: Option[String],
                                requestContext: RequestContext): Future[User]
  def requestPasswordReset(userId: UserId): Future[Boolean]
  def updatePassword(userId: UserId, resetToken: String, password: String): Future[Boolean]
  def validateEmail(verificationToken: String): Future[Boolean]
  def updateOptInNewsletter(userId: UserId, optInNewsletter: Boolean): Future[Boolean]
  def updateOptInNewsletter(email: String, optInNewsletter: Boolean): Future[Boolean]
  def updateIsHardBounce(userId: UserId, isHardBounce: Boolean): Future[Boolean]
  def updateIsHardBounce(email: String, isHardBounce: Boolean): Future[Boolean]
  def updateLastMailingError(userId: UserId, lastMailingError: Option[MailingErrorLog]): Future[Boolean]
  def updateLastMailingError(email: String, lastMailingError: Option[MailingErrorLog]): Future[Boolean]
  def getUsersWithHardBounce(page: Int, limit: Int): Future[Seq[User]]
  def getOptInUsers(page: Int, limit: Int): Future[Seq[User]]
  def getOptOutUsers(page: Int, limit: Int): Future[Seq[User]]
}

case class UserRegisterData(email: String,
                            firstName: Option[String],
                            lastName: Option[String] = None,
                            password: Option[String],
                            lastIp: Option[String],
                            dateOfBirth: Option[LocalDate] = None,
                            profession: Option[String] = None,
                            postalCode: Option[String] = None,
                            country: String,
                            language: String)

trait DefaultUserServiceComponent extends UserServiceComponent with ShortenedNames with StrictLogging {
  this: IdGeneratorComponent
    with UserTokenGeneratorComponent
    with PersistentUserServiceComponent
    with EventBusServiceComponent =>

  val userService: UserService = new UserService {

    val validationTokenExpiresIn: Long = 30.days.toSeconds
    val resetTokenExpiresIn: Long = 1.days.toSeconds

    override def getUser(userId: UserId): Future[Option[User]] = {
      persistentUserService.get(userId)
    }

    override def getUserByEmail(email: String): Future[Option[User]] = {
      persistentUserService.findByEmail(email)
    }

    override def getUsersByUserIds(ids: Seq[UserId]): Future[Seq[User]] = {
      persistentUserService.findAllByUserIds(ids)
    }

    private def registerUser(userRegisterData: UserRegisterData,
                             lowerCasedEmail: String,
                             country: String,
                             language: String,
                             profile: Option[Profile],
                             hashedVerificationToken: String): Future[User] = {
      val user = User(
        userId = idGenerator.nextUserId(),
        email = lowerCasedEmail,
        firstName = userRegisterData.firstName,
        lastName = userRegisterData.lastName,
        lastIp = userRegisterData.lastIp,
        hashedPassword = userRegisterData.password.map(_.bcrypt),
        enabled = true,
        emailVerified = false,
        lastConnection = DateHelper.now(),
        verificationToken = Some(hashedVerificationToken),
        verificationTokenExpiresAt = Some(DateHelper.now().plusSeconds(validationTokenExpiresIn)),
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq(Role.RoleCitizen),
        country = country,
        language = language,
        profile = profile
      )

      val futureUser: Future[User] = persistentUserService.persist(user)
      futureUser.onComplete {
        case Success(u) => eventBusService.publish(UserCreatedEvent(userId = Some(u.userId)))
        case _          =>
      }

      futureUser
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

      val country = BusinessConfig.validateCountry(userRegisterData.country)
      val language = BusinessConfig.validateLanguage(userRegisterData.country, userRegisterData.language)

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
        user                    <- registerUser(userRegisterData, lowerCasedEmail, country, language, profile, hashedVerificationToken)
      } yield user

      result.onComplete {
        case Success(user) =>
          eventBusService.publish(UserCreatedEvent(userId = Some(user.userId)))
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
              postalCode = user.profile.flatMap(_.postalCode),
              country = user.country,
              language = user.language
            )
          )
        case _ =>
      }

      result
    }

    override def getOrCreateUserFromSocial(userInfo: UserInfo,
                                           clientIp: Option[String],
                                           requestContext: RequestContext): Future[User] = {

      val lowerCasedEmail: String = userInfo.email.map(_.toLowerCase()).getOrElse("")

      persistentUserService.findByEmail(lowerCasedEmail).flatMap {
        case Some(user) => Future.successful(user)
        case None       => createUserFromSocial(requestContext, userInfo, clientIp)
      }
    }

    private def createUserFromSocial(requestContext: RequestContext,
                                     userInfo: UserInfo,
                                     clientIp: Option[String]): Future[User] = {

      val country = BusinessConfig.validateCountry(userInfo.country)
      val language = BusinessConfig.validateLanguage(userInfo.country, userInfo.language)
      val lowerCasedEmail: String = userInfo.email.map(_.toLowerCase()).getOrElse("")
      val profile: Option[Profile] =
        Profile.parseProfile(
          facebookId = userInfo.facebookId,
          googleId = userInfo.googleId,
          avatarUrl = userInfo.picture,
          gender = userInfo.gender.map {
            case "male"   => Male
            case "female" => Female
            case _        => Other
          },
          genderName = userInfo.gender
        )

      val user = User(
        userId = idGenerator.nextUserId(),
        email = lowerCasedEmail,
        firstName = userInfo.firstName,
        lastName = userInfo.lastName,
        lastIp = clientIp,
        hashedPassword = None,
        enabled = true,
        emailVerified = true,
        lastConnection = DateHelper.now(),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = getRolesFromSocial(userInfo),
        country = country,
        language = language,
        profile = profile
      )

      persistentUserService.persist(user).map { user =>
        publishCreateEventsFromSocial(user = user, requestContext = requestContext)
        user
      }
    }

    // @todo: Add a unit test to check role by domain
    private def getRolesFromSocial(userInfo: UserInfo): Seq[Role] = {
      var roles: Seq[Role] = Seq(Role.RoleCitizen)
      if (userInfo.domain.contains(GoogleUserInfo.MODERATOR_DOMAIN)) {
        roles = roles ++ Seq(Role.RoleAdmin, Role.RoleModerator)
      }

      roles
    }

    private def publishCreateEventsFromSocial(user: User, requestContext: RequestContext): Unit = {
      eventBusService.publish(UserCreatedEvent(userId = Some(user.userId)))
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
          postalCode = user.profile.flatMap(_.postalCode),
          country = user.country,
          language = user.language
        )
      )

      eventBusService.publish(
        UserValidatedAccountEvent(
          userId = user.userId,
          country = user.country,
          language = user.language,
          requestContext = requestContext
        )
      )
      eventBusService.publish(UserUpdateValidatedEvent(userId = Some(user.userId)))
    }

    override def requestPasswordReset(userId: UserId): Future[Boolean] = {
      for {
        resetToken <- generateResetToken()
        result <- persistentUserService.requestResetPassword(
          userId,
          resetToken,
          Some(DateHelper.now().plusSeconds(resetTokenExpiresIn))
        )
      } yield result
    }

    override def updatePassword(userId: UserId, resetToken: String, password: String): Future[Boolean] = {
      val futureResult: Future[Boolean] = persistentUserService.updatePassword(userId, resetToken, password.bcrypt)
      futureResult.onComplete {
        case Success(true) => UserUpdatedPasswordEvent(userId = Some(userId))
        case _             =>
      }

      futureResult
    }

    override def validateEmail(verificationToken: String): Future[Boolean] = {
      persistentUserService.validateEmail(verificationToken)
    }

    override def updateOptInNewsletter(userId: UserId, optInNewsletter: Boolean): Future[Boolean] = {
      val futureResult: Future[Boolean] = persistentUserService.updateOptInNewsletter(userId, optInNewsletter)
      futureResult.onComplete {
        case Success(true) =>
          eventBusService.publish(
            UserUpdatedOptInNewsletterEvent(
              userId = Some(userId),
              eventDate = DateHelper.now(),
              optInNewsletter = optInNewsletter
            )
          )
        case _ =>
      }
      futureResult
    }

    override def updateIsHardBounce(userId: UserId, isHardBounce: Boolean): Future[Boolean] = {
      val futureResult: Future[Boolean] = persistentUserService.updateIsHardBounce(userId, isHardBounce)

      futureResult.onComplete {
        case Success(true) if isHardBounce =>
          eventBusService.publish(UserUpdatedHardBounceEvent(userId = Some(userId)))
        case _ =>
      }
      futureResult
    }

    override def updateOptInNewsletter(email: String, optInNewsletter: Boolean): Future[Boolean] = {
      val futureResult = persistentUserService.updateOptInNewsletter(email, optInNewsletter)
      futureResult.onComplete {
        case Success(true) =>
          eventBusService.publish(
            UserUpdatedOptInNewsletterEvent(
              email = Some(email),
              eventDate = DateHelper.now(),
              optInNewsletter = optInNewsletter
            )
          )
        case _ =>
      }
      futureResult
    }

    override def updateIsHardBounce(email: String, isHardBounce: Boolean): Future[Boolean] = {
      val futureResult: Future[Boolean] = persistentUserService.updateIsHardBounce(email, isHardBounce)

      futureResult.onComplete {
        case Success(true) =>
          eventBusService.publish(UserUpdatedHardBounceEvent(email = Some(email), eventDate = DateHelper.now()))
        case _ =>
      }
      futureResult
    }

    override def updateLastMailingError(email: String, lastMailingError: Option[MailingErrorLog]): Future[Boolean] = {
      persistentUserService.updateLastMailingError(email, lastMailingError)
    }

    override def updateLastMailingError(userId: UserId, lastMailingError: Option[MailingErrorLog]): Future[Boolean] = {
      persistentUserService.updateLastMailingError(userId, lastMailingError)
    }

    override def getUsersWithHardBounce(page: Int, limit: Int): Future[Seq[User]] = {
      persistentUserService.findUsersWithHardBounce(page: Int, limit: Int)
    }

    override def getOptInUsers(page: Int, limit: Int): Future[Seq[User]] = {
      persistentUserService.findOptInUsers(page: Int, limit: Int)
    }

    override def getOptOutUsers(page: Int, limit: Int): Future[Seq[User]] = {
      persistentUserService.findOptOutUsers(page: Int, limit: Int)
    }
  }
}
