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
import akka.stream.scaladsl.Sink
import com.github.t3hnar.bcrypt._
import eu.timepit.refined.auto._
import grizzled.slf4j.Logging
import org.make.api.extensions.{MailJetConfigurationComponent, MakeSettingsComponent}
import org.make.api.partner.PersistentPartnerServiceComponent
import org.make.api.proposal.ProposalServiceComponent
import org.make.api.proposal.PublishedProposalEvent.ReindexProposal
import org.make.api.question.AuthorRequest
import org.make.api.technical._
import org.make.api.technical.auth.{
  MakeDataHandlerComponent,
  TokenGeneratorComponent,
  TokenResponse,
  UserTokenGeneratorComponent
}
import org.make.api.technical.crm.{CrmServiceComponent, PersistentCrmUserServiceComponent}
import org.make.api.technical.Futures._
import org.make.api.technical.job.JobActor.Protocol.Response.JobAcceptance
import org.make.api.technical.job.JobCoordinatorServiceComponent
import org.make.api.technical.security.SecurityHelper
import org.make.api.technical.storage.Content.FileContent
import org.make.api.technical.storage.StorageServiceComponent
import org.make.api.user.UserExceptions.{EmailAlreadyRegisteredException, EmailNotAllowed}
import org.make.api.user.social.models.UserInfo
import org.make.api.user.social.models.google.PeopleInfo
import org.make.api.user.validation.UserRegistrationValidatorComponent
import org.make.api.userhistory._
import org.make.core.auth.UserRights
import org.make.core.job.Job.JobId.AnonymizeInactiveUsers
import org.make.core.profile.Gender.{Female, Male, Other}
import org.make.core.profile.Profile
import org.make.core.proposal._
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.user.Role.RoleCitizen
import org.make.core.user._
import org.make.core.{DateHelper, DateHelperComponent, Order, RequestContext, ValidationError, ValidationFailedError}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.make.core.technical.Pagination.{End, Start}

import scala.util.{Failure, Success}

trait DefaultUserServiceComponent extends UserServiceComponent with ShortenedNames with Logging {
  this: ActorSystemComponent
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with UserTokenGeneratorComponent
    with PersistentUserServiceComponent
    with PersistentUserToAnonymizeServiceComponent
    with PersistentCrmUserServiceComponent
    with ProposalServiceComponent
    with CrmServiceComponent
    with EventBusServiceComponent
    with TokenGeneratorComponent
    with MakeSettingsComponent
    with UserRegistrationValidatorComponent
    with StorageServiceComponent
    with DownloadServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with JobCoordinatorServiceComponent
    with MailJetConfigurationComponent
    with DateHelperComponent
    with PersistentPartnerServiceComponent =>

  override lazy val userService: UserService = new DefaultUserService

  class DefaultUserService extends UserService {

    val validationTokenExpiresIn: Long = makeSettings.validationTokenExpiresIn.toSeconds
    val resetTokenExpiresIn: Long = makeSettings.resetTokenExpiresIn.toSeconds
    val resetTokenB2BExpiresIn: Long = makeSettings.resetTokenB2BExpiresIn.toSeconds

    private lazy val batchSize: Int = mailJetConfiguration.userListBatchSize

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

    override def getUserByEmailAndPassword(email: String, password: String): Future[Option[User]] = {
      persistentUserService.findByEmailAndPassword(email, password)
    }

    override def getUsersByUserIds(ids: Seq[UserId]): Future[Seq[User]] = {
      persistentUserService.findAllByUserIds(ids)
    }

    override def adminFindUsers(
      start: Start,
      end: Option[End],
      sort: Option[String],
      order: Option[Order],
      ids: Option[Seq[UserId]],
      email: Option[String],
      firstName: Option[String],
      lastName: Option[String],
      role: Option[Role],
      userType: Option[UserType]
    ): Future[Seq[User]] = {
      persistentUserService.adminFindUsers(start, end, sort, order, ids, email, firstName, lastName, role, userType)
    }

