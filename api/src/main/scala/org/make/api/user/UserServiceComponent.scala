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

import java.time.LocalDate

import com.github.t3hnar.bcrypt._
import com.typesafe.scalalogging.StrictLogging
import org.make.api.proposal.ProposalServiceComponent
import org.make.api.proposal.PublishedProposalEvent.ReindexProposal
import org.make.api.question.AuthorRequest
import org.make.api.technical.auth.{TokenGeneratorComponent, UserTokenGeneratorComponent}
import org.make.api.technical.businessconfig.BusinessConfig
import org.make.api.technical.crm.CrmServiceComponent
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, ShortenedNames}
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user.social.models.UserInfo
import org.make.api.user.social.models.google.{UserInfo => GoogleUserInfo}
import org.make.api.userhistory.UserEvent._
import org.make.core.profile.Gender.{Female, Male, Other}
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.proposal._
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.user.Role.RoleCitizen
import org.make.core.user._
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

trait UserServiceComponent {
  def userService: UserService
}

trait UserService extends ShortenedNames {
  def getUser(id: UserId): Future[Option[User]]
  def getUserByEmail(email: String): Future[Option[User]]
  def getUserByUserIdAndPassword(userId: UserId, password: Option[String]): Future[Option[User]]
  def getUsersByUserIds(ids: Seq[UserId]): Future[Seq[User]]
  def adminFindUsers(start: Int,
                     end: Option[Int],
                     sort: Option[String],
                     order: Option[String],
                     email: Option[String],
                     firstName: Option[String],
                     role: Option[Role]): Future[Seq[User]]
  def register(userRegisterData: UserRegisterData, requestContext: RequestContext): Future[User]
  def update(user: User, requestContext: RequestContext): Future[User]
  def createOrUpdateUserFromSocial(userInfo: UserInfo,
                                   clientIp: Option[String],
                                   questionId: Option[QuestionId],
                                   requestContext: RequestContext): Future[User]
  def requestPasswordReset(userId: UserId): Future[Boolean]
  def updatePassword(userId: UserId, resetToken: Option[String], password: String): Future[Boolean]
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
  def getUsersWithoutRegisterQuestion: Future[Seq[User]]
  def anonymize(user: User, adminId: UserId, requestContext: RequestContext): Future[Unit]
  def getFollowedUsers(userId: UserId): Future[Seq[UserId]]
  def followUser(followedUserId: UserId, userId: UserId, requestContext: RequestContext): Future[UserId]
  def unfollowUser(followedUserId: UserId, userId: UserId, requestContext: RequestContext): Future[UserId]
  def retrieveOrCreateVirtualUser(userInfo: AuthorRequest, country: Country, language: Language): Future[User]
  def adminCountUsers(email: Option[String], firstName: Option[String], role: Option[Role]): Future[Int]
}

case class UserRegisterData(email: String,
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
                            availableQuestions: Seq[QuestionId] = Seq.empty)

trait DefaultUserServiceComponent extends UserServiceComponent with ShortenedNames with StrictLogging {
  this: IdGeneratorComponent
    with UserTokenGeneratorComponent
    with PersistentUserServiceComponent
    with PersistentUserToAnonymizeServiceComponent
    with ProposalServiceComponent
    with CrmServiceComponent
    with EventBusServiceComponent
    with TokenGeneratorComponent =>

