package org.make.api.user

import com.github.t3hnar.bcrypt._
import org.make.api.technical.auth.UserTokenGeneratorComponent
import org.make.api.technical.businessconfig.BusinessConfig
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, ShortenedNames}
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.userhistory.UserEvent.OrganisationRegisteredEvent
import org.make.core.user._
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Success

trait OrganisationServiceComponent {
  def organisationService: OrganisationService
}

trait OrganisationService extends ShortenedNames {
  def getOrganisation(id: UserId): Future[Option[User]]
  def register(organisationRegisterData: OrganisationRegisterData, requestContext: RequestContext): Future[User]
}

case class OrganisationRegisterData(name: String,
                                    email: String,
                                    password: Option[String],
                                    lastIp: Option[String],
                                    country: String,
                                    language: String)

trait DefaultOrganisationServiceComponent extends OrganisationServiceComponent with ShortenedNames {
  this: IdGeneratorComponent
    with UserTokenGeneratorComponent
    with PersistentUserServiceComponent
    with EventBusServiceComponent =>

  val organisationService: OrganisationService = new OrganisationService {

    val validationTokenExpiresIn: Long = 30.days.toSeconds

    override def getOrganisation(userId: UserId): Future[Option[User]] = {
      persistentUserService.get(userId)
    }

    private def registerOrganisation(organisationRegisterData: OrganisationRegisterData,
                             lowerCasedEmail: String,
                             country: String,
                             language: String,
                             hashedVerificationToken: String): Future[User] = {
      val user = User(
        userId = idGenerator.nextUserId(),
        email = lowerCasedEmail,
        firstName = None,
        lastName = None,
        lastIp = organisationRegisterData.lastIp,
        hashedPassword = organisationRegisterData.password.map(_.bcrypt),
        enabled = true,
        emailVerified = true,
        lastConnection = DateHelper.now(),
        verificationToken = Some(hashedVerificationToken),
        verificationTokenExpiresAt = Some(DateHelper.now().plusSeconds(validationTokenExpiresIn)),
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq(Role.RoleOrganisation),
        country = country,
        language = language,
        profile = None
      )
      persistentUserService.persist(user)
    }


    override def register(organisationRegisterData: OrganisationRegisterData,
                          requestContext: RequestContext): Future[User] = {

      val country = BusinessConfig.validateCountry(organisationRegisterData.country)
      val language = BusinessConfig.validateLanguage(organisationRegisterData.country, organisationRegisterData.language)

      val lowerCasedEmail: String = organisationRegisterData.email.toLowerCase()

      val result = for {
        emailExists             <- persistentUserService.emailExists(lowerCasedEmail)
        hashedVerificationToken <- generateVerificationToken(lowerCasedEmail, emailExists)
        user                    <- registerOrganisation(organisationRegisterData, lowerCasedEmail, country, language, hashedVerificationToken)
      } yield user

      result.onComplete {
        case Success(user) =>
          eventBusService.publish(
            OrganisationRegisteredEvent(
              connectedUserId = Some(user.userId),
              userId = user.userId,
              requestContext = requestContext,
              email = user.email,
              country = user.country,
              language = user.language
            )
          )
        case _ =>
      }

      result
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
  }
}