    private def registerUser(
      userRegisterData: UserRegisterData,
      lowerCasedEmail: String,
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
        lastConnection = Some(dateHelper.now()),
        verificationToken = Some(hashedVerificationToken),
        verificationTokenExpiresAt = Some(dateHelper.now().plusSeconds(validationTokenExpiresIn)),
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = userRegisterData.roles,
        country = userRegisterData.country,
        profile = profile,
        availableQuestions = userRegisterData.availableQuestions,
        anonymousParticipation = makeSettings.defaultUserAnonymousParticipation,
        userType = UserType.UserTypeUser,
        publicProfile = userRegisterData.publicProfile,
        privacyPolicyApprovalDate = userRegisterData.privacyPolicyApprovalDate
      )

      persistentUserService.persist(user)
    }

    private def persistPersonality(
      personalityRegisterData: PersonalityRegisterData,
      lowerCasedEmail: String,
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
        lastConnection = None,
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = Some(resetToken),
        resetTokenExpiresAt = Some(dateHelper.now().plusSeconds(resetTokenB2BExpiresIn)),
        roles = Seq(Role.RoleCitizen),
        country = personalityRegisterData.country,
        profile = profile,
        availableQuestions = Seq.empty,
        anonymousParticipation = makeSettings.defaultUserAnonymousParticipation,
        userType = UserType.UserTypePersonality,
        publicProfile = true,
        privacyPolicyApprovalDate = Some(DateHelper.now())
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
        Future.unit
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
        user                    <- registerUser(userRegisterData, lowerCasedEmail, profile, hashedVerificationToken)
      } yield user