  val userService: UserService = new UserService {

    val validationTokenExpiresIn: Long = 30.days.toSeconds
    val resetTokenExpiresIn: Long = 1.days.toSeconds

    override def getUser(userId: UserId): Future[Option[User]] = {
      persistentUserService.get(userId)
    }

    override def getUserByEmail(email: String): Future[Option[User]] = {
      persistentUserService.findByEmail(email)
    }

    override def getUserByUserIdAndPassword(userId: UserId, password: Option[String]): Future[Option[User]] = {
      password.map { pass =>
        persistentUserService.findByUserIdAndPassword(userId, pass)
      }.getOrElse(getUser(userId))
    }

    override def getUsersByUserIds(ids: Seq[UserId]): Future[Seq[User]] = {
      persistentUserService.findAllByUserIds(ids)
    }

    override def adminFindUsers(start: Int,
                                end: Option[Int],
                                sort: Option[String],
                                order: Option[String],
                                email: Option[String],
                                firstName: Option[String],
                                role: Option[Role]): Future[Seq[User]] = {
      persistentUserService.adminFindUsers(start, end, sort, order, email, firstName, role)
    }

    private def registerUser(userRegisterData: UserRegisterData,
                             lowerCasedEmail: String,
                             country: Country,
                             language: Language,
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
        roles = userRegisterData.roles,
        country = country,
        language = language,
        profile = profile,
        availableQuestions = userRegisterData.availableQuestions
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
          optInPartner = userRegisterData.optInPartner
        )

      val result = for {
        emailExists             <- persistentUserService.emailExists(lowerCasedEmail)
        hashedVerificationToken <- generateVerificationToken(lowerCasedEmail, emailExists)
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

    override def createOrUpdateUserFromSocial(userInfo: UserInfo,
                                              clientIp: Option[String],
                                              questionId: Option[QuestionId],
                                              requestContext: RequestContext): Future[User] = {

      val lowerCasedEmail: String = userInfo.email.map(_.toLowerCase()).getOrElse("")

      persistentUserService.findByEmail(lowerCasedEmail).flatMap {
        case Some(user) => updateUserFromSocial(user, userInfo, clientIp)
        case None       => createUserFromSocial(requestContext, userInfo, questionId, clientIp)
      }
    }

    private def createUserFromSocial(requestContext: RequestContext,
                                     userInfo: UserInfo,
                                     questionId: Option[QuestionId],
                                     clientIp: Option[String]): Future[User] = {

      val country = BusinessConfig.validateCountry(Country(userInfo.country))
      val language = BusinessConfig.validateLanguage(Country(userInfo.country), Language(userInfo.language))
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
          genderName = userInfo.gender,
          registerQuestionId = questionId
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
        profile = profile,
        availableQuestions = Seq.empty
      )

      persistentUserService.persist(user).map { user =>
        publishCreateEventsFromSocial(user = user, requestContext = requestContext)
        user
      }
    }

    private def updateUserFromSocial(user: User, userInfo: UserInfo, clientIp: Option[String]): Future[User] = {
      val country = BusinessConfig.validateCountry(Country(userInfo.country))
      val language = BusinessConfig.validateLanguage(Country(userInfo.country), Language(userInfo.language))

      val updatedProfile: Option[Profile] = user.profile.map {
        _.copy(
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
      }.orElse {
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
      }
      val updatedUser: User =
        user.copy(
          firstName = userInfo.firstName,
          lastName = userInfo.lastName,
          lastIp = clientIp,
          country = country,
          language = language,
          profile = updatedProfile
        )

      if (user == updatedUser) {
        Future.successful(user)
      } else {
        persistentUserService.updateSocialUser(updatedUser).map { userUpdated =>
          if (userUpdated) updatedUser else user
        }
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

    override def validateEmail(verificationToken: String): Future[Boolean] = {
      persistentUserService.validateEmail(verificationToken)
    }

    override def updateOptInNewsletter(userId: UserId, optInNewsletter: Boolean): Future[Boolean] = {
      persistentUserService.updateOptInNewsletter(userId, optInNewsletter).map { result =>
        if (result) {
          eventBusService.publish(
            UserUpdatedOptInNewsletterEvent(
              connectedUserId = Some(userId),
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

    override def getUsersWithHardBounce(page: Int, limit: Int): Future[Seq[User]] = {
      persistentUserService.findUsersWithHardBounce(page: Int, limit: Int)
    }

    override def getOptInUsers(page: Int, limit: Int): Future[Seq[User]] = {
      persistentUserService.findOptInUsers(page: Int, limit: Int)
    }

    override def getOptOutUsers(page: Int, limit: Int): Future[Seq[User]] = {
      persistentUserService.findOptOutUsers(page: Int, limit: Int)
    }

    override def getUsersWithoutRegisterQuestion: Future[Seq[User]] = {
      persistentUserService.findUsersWithoutRegisterQuestion
    }

    private def updateProposalVotedByOrganisation(user: User): Future[Unit] = {
      if (user.isOrganisation) {
        proposalService
          .searchProposalsVotedByUser(user.userId, None, None, RequestContext.empty)
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
          query = SearchQuery(
            filters = Some(
              SearchFilters(
                user = Some(UserSearchFilter(userId = user.userId)),
                status = Some(StatusSearchFilter(ProposalStatus.statusMap.filter {
                  case (_, status) => status != ProposalStatus.Archived
                }.values.toSeq))
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

    override def anonymize(user: User, adminId: UserId, requestContext: RequestContext): Future[Unit] = {
      val anonymizedUser: User = user.copy(
        email = s"yopmail+${user.userId.value}@make.org",
        firstName = Some("DELETE_REQUESTED"),
        lastName = Some("DELETE_REQUESTED"),
        lastIp = None,
        hashedPassword = None,
        enabled = false,
        emailVerified = false,
        isOrganisation = false,
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
            userId = userId,
            requestContext = requestContext,
            followedUserId = followedUserId
          )
        )
        value
      }
    }

    override def unfollowUser(followedUserId: UserId,
                              userId: UserId,
                              requestContext: RequestContext): Future[UserId] = {
      persistentUserService.unfollowUser(followedUserId, userId).map(_ => followedUserId).map { value =>
        eventBusService.publish(
          UserUnfollowEvent(
            connectedUserId = Some(userId),
            userId = userId,
            requestContext = requestContext,
            unfollowedUserId = followedUserId
          )
        )
        value
      }
    }

    override def retrieveOrCreateVirtualUser(userInfo: AuthorRequest,
                                             country: Country,
                                             language: Language): Future[User] = {
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

    override def adminCountUsers(email: Option[String], firstName: Option[String], role: Option[Role]): Future[Int] = {
      persistentUserService.adminCountUsers(email, firstName, role)
    }
  }
}
