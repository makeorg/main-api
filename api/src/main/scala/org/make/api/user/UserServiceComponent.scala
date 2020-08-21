/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.user

import java.io.File
import java.nio.file.Files
import java.time.{LocalDate, ZonedDateTime}

import akka.http.scaladsl.model.ContentType
import com.github.t3hnar.bcrypt._
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal.ProposalServiceComponent
import org.make.api.proposal.PublishedProposalEvent.ReindexProposal
import org.make.api.question.AuthorRequest
import org.make.api.technical._
import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.api.technical.auth.{MakeDataHandlerComponent, TokenGeneratorComponent, UserTokenGeneratorComponent}
import org.make.api.technical.crm.CrmServiceComponent
import org.make.api.technical.security.SecurityHelper
import org.make.api.technical.storage.Content.FileContent
import org.make.api.technical.storage.StorageServiceComponent
import org.make.api.user.UserExceptions.{EmailAlreadyRegisteredException, EmailNotAllowed}
import org.make.api.user.social.models.UserInfo
import org.make.api.user.social.models.google.{UserInfo => GoogleUserInfo}
import org.make.api.user.validation.UserRegistrationValidatorComponent
import org.make.api.userhistory._
import org.make.core.auth.UserRights
import org.make.core.profile.Gender.{Female, Male, Other}
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.proposal._
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.user.Role.RoleCitizen
import org.make.core.user._
import org.make.core.{BusinessConfig, DateHelper, RequestContext, ValidationError, ValidationFailedError}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait UserServiceComponent {
  def userService: UserService
}

trait UserService extends ShortenedNames {
  def getUser(id: UserId): Future[Option[User]]
  def getPersonality(id: UserId): Future[Option[User]]
  def getUserByEmail(email: String): Future[Option[User]]
  def getUserByUserIdAndPassword(userId: UserId, password: Option[String]): Future[Option[User]]
  def getUsersByUserIds(ids: Seq[UserId]): Future[Seq[User]]
  def adminFindUsers(
    start: Int,
    limit: Option[Int],
    sort: Option[String],
    order: Option[String],
    email: Option[String],
    firstName: Option[String],
    lastName: Option[String],
    role: Option[Role],
    userType: Option[UserType]
  ): Future[Seq[User]]
  def register(userRegisterData: UserRegisterData, requestContext: RequestContext): Future[User]
  def registerPersonality(
    personalityRegisterData: PersonalityRegisterData,
    requestContext: RequestContext
  ): Future[User]
  def update(user: User, requestContext: RequestContext): Future[User]
  def updatePersonality(
    personality: User,
    moderatorId: Option[UserId],
    oldEmail: String,
    requestContext: RequestContext
  ): Future[User]
  def createOrUpdateUserFromSocial(
    userInfo: UserInfo,
    clientIp: Option[String],
    questionId: Option[QuestionId],
    requestContext: RequestContext
  ): Future[(User, Boolean)]
  def requestPasswordReset(userId: UserId): Future[Boolean]
  def updatePassword(userId: UserId, resetToken: Option[String], password: String): Future[Boolean]
  def validateEmail(user: User, verificationToken: String): Future[TokenResponse]
  def updateOptInNewsletter(userId: UserId, optInNewsletter: Boolean): Future[Boolean]
  def updateOptInNewsletter(email: String, optInNewsletter: Boolean): Future[Boolean]
  def updateIsHardBounce(userId: UserId, isHardBounce: Boolean): Future[Boolean]
  def updateIsHardBounce(email: String, isHardBounce: Boolean): Future[Boolean]
  def updateLastMailingError(userId: UserId, lastMailingError: Option[MailingErrorLog]): Future[Boolean]
  def updateLastMailingError(email: String, lastMailingError: Option[MailingErrorLog]): Future[Boolean]
  def findUsersForCrmSynchro(
    optIn: Option[Boolean],
    hardBounce: Option[Boolean],
    page: Int,
    limit: Int
  ): Future[Seq[User]]
  def getUsersWithoutRegisterQuestion: Future[Seq[User]]
  def anonymize(user: User, adminId: UserId, requestContext: RequestContext): Future[Unit]
  def getFollowedUsers(userId: UserId): Future[Seq[UserId]]
  def followUser(followedUserId: UserId, userId: UserId, requestContext: RequestContext): Future[UserId]
  def unfollowUser(followedUserId: UserId, userId: UserId, requestContext: RequestContext): Future[UserId]
  def retrieveOrCreateVirtualUser(userInfo: AuthorRequest, country: Country, language: Language): Future[User]
  def adminCountUsers(
    email: Option[String],
    firstName: Option[String],
    lastName: Option[String],
    role: Option[Role],
    userType: Option[UserType]
  ): Future[Int]
  def reconnectInfo(userId: UserId): Future[Option[ReconnectInfo]]
  def changeEmailVerificationTokenIfNeeded(userId: UserId): Future[Option[String]]
  def changeAvatarForUser(
    userId: UserId,
    avatarUrl: String,
    requestContext: RequestContext,
    eventDate: ZonedDateTime
  ): Future[Unit]
  def adminUpdateUserEmail(user: User, email: String): Future[Unit]
}