      result.map { user =>
        eventBusService.publish(
          UserRegisteredEvent(
            connectedUserId = Some(user.userId),
            userId = user.userId,
            requestContext = requestContext,
            firstName = user.firstName,
            country = user.country,
            optInPartner = user.profile.flatMap(_.optInPartner),
            registerQuestionId = user.profile.flatMap(_.registerQuestionId),
            eventDate = dateHelper.now(),
            eventId = Some(idGenerator.nextEventId())
          )
        )
        user
      }
    }

    override def registerPersonality(
      personalityRegisterData: PersonalityRegisterData,
      requestContext: RequestContext
    ): Future[User] = {

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
        user        <- persistPersonality(personalityRegisterData, lowerCasedEmail, profile, resetToken)
      } yield user

      result.map { user =>
        eventBusService.publish(
          PersonalityRegisteredEvent(
            connectedUserId = Some(user.userId),
            userId = user.userId,
            requestContext = requestContext,
            email = user.email,
            country = user.country,
            eventDate = dateHelper.now(),
            eventId = Some(idGenerator.nextEventId())
          )
        )
        user
      }
    }

    override def createOrUpdateUserFromSocial(
      userInfo: UserInfo,
      questionId: Option[QuestionId],
      country: Country,
      requestContext: RequestContext,
      privacyPolicyApprovalDate: Option[ZonedDateTime]
    ): Future[(User, Boolean)] = {

      userInfo.email
        .filter(_ != "")
        .map(_.toLowerCase())
        .map { lowerCasedEmail =>
          persistentUserService.findByEmail(lowerCasedEmail).flatMap {
            case Some(user) =>
              updateUserFromSocial(user, userInfo, requestContext.ipAddress, privacyPolicyApprovalDate)
                .map((_, false))
            case None =>
              createUserFromSocial(
                lowerCasedEmail,
                requestContext,
                userInfo,
                country,
                questionId,
                privacyPolicyApprovalDate
              ).map((_, true))
          }
        }
        .getOrElse {
          logger.error(
            s"We couldn't find any email on social login. UserInfo: $userInfo, requestContext: $requestContext"
          )
          Future
            .failed(ValidationFailedError(Seq(ValidationError("email", "missing", Some("No email found for user")))))
        }
    }

    private def createUserFromSocial(
      lowerCasedEmail: String,
      requestContext: RequestContext,
      userInfo: UserInfo,
      country: Country,
      questionId: Option[QuestionId],
      privacyPolicyApprovalDate: Option[ZonedDateTime]
    ): Future[User] = {

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
        lastIp = requestContext.ipAddress,
        hashedPassword = None,
        enabled = true,
        emailVerified = true,
        lastConnection = Some(dateHelper.now()),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = getRolesFromSocial(userInfo),
        country = country,
        profile = profile,
        availableQuestions = Seq.empty,
        anonymousParticipation = makeSettings.defaultUserAnonymousParticipation,
        userType = UserType.UserTypeUser,
        privacyPolicyApprovalDate = privacyPolicyApprovalDate
      )

      persistentUserService.persist(user).map { user =>
        publishCreateEventsFromSocial(user = user, requestContext = requestContext)
        user
      }
    }

    private def updateUserFromSocial(
      user: User,
      userInfo: UserInfo,
      clientIp: Option[String],
      privacyPolicyApprovalDate: Option[ZonedDateTime]
    ): Future[User] = {
      val hashedPassword = if (!user.emailVerified) None else user.hashedPassword

      val profile: Option[Profile] = user.profile.orElse(Profile.parseProfile())
      val updatedProfile: Option[Profile] = profile.map(
        _.copy(facebookId = userInfo.facebookId, googleId = userInfo.googleId, gender = userInfo.gender.map {
          case "male"   => Male
          case "female" => Female
          case _        => Other
        }, genderName = userInfo.gender, dateOfBirth = profile.flatMap(_.dateOfBirth).orElse(userInfo.dateOfBirth))
      )
      val updatedUser: User =
        user.copy(
          firstName = userInfo.firstName,
          lastIp = clientIp,
          profile = updatedProfile,
          hashedPassword = hashedPassword,
          emailVerified = true,
          privacyPolicyApprovalDate = privacyPolicyApprovalDate.orElse(user.privacyPolicyApprovalDate)
        )

      persistentUserService.updateSocialUser(updatedUser).map { userUpdated =>
        if (userUpdated) updatedUser else user
      }
    }

    // @todo: Add a unit test to check role by domain
    private def getRolesFromSocial(userInfo: UserInfo): Seq[Role] = {
      var roles: Seq[Role] = Seq(Role.RoleCitizen)
      if (userInfo.domain.contains(PeopleInfo.MODERATOR_DOMAIN)) {
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
          firstName = user.firstName,
          country = user.country,
          isSocialLogin = true,
          optInPartner = user.profile.flatMap(_.optInPartner),
          registerQuestionId = user.profile.flatMap(_.registerQuestionId),
          eventDate = dateHelper.now(),
          eventId = Some(idGenerator.nextEventId())
        )
      )
      eventBusService.publish(
        UserValidatedAccountEvent(
          userId = user.userId,
          country = user.country,
          requestContext = requestContext,
          isSocialLogin = true,
          eventDate = dateHelper.now(),
          eventId = Some(idGenerator.nextEventId())
        )
      )
      user.profile.flatMap(_.avatarUrl).foreach { avatarUrl =>
        eventBusService.publish(
          UserUploadAvatarEvent(
            connectedUserId = Some(user.userId),
            userId = user.userId,
            country = user.country,
            requestContext = requestContext,
            avatarUrl = avatarUrl,
            eventDate = dateHelper.now(),
            eventId = Some(idGenerator.nextEventId())
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
          Some(dateHelper.now().plusSeconds(resetTokenExpiresIn))
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
        TokenResponse.fromAccessToken(accessToken)
      }
    }

    override def updateOptInNewsletter(userId: UserId, optInNewsletter: Boolean): Future[Boolean] = {
      persistentUserService.updateOptInNewsletter(userId, optInNewsletter).map { result =>
        if (result) {
          eventBusService.publish(
            UserUpdatedOptInNewsletterEvent(
              connectedUserId = Some(userId),
              eventDate = dateHelper.now(),
              userId = userId,
              requestContext = RequestContext.empty,
              optInNewsletter = optInNewsletter,
              eventId = Some(idGenerator.nextEventId())
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
          if (result) {
            maybeUser.foreach { user =>
              val userId = user.userId
              eventBusService.publish(
                UserUpdatedOptInNewsletterEvent(
                  connectedUserId = Some(userId),
                  userId = userId,
                  eventDate = dateHelper.now(),
                  requestContext = RequestContext.empty,
                  optInNewsletter = optInNewsletter,
                  eventId = Some(idGenerator.nextEventId())
                )
              )
            }
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
                    .publish(
                      ReindexProposal(
                        proposal.id,
                        dateHelper.now(),
                        RequestContext.empty,
                        Some(idGenerator.nextEventId())
                      )
                    )
              )
          )
      } else {
        Future.unit
      }
    }

    private def updateProposalsSubmitByUser(user: User, requestContext: RequestContext): Future[Unit] = {
      proposalService
        .searchForUser(
          userId = Some(user.userId),
          query = SearchQuery(filters = Some(
            SearchFilters(
              users = Some(UserSearchFilter(userIds = Seq(user.userId))),
              status = Some(StatusSearchFilter(ProposalStatus.values))
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
                  .publish(
                    ReindexProposal(
                      proposal.id,
                      dateHelper.now(),
                      RequestContext.empty,
                      Some(idGenerator.nextEventId())
                    )
                  )
            )
        )
    }

    private def handleEmailChangeIfNecessary(oldEmail: String, newEmail: String): Future[Unit] = {
      if (oldEmail == newEmail) {
        Future.unit
      } else {
        persistentUserToAnonymizeService.create(oldEmail)
      }
    }

    override def update(user: User, requestContext: RequestContext): Future[User] = {
      for {
        previousUser <- persistentUserService.get(user.userId)
        updatedUser  <- persistentUserService.updateUser(user)
        _            <- handleEmailChangeIfNecessary(previousUser.map(_.email).getOrElse(user.email), user.email)
        _            <- updateProposalVotedByOrganisation(updatedUser)
        _            <- updateProposalsSubmitByUser(updatedUser, requestContext)
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
                eventDate = dateHelper.now(),
                oldEmail = oldEmail,
                newEmail = email,
                eventId = Some(idGenerator.nextEventId())
              )
            )
          }
        case None => Future.unit
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

    override def anonymize(
      user: User,
      adminId: UserId,
      requestContext: RequestContext,
      mode: Anonymization
    ): Future[Unit] = {

      val anonymizedUser: User = user.copy(
        email = s"yopmail+${user.userId.value}@make.org",
        firstName = if (mode == Anonymization.Explicit) Some("DELETE_REQUESTED") else user.firstName,
        lastName = Some("DELETE_REQUESTED"),
        lastIp = None,
        hashedPassword = None,
        enabled = false,
        emailVerified = false,
        lastConnection = None,
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq(RoleCitizen),
        profile = Profile.parseProfile(optInNewsletter = false),
        isHardBounce = true,
        lastMailingError = None,
        organisationName = None,
        userType = if (mode == Anonymization.Explicit) UserType.UserTypeAnonymous else user.userType,
        createdAt = Some(dateHelper.now()),
        publicProfile = false
      )

      def unlinkUser(user: User): Future[Unit] = {
        val unlinkPartners = user.userType match {
          case UserType.UserTypeOrganisation =>
            persistentPartnerService
              .find(
                start = Start.zero,
                end = None,
                sort = None,
                order = None,
                questionId = None,
                organisationId = Some(user.userId),
                partnerKind = None
              )
              .flatMap { partners =>
                Future.traverse(partners) { partner =>
                  persistentPartnerService.modify(partner.copy(organisationId = None))
                }
              }
          case _ => Future.unit
        }
        for {
          _ <- unlinkPartners
          _ <- persistentUserService.removeAnonymizedUserFromFollowedUserTable(user.userId)
        } yield {}
      }

      val futureDelete: Future[Unit] = for {
        _ <- persistentUserToAnonymizeService.create(user.email)
        _ <- persistentUserService.updateUser(anonymizedUser)
        _ <- unlinkUser(user)
        _ <- userHistoryCoordinatorService.delete(user.userId)
        _ <- updateProposalsSubmitByUser(user, requestContext)
      } yield {}
      futureDelete.map { _ =>
        eventBusService.publish(
          UserAnonymizedEvent(
            connectedUserId = Some(adminId),
            userId = user.userId,
            requestContext = requestContext,
            country = user.country,
            eventDate = dateHelper.now(),
            adminId = adminId,
            mode = mode,
            eventId = Some(idGenerator.nextEventId())
          )
        )
      }
    }

    override def anonymizeInactiveUsers(adminId: UserId, requestContext: RequestContext): Future[JobAcceptance] = {
      val startTime: Long = System.currentTimeMillis()

      jobCoordinatorService.start(AnonymizeInactiveUsers) { _ =>
        val anonymizeUsers = StreamUtils
          .asyncPageToPageSource(persistentCrmUserService.findInactiveUsers(_, batchSize))
          .mapAsync(1) { crmUsers =>
            persistentUserService.findAllByUserIds(crmUsers.map(user => UserId(user.userId)))
          }
          .mapConcat(identity)
          .mapAsync(1) { user =>
            anonymize(user, adminId, requestContext, Anonymization.Automatic)
          }
          .runWith(Sink.ignore)
          .toUnit

        anonymizeUsers.onComplete {
          case Failure(exception) =>
            logger.error(s"Inactive users anonymization failed:", exception)
          case Success(_) =>
            logger.info(s"Inactive users anonymization succeeded in ${System.currentTimeMillis() - startTime}ms")
        }

        anonymizeUsers
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
            eventDate = dateHelper.now(),
            userId = userId,
            requestContext = requestContext,
            followedUserId = followedUserId,
            eventId = Some(idGenerator.nextEventId())
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
            eventDate = dateHelper.now(),
            requestContext = requestContext,
            unfollowedUserId = followedUserId,
            eventId = Some(idGenerator.nextEventId())
          )
        )
        value
      }
    }

    override def retrieveOrCreateVirtualUser(userInfo: AuthorRequest, country: Country): Future[User] = {
      // Take only 50 chars to avoid having values too large for the column
      val fullHash: String = tokenGenerator.tokenToHash(s"$userInfo")
      val hash = fullHash.substring(0, Math.min(50, fullHash.length)).toLowerCase()
      val email = s"yopmail+$hash@make.org"
      getUserByEmail(email).flatMap {
        case Some(user) => Future.successful(user)
        case None =>
          val dateOfBirth: Option[LocalDate] = userInfo.age.map(dateHelper.computeBirthDate)

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
                optIn = Some(false)
              ),
              RequestContext.empty
            )
      }
    }

    override def adminCountUsers(
      ids: Option[Seq[UserId]],
      email: Option[String],
      firstName: Option[String],
      lastName: Option[String],
      role: Option[Role],
      userType: Option[UserType]
    ): Future[Int] = {
      persistentUserService.adminCountUsers(ids, email, firstName, lastName, role, userType)
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
        _              <- persistentUserService.updateReconnectToken(userId, reconnectToken, dateHelper.now())
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

    override def changeEmailVerificationTokenIfNeeded(userId: UserId): Future[Option[User]] = {
      getUser(userId).flatMap {
        case Some(user)
            // if last verification token was changed more than 10 minutes ago
            if user.verificationTokenExpiresAt
              .forall(_.minusSeconds(validationTokenExpiresIn).plusMinutes(10).isBefore(dateHelper.now()))
              && !user.emailVerified =>
          userTokenGenerator.generateVerificationToken().flatMap {
            case (_, token) =>
              persistentUserService
                .updateUser(
                  user.copy(
                    verificationToken = Some(token),
                    verificationTokenExpiresAt = Some(dateHelper.now().plusSeconds(validationTokenExpiresIn))
                  )
                )
                .map(Some(_))
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
              .uploadUserAvatar(extension(contentType), contentType.value, FileContent(tempFile))
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
