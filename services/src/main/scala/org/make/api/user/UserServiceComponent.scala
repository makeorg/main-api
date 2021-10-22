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

import java.time.{LocalDate, ZonedDateTime}

import org.make.api.question.AuthorRequest
import org.make.api.technical.auth.TokenResponse
import org.make.api.technical.job.JobActor.Protocol.Response.JobAcceptance
import org.make.api.user.social.models.UserInfo
import org.make.core.profile.{Gender, SocioProfessionalCategory}
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.user._
import org.make.core.{Order, RequestContext}

import scala.concurrent.Future
import org.make.core.technical.Pagination.{End, Start}

trait UserServiceComponent {
  def userService: UserService
}

trait UserService {
  def getUser(id: UserId): Future[Option[User]]
  def getPersonality(id: UserId): Future[Option[User]]
  def getUserByEmail(email: String): Future[Option[User]]
  def getUserByUserIdAndPassword(userId: UserId, password: Option[String]): Future[Option[User]]
  def getUserByEmailAndPassword(email: String, password: String): Future[Option[User]]
  def getUsersByUserIds(ids: Seq[UserId]): Future[Seq[User]]
  def adminFindUsers(
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
    questionId: Option[QuestionId],
    country: Country,
    requestContext: RequestContext,
    privacyPolicyApprovalDate: Option[ZonedDateTime]
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
  def getUsersWithoutRegisterQuestion: Future[Seq[User]]
  def anonymize(user: User, adminId: UserId, requestContext: RequestContext, mode: Anonymization): Future[Unit]
  def anonymizeInactiveUsers(adminId: UserId, requestContext: RequestContext): Future[JobAcceptance]
  def getFollowedUsers(userId: UserId): Future[Seq[UserId]]
  def followUser(followedUserId: UserId, userId: UserId, requestContext: RequestContext): Future[UserId]
  def unfollowUser(followedUserId: UserId, userId: UserId, requestContext: RequestContext): Future[UserId]
  def retrieveOrCreateVirtualUser(userInfo: AuthorRequest, country: Country): Future[User]
  def adminCountUsers(
    ids: Option[Seq[UserId]],
    email: Option[String],
    firstName: Option[String],
    lastName: Option[String],
    role: Option[Role],
    userType: Option[UserType]
  ): Future[Int]
  def reconnectInfo(userId: UserId): Future[Option[ReconnectInfo]]
  def changeEmailVerificationTokenIfNeeded(userId: UserId): Future[Option[User]]
  def changeAvatarForUser(
    userId: UserId,
    avatarUrl: String,
    requestContext: RequestContext,
    eventDate: ZonedDateTime
  ): Future[Unit]
  def adminUpdateUserEmail(user: User, email: String): Future[Unit]
}

final case class UserRegisterData(
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
  questionId: Option[QuestionId] = None,
  optIn: Option[Boolean] = None,
  optInPartner: Option[Boolean] = None,
  roles: Seq[Role] = Seq(Role.RoleCitizen),
  availableQuestions: Seq[QuestionId] = Seq.empty,
  politicalParty: Option[String] = None,
  website: Option[String] = None,
  publicProfile: Boolean = false,
  legalMinorConsent: Option[Boolean] = None,
  legalAdvisorApproval: Option[Boolean] = None,
  privacyPolicyApprovalDate: Option[ZonedDateTime] = None
)

final case class PersonalityRegisterData(
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  gender: Option[Gender],
  genderName: Option[String],
  country: Country,
  description: Option[String],
  avatarUrl: Option[String],
  website: Option[String],
  politicalParty: Option[String]
)