case class UserRegisterData(
  email: String,
  firstName: Option[String],
  lastName: Option[String] = None,
  password: Option[String],
  lastIp: Option[String],
  dateOfBirth: Option[LocalDate] = None,
  profession: Option[String] = None,
  postalCode: Option[String] = None,
  gender: Option[Gender] = None,
  socioProfessionalCategory: Option[SocioProfessionalCategory] = None,
  country: Country,
  language: Language,
  questionId: Option[QuestionId] = None,
  optIn: Option[Boolean] = None,
  optInPartner: Option[Boolean] = None,
  roles: Seq[Role] = Seq(Role.RoleCitizen),
  availableQuestions: Seq[QuestionId] = Seq.empty,
  politicalParty: Option[String] = None,
  website: Option[String] = None,
  publicProfile: Boolean = false,
  legalMinorConsent: Option[Boolean] = None,
  legalAdvisorApproval: Option[Boolean] = None
)

case class PersonalityRegisterData(
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  gender: Option[Gender],
  genderName: Option[String],
  country: Country,
  language: Language,
  description: Option[String],
  avatarUrl: Option[String],
  website: Option[String],
  politicalParty: Option[String]
)

trait DefaultUserServiceComponent extends UserServiceComponent with ShortenedNames with StrictLogging {
  this: IdGeneratorComponent
    with MakeDataHandlerComponent
    with UserTokenGeneratorComponent
    with PersistentUserServiceComponent
    with PersistentUserToAnonymizeServiceComponent
    with ProposalServiceComponent
    with CrmServiceComponent
    with EventBusServiceComponent
    with TokenGeneratorComponent
    with MakeSettingsComponent
    with UserRegistrationValidatorComponent
    with StorageServiceComponent
    with DownloadServiceComponent
    with UserHistoryCoordinatorServiceComponent =>

  override lazy val userService: UserService = new DefaultUserService

  class DefaultUserService extends UserService {

    val validationTokenExpiresIn: Long = makeSettings.validationTokenExpiresIn.toSeconds
    val resetTokenExpiresIn: Long = makeSettings.resetTokenExpiresIn.toSeconds
    val resetTokenB2BExpiresIn: Long = makeSettings.resetTokenB2BExpiresIn.toSeconds

    override def getUser(userId: UserId): Future[Option[User]] = {
      persistentUserService.get(userId)
    }

    override def getPersonality(id: UserId): Future[Option[User]] = {
      persistentUserService.findByUserIdAndUserType(id, UserType.UserTypePersonality)
    }

    override def getUserByEmail(email: String): Future[Option[User]] = {
      persistentUserService.findByEmail(email)
    }

    override def getUserByUserIdAndPassword(userId: UserId, password: Option[String]): Future[Option[User]] = {
      persistentUserService.findByUserIdAndPassword(userId, password)
    }

    override def getUsersByUserIds(ids: Seq[UserId]): Future[Seq[User]] = {
      persistentUserService.findAllByUserIds(ids)
    }

    override def adminFindUsers(
      start: Int,
      limit: Option[Int],
      sort: Option[String],
      order: Option[String],
      email: Option[String],
      firstName: Option[String],
      lastName: Option[String],
      role: Option[Role],
      userType: Option[UserType]
    ): Future[Seq[User]] = {
      persistentUserService.adminFindUsers(start, limit, sort, order, email, firstName, lastName, role, userType)
    }

    private def registerUser(
      userRegisterData: UserRegisterData,
      lowerCasedEmail: String,
      country: Country,
      language: Language,
      profile: Option[Profile],
      hashedVerificationToken: String
    ): Future[User] = {

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
        roles = userRegisterData.roles,
        country = country,
        language = language,
        profile = profile,
        availableQuestions = userRegisterData.availableQuestions,
        anonymousParticipation = makeSettings.defaultUserAnonymousParticipation,
        userType = UserType.UserTypeUser,
        publicProfile = userRegisterData.publicProfile
      )

      persistentUserService.persist(user)
    }

    private def persistPersonality(
      personalityRegisterData: PersonalityRegisterData,
      lowerCasedEmail: String,
      country: Country,
      language: Language,
      profile: Option[Profile],
      resetToken: String
    ): Future[User] = {

      val user = User(
        userId = idGenerator.nextUserId(),
        email = lowerCasedEmail,
        firstName = personalityRegisterData.firstName,
        lastName = personalityRegisterData.lastName,
        lastIp = None,
        hashedPassword = None,
        enabled = true,
        emailVerified = true,
        lastConnection = DateHelper.now(),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = Some(resetToken),
        resetTokenExpiresAt = Some(DateHelper.now().plusSeconds(resetTokenB2BExpiresIn)),
        roles = Seq(Role.RoleCitizen),
        country = country,
        language = language,
        profile = profile,
        availableQuestions = Seq.empty,
        anonymousParticipation = makeSettings.defaultUserAnonymousParticipation,
        userType = UserType.UserTypePersonality,
        publicProfile = true
      )

      persistentUserService.persist(user)
    }

    private def validateAccountCreation(
      emailExists: Boolean,
      canRegister: Boolean,
      lowerCasedEmail: String
    ): Future[Unit] = {
      if (emailExists) {
        Future.failed(EmailAlreadyRegisteredException(lowerCasedEmail))
      } else if (!canRegister) {
        Future.failed(EmailNotAllowed(lowerCasedEmail))
      } else {
        Future.successful {}
      }
    }

    private def generateVerificationToken(): Future[String] = {
      userTokenGenerator.generateVerificationToken().map {
        case (_, token) => token
      }
    }

    private def generateResetToken(): Future[String] = {
      userTokenGenerator.generateResetToken().map {
        case (_, token) => token
      }
    }

    private def generateReconnectToken(): Future[String] = {
      userTokenGenerator.generateReconnectToken().map {
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
          postalCode = userRegisterData.postalCode,
          gender = userRegisterData.gender,
          socioProfessionalCategory = userRegisterData.socioProfessionalCategory,
          registerQuestionId = userRegisterData.questionId,
          optInNewsletter = userRegisterData.optIn.getOrElse(true),
          optInPartner = userRegisterData.optInPartner,
          politicalParty = userRegisterData.politicalParty,
          website = userRegisterData.website
        )

      val result = for {
        emailExists             <- persistentUserService.emailExists(lowerCasedEmail)
        canRegister             <- userRegistrationValidator.canRegister(userRegisterData)
        _                       <- validateAccountCreation(emailExists, canRegister, lowerCasedEmail)
        hashedVerificationToken <- generateVerificationToken()
        user                    <- registerUser(userRegisterData, lowerCasedEmail, country, language, profile, hashedVerificationToken)
      } yield user

      result.map { user =>
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
            language = user.language,
            gender = user.profile.flatMap(_.gender),
            socioProfessionalCategory = user.profile.flatMap(_.socioProfessionalCategory),
            optInPartner = user.profile.flatMap(_.optInPartner),
            registerQuestionId = user.profile.flatMap(_.registerQuestionId),
            eventDate = DateHelper.now()
          )
        )
        user
      }
    }

    override def registerPersonality(
      personalityRegisterData: PersonalityRegisterData,
      requestContext: RequestContext
    ): Future[User] = {

      val country = BusinessConfig.validateCountry(personalityRegisterData.country)
      val language = BusinessConfig.validateLanguage(personalityRegisterData.country, personalityRegisterData.language)

      val lowerCasedEmail: String = personalityRegisterData.email.toLowerCase()
      val profile: Option[Profile] =
        Profile.parseProfile(
          gender = personalityRegisterData.gender,
          genderName = personalityRegisterData.genderName,
          description = personalityRegisterData.description,
          avatarUrl = personalityRegisterData.avatarUrl,
          optInNewsletter = false,
          optInPartner = Some(false),
          politicalParty = personalityRegisterData.politicalParty,
          website = personalityRegisterData.website
        )

      val result = for {
        emailExists <- persistentUserService.emailExists(lowerCasedEmail)
        _           <- validateAccountCreation(emailExists, canRegister = true, lowerCasedEmail)
        resetToken  <- generateResetToken()
        user        <- persistPersonality(personalityRegisterData, lowerCasedEmail, country, language, profile, resetToken)
      } yield user

      result.map { user =>
        eventBusService.publish(
          PersonalityRegisteredEvent(
            connectedUserId = Some(user.userId),
            userId = user.userId,
            requestContext = requestContext,
            email = user.email,
            country = user.country,
            language = user.language,
            eventDate = DateHelper.now()
          )
        )
        user
      }
    }

    override def createOrUpdateUserFromSocial(
      userInfo: UserInfo,
      clientIp: Option[String],
      questionId: Option[QuestionId],
      requestContext: RequestContext
    ): Future[(User, Boolean)] = {

      userInfo.email
        .filter(_ != "")
        .map(_.toLowerCase())
        .map { lowerCasedEmail =>
          persistentUserService.findByEmail(lowerCasedEmail).flatMap {
            case Some(user) => updateUserFromSocial(user, userInfo, clientIp).map((_, false))
            case None =>
              createUserFromSocial(lowerCasedEmail, requestContext, userInfo, questionId, clientIp).map((_, true))
          }
        }
        .getOrElse {
          logger.error(
            "We couldn't find any email on social login. UserInfo: {}, requestContext: {}",
            userInfo,
            requestContext
          )
          Future
            .failed(ValidationFailedError(Seq(ValidationError("email", "missing", Some("No email found for user")))))
        }
    }

    private def createUserFromSocial(
      lowerCasedEmail: String,
      requestContext: RequestContext,
      userInfo: UserInfo,
      questionId: Option[QuestionId],
      clientIp: Option[String]
    ): Future[User] = {

      val country = BusinessConfig.validateCountry(userInfo.country)
      val language = BusinessConfig.validateLanguage(userInfo.country, userInfo.language)
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
          genderName = userInfo.gender,
          registerQuestionId = questionId,
          dateOfBirth = userInfo.dateOfBirth
        )

      val user = User(
        userId = idGenerator.nextUserId(),
        email = lowerCasedEmail,
        firstName = userInfo.firstName,
        lastName = None,
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
        profile = profile,
        availableQuestions = Seq.empty,
        anonymousParticipation = makeSettings.defaultUserAnonymousParticipation,
        userType = UserType.UserTypeUser
      )

      persistentUserService.persist(user).map { user =>
        publishCreateEventsFromSocial(user = user, requestContext = requestContext)
        user
      }
    }

    private def updateUserFromSocial(user: User, userInfo: UserInfo, clientIp: Option[String]): Future[User] = {
      val country = BusinessConfig.validateCountry(userInfo.country)
      val language = BusinessConfig.validateLanguage(userInfo.country, userInfo.language)
      val hashedPassword = if (!user.emailVerified) None else user.hashedPassword

      val updatedProfile: Option[Profile] = user.profile.map {
        _.copy(facebookId = userInfo.facebookId, googleId = userInfo.googleId, gender = userInfo.gender.map {
          case "male"   => Male
          case "female" => Female
          case _        => Other
        }, genderName = userInfo.gender)
      }.orElse {
        Profile.parseProfile(
          facebookId = userInfo.facebookId,
          googleId = userInfo.googleId,
          gender = userInfo.gender.map {
            case "male"   => Male
            case "female" => Female
            case _        => Other
          },
          genderName = userInfo.gender
        )
      }
      val updatedUser: User =
        user.copy(
          firstName = userInfo.firstName,
          lastIp = clientIp,
          country = country,
          language = language,
          profile = updatedProfile,
          hashedPassword = hashedPassword,
          emailVerified = true
        )

      persistentUserService.updateSocialUser(updatedUser).map { userUpdated =>
        if (userUpdated) updatedUser else user
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
          language = user.language,
          isSocialLogin = true,
          gender = user.profile.flatMap(_.gender),
          socioProfessionalCategory = user.profile.flatMap(_.socioProfessionalCategory),
          optInPartner = user.profile.flatMap(_.optInPartner),
          registerQuestionId = user.profile.flatMap(_.registerQuestionId),
          eventDate = DateHelper.now()
        )
      )
      eventBusService.publish(
        UserValidatedAccountEvent(
          userId = user.userId,
          country = user.country,
          language = user.language,
          requestContext = requestContext,
          isSocialLogin = true,
          eventDate = DateHelper.now()
        )
      )
      user.profile.flatMap(_.avatarUrl).foreach { avatarUrl =>
        eventBusService.publish(
          UserUploadAvatarEvent(
            connectedUserId = Some(user.userId),
            userId = user.userId,
            country = user.country,
            language = user.language,
            requestContext = requestContext,
            avatarUrl = avatarUrl,
            eventDate = DateHelper.now()
          )
        )
      }
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

    override def updatePassword(userId: UserId, resetToken: Option[String], password: String): Future[Boolean] = {
      persistentUserService.updatePassword(userId, resetToken, password.bcrypt)
    }

    override def validateEmail(user: User, verificationToken: String): Future[TokenResponse] = {

      for {
        emailVerified <- persistentUserService.validateEmail(verificationToken)
        accessToken <- oauth2DataHandler.createAccessToken(authInfo = AuthInfo(
          user = UserRights(user.userId, user.roles, user.availableQuestions, emailVerified),
          clientId = None,
          scope = None,
          redirectUri = None
        )
        )
      } yield {
        TokenResponse(
          "Bearer",
          accessToken.token,
          accessToken.expiresIn.getOrElse(1L),
          accessToken.refreshToken.getOrElse("")
        )
      }
    }

    override def updateOptInNewsletter(userId: UserId, optInNewsletter: Boolean): Future[Boolean] = {
      persistentUserService.updateOptInNewsletter(userId, optInNewsletter).map { result =>
        if (result) {
          eventBusService.publish(
            UserUpdatedOptInNewsletterEvent(
              connectedUserId = Some(userId),
              eventDate = DateHelper.now(),
              userId = userId,
              requestContext = RequestContext.empty,
              optInNewsletter = optInNewsletter
            )
          )
        }
        result
      }
    }

    override def updateIsHardBounce(userId: UserId, isHardBounce: Boolean): Future[Boolean] = {
      persistentUserService.updateIsHardBounce(userId, isHardBounce)
    }

    override def updateOptInNewsletter(email: String, optInNewsletter: Boolean): Future[Boolean] = {
      getUserByEmail(email).flatMap { maybeUser =>
        persistentUserService.updateOptInNewsletter(email, optInNewsletter).map { result =>
          if (maybeUser.isDefined && result) {
            val userId = maybeUser.get.userId
            eventBusService.publish(
              UserUpdatedOptInNewsletterEvent(
                connectedUserId = Some(userId),
                userId = userId,
                eventDate = DateHelper.now(),
                requestContext = RequestContext.empty,
                optInNewsletter = optInNewsletter
              )
            )
          }
          result
        }
      }
    }

    override def updateIsHardBounce(email: String, isHardBounce: Boolean): Future[Boolean] = {
      persistentUserService.updateIsHardBounce(email, isHardBounce)
    }

    override def updateLastMailingError(email: String, lastMailingError: Option[MailingErrorLog]): Future[Boolean] = {
      persistentUserService.updateLastMailingError(email, lastMailingError)
    }

    override def updateLastMailingError(userId: UserId, lastMailingError: Option[MailingErrorLog]): Future[Boolean] = {
      persistentUserService.updateLastMailingError(userId, lastMailingError)
    }

    override def findUsersForCrmSynchro(
      optIn: Option[Boolean],
      hardBounce: Option[Boolean],
      offset: Int,
      limit: Int
    ): Future[Seq[User]] = {
      persistentUserService.findUsersForCrmSynchro(optIn, hardBounce, offset, limit)
    }

    override def getUsersWithoutRegisterQuestion: Future[Seq[User]] = {
      persistentUserService.findUsersWithoutRegisterQuestion
    }

    private def updateProposalVotedByOrganisation(user: User): Future[Unit] = {
      if (user.userType == UserType.UserTypeOrganisation) {
        proposalService
          .searchProposalsVotedByUser(
            userId = user.userId,
            filterVotes = None,
            filterQualifications = None,
            sort = None,
            limit = None,
            skip = None,
            RequestContext.empty
          )
          .map(
            result =>
              result.results.foreach(
                proposal =>
                  eventBusService
                    .publish(ReindexProposal(proposal.id, DateHelper.now(), RequestContext.empty))
              )
          )
      } else {
        Future.successful({})
      }
    }

    private def updateProposalsSubmitByUser(user: User, requestContext: RequestContext): Future[Unit] = {
      proposalService
        .searchForUser(
          userId = Some(user.userId),
          query = SearchQuery(filters = Some(
            SearchFilters(
              user = Some(UserSearchFilter(userId = user.userId)),
              status = Some(StatusSearchFilter(ProposalStatus.values.filter(_ != ProposalStatus.Archived)))
            )
          )
          ),
          requestContext = requestContext
        )
        .map(
          result =>
            result.results.foreach(
              proposal =>
                eventBusService
                  .publish(ReindexProposal(proposal.id, DateHelper.now(), RequestContext.empty))
            )
        )
    }

    override def update(user: User, requestContext: RequestContext): Future[User] = {
      for {
        updatedUser <- persistentUserService.updateUser(user)
        _           <- updateProposalVotedByOrganisation(updatedUser)
        _           <- updateProposalsSubmitByUser(updatedUser, requestContext)
      } yield updatedUser
    }

    private def updatePersonalityEmail(
      personality: User,
      moderatorId: Option[UserId],
      newEmail: Option[String],
      oldEmail: String,
      requestContext: RequestContext
    ): Future[Unit] = {
      newEmail match {
        case Some(email) =>
          persistentUserToAnonymizeService.create(oldEmail).map { _ =>
            eventBusService.publish(
              PersonalityEmailChangedEvent(
                connectedUserId = moderatorId,
                userId = personality.userId,
                requestContext = requestContext,
                country = personality.country,
                language = personality.language,
                eventDate = DateHelper.now(),
                oldEmail = oldEmail,
                newEmail = email
              )
            )
          }
        case None => Future.successful {}
      }
    }

    override def updatePersonality(
      personality: User,
      moderatorId: Option[UserId],
      oldEmail: String,
      requestContext: RequestContext
    ): Future[User] = {

      val newEmail: Option[String] = personality.email match {
        case email if email.toLowerCase == oldEmail.toLowerCase => None
        case email                                              => Some(email)
      }

      for {
        updatedUser <- persistentUserService.updateUser(personality)
        _           <- updatePersonalityEmail(updatedUser, moderatorId, newEmail, oldEmail, requestContext)
        _           <- updateProposalVotedByOrganisation(updatedUser)
        _           <- updateProposalsSubmitByUser(updatedUser, requestContext)
      } yield updatedUser
    }

    override def anonymize(user: User, adminId: UserId, requestContext: RequestContext): Future[Unit] = {
      val anonymizedUser: User = user.copy(
        email = s"yopmail+${user.userId.value}@make.org",
        firstName = Some("DELETE_REQUESTED"),
        lastName = Some("DELETE_REQUESTED"),
        lastIp = None,
        hashedPassword = None,
        enabled = false,
        emailVerified = false,
        lastConnection = DateHelper.now(),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq(RoleCitizen),
        profile = Profile.parseProfile(optInNewsletter = false),
        isHardBounce = true,
        lastMailingError = None,
        organisationName = None,
        userType = UserType.UserTypeUser,
        createdAt = Some(DateHelper.now()),
        publicProfile = false
      )
      val futureDelete: Future[Unit] = for {
        _ <- persistentUserToAnonymizeService.create(user.email)
        _ <- persistentUserService.updateUser(anonymizedUser)
        _ <- persistentUserService.removeAnonymizedUserFromFollowedUserTable(user.userId)
        _ <- proposalService.anonymizeByUserId(user.userId)
      } yield {}
      futureDelete.map { _ =>
        eventBusService.publish(
          UserAnonymizedEvent(
            connectedUserId = Some(adminId),
            userId = user.userId,
            requestContext = requestContext,
            country = user.country,
            eventDate = DateHelper.now(),
            language = user.language,
            adminId = adminId
          )
        )
      }
    }

    override def getFollowedUsers(userId: UserId): Future[Seq[UserId]] = {
      persistentUserService.getFollowedUsers(userId).map(_.map(UserId(_)))
    }

    override def followUser(followedUserId: UserId, userId: UserId, requestContext: RequestContext): Future[UserId] = {
      persistentUserService.followUser(followedUserId, userId).map(_ => followedUserId).map { value =>
        eventBusService.publish(
          UserFollowEvent(
            connectedUserId = Some(userId),
            eventDate = DateHelper.now(),
            userId = userId,
            requestContext = requestContext,
            followedUserId = followedUserId
          )
        )
        value
      }
    }

    override def unfollowUser(
      followedUserId: UserId,
      userId: UserId,
      requestContext: RequestContext
    ): Future[UserId] = {
      persistentUserService.unfollowUser(followedUserId, userId).map(_ => followedUserId).map { value =>
        eventBusService.publish(
          UserUnfollowEvent(
            connectedUserId = Some(userId),
            userId = userId,
            eventDate = DateHelper.now(),
            requestContext = requestContext,
            unfollowedUserId = followedUserId
          )
        )
        value
      }
    }

    override def retrieveOrCreateVirtualUser(
      userInfo: AuthorRequest,
      country: Country,
      language: Language
    ): Future[User] = {
      // Take only 50 chars to avoid having values too large for the column
      val fullHash: String = tokenGenerator.tokenToHash(s"$userInfo")
      val hash = fullHash.substring(0, Math.min(50, fullHash.length)).toLowerCase()
      val email = s"yopmail+$hash@make.org"
      getUserByEmail(email).flatMap {
        case Some(user) => Future.successful(user)
        case None =>
          val dateOfBirth: Option[LocalDate] = userInfo.age.map(DateHelper.computeBirthDate)

          userService
            .register(
              UserRegisterData(
                email = email,
                password = None,
                lastIp = None,
                dateOfBirth = dateOfBirth,
                firstName = Some(userInfo.firstName),
                lastName = userInfo.lastName,
                profession = userInfo.profession,
                country = country,
                language = language,
                optIn = Some(false)
              ),
              RequestContext.empty
            )
      }
    }

    override def adminCountUsers(
      email: Option[String],
      firstName: Option[String],
      lastName: Option[String],
      role: Option[Role],
      userType: Option[UserType]
    ): Future[Int] = {
      persistentUserService.adminCountUsers(email, firstName, lastName, role, userType)
    }

    private def getConnectionModes(user: User): Seq[ConnectionMode] = {
      Map(
        ConnectionMode.Facebook -> user.profile.flatMap(_.facebookId),
        ConnectionMode.Google -> user.profile.flatMap(_.googleId),
        ConnectionMode.Mail -> user.hashedPassword
      ).collect {
        case (mode, Some(_)) => mode
      }.toSeq
    }

    override def reconnectInfo(userId: UserId): Future[Option[ReconnectInfo]] = {
      val futureReconnectInfo = for {
        user           <- persistentUserService.get(userId)
        reconnectToken <- generateReconnectToken()
        _              <- persistentUserService.updateReconnectToken(userId, reconnectToken, DateHelper.now())
      } yield (user, reconnectToken)

      futureReconnectInfo.map {
        case (maybeUser, reconnectToken) =>
          maybeUser.map { user =>
            val hiddenEmail = SecurityHelper.anonymizeEmail(user.email)
            ReconnectInfo(
              reconnectToken,
              user.firstName,
              user.profile.flatMap(_.avatarUrl),
              hiddenEmail,
              getConnectionModes(user)
            )
          }
      }
    }

    override def changeEmailVerificationTokenIfNeeded(userId: UserId): Future[Option[String]] = {
      getUser(userId).flatMap {
        case Some(user)
            // if last verification token was changed more than 10 minutes ago
            if user.verificationTokenExpiresAt
              .forall(_.minusSeconds(validationTokenExpiresIn).plusMinutes(10).isBefore(DateHelper.now()))
              && !user.emailVerified =>
          userTokenGenerator.generateVerificationToken().flatMap {
            case (_, token) =>
              persistentUserService
                .updateUser(
                  user.copy(
                    verificationToken = Some(token),
                    verificationTokenExpiresAt = Some(DateHelper.now().plusSeconds(validationTokenExpiresIn))
                  )
                )
                .map(_.verificationToken)
          }
        case _ => Future.successful(None)
      }
    }

    def changeAvatarForUser(
      userId: UserId,
      avatarUrl: String,
      requestContext: RequestContext,
      eventDate: ZonedDateTime
    ): Future[Unit] = {
      def extension(contentType: ContentType): String = contentType.mediaType.subType
      def destFn(contentType: ContentType): File =
        Files.createTempFile("user-upload-avatar", s".${extension(contentType)}").toFile

      downloadService
        .downloadImage(avatarUrl, destFn)
        .flatMap {
          case (contentType, tempFile) =>
            tempFile.deleteOnExit()
            storageService
              .uploadUserAvatar(userId, extension(contentType), contentType.value, FileContent(tempFile))
              .map(Option.apply)
        }
        .recover {
          case _: ImageUnavailable => None
        }
        .flatMap(
          path =>
            getUser(userId).flatMap {
              case Some(user) =>
                val newProfile: Option[Profile] = user.profile match {
                  case Some(profile) => Some(profile.copy(avatarUrl = path))
                  case None          => Profile.parseProfile(avatarUrl = path)
                }
                update(user.copy(profile = newProfile), requestContext).map(_ => path)
              case None =>
                logger.warn(s"Could not find user $userId to update avatar")
                Future.successful(path)
            }
        )
        .map { _ =>
          userHistoryCoordinatorService.logHistory(
            LogUserUploadedAvatarEvent(
              userId = userId,
              requestContext = requestContext,
              action = UserAction(
                date = eventDate,
                actionType = LogUserUploadedAvatarEvent.action,
                arguments = UploadedAvatar(avatarUrl = avatarUrl)
              )
            )
          )
        }

    }

    override def adminUpdateUserEmail(user: User, email: String): Future[Unit] = {
      val toDelete = user.email
      // do not initialize the futures before the comprehension:
      // we actually want to anonymize only if update was successful
      for {
        _ <- persistentUserService.updateUser(user.copy(email = email))
        _ <- persistentUserToAnonymizeService.create(toDelete)
      } yield ()
    }

  }
}
