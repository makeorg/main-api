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

import java.time.ZonedDateTime
import org.make.api.user.PersistentUserService.UpdateFailed
import org.make.core.Order
import org.make.core.technical.Pagination._
import org.make.core.user._

import scala.concurrent.Future

trait PersistentUserServiceComponent {
  def persistentUserService: PersistentUserService
}

trait PersistentUserService {
  def get(uuid: UserId): Future[Option[User]]
  def findAllByUserIds(ids: Seq[UserId]): Future[Seq[User]]
  def findByEmailAndPassword(email: String, hashedPassword: String): Future[Option[User]]
  def findByUserIdAndPassword(userId: UserId, hashedPassword: Option[String]): Future[Option[User]]
  def findByUserIdAndUserType(userId: UserId, userType: UserType): Future[Option[User]]
  def findByEmail(email: String): Future[Option[User]]
  def adminFindUsers(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    ids: Option[Seq[UserId]],
    email: Option[String],
    firstName: Option[String],
    lastName: Option[String],
    maybeRole: Option[Role],
    maybeUserType: Option[UserType]
  ): Future[Seq[User]]
  def findAllOrganisations(): Future[Seq[User]]
  def findOrganisations(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    ids: Option[Seq[UserId]],
    organisationName: Option[String]
  ): Future[Seq[User]]
  def findUserIdByEmail(email: String): Future[Option[UserId]]
  def findUserByUserIdAndResetToken(userId: UserId, resetToken: String): Future[Option[User]]
  def findUserByUserIdAndVerificationToken(userId: UserId, verificationToken: String): Future[Option[User]]
  def findByReconnectTokenAndPassword(
    reconnectToken: String,
    password: String,
    validityReconnectToken: Int
  ): Future[Option[User]]
  def emailExists(email: String): Future[Boolean]
  def verificationTokenExists(verificationToken: String): Future[Boolean]
  def resetTokenExists(resetToken: String): Future[Boolean]
  def persist(user: User): Future[User]
  def updateUser(user: User): Future[User]
  def modifyOrganisation(organisation: User): Future[Either[UpdateFailed, User]]
  def requestResetPassword(
    userId: UserId,
    resetToken: String,
    resetTokenExpiresAt: Option[ZonedDateTime]
  ): Future[Boolean]
  def updatePassword(userId: UserId, resetToken: Option[String], hashedPassword: String): Future[Boolean]
  def validateEmail(verificationToken: String): Future[Boolean]
  def updateOptInNewsletter(userId: UserId, optInNewsletter: Boolean): Future[Boolean]
  def updateIsHardBounce(userId: UserId, isHardBounce: Boolean): Future[Boolean]
  def updateLastMailingError(userId: UserId, lastMailingError: Option[MailingErrorLog]): Future[Boolean]
  def updateOptInNewsletter(email: String, optInNewsletter: Boolean): Future[Boolean]
  def updateIsHardBounce(email: String, isHardBounce: Boolean): Future[Boolean]
  def updateLastMailingError(email: String, lastMailingError: Option[MailingErrorLog]): Future[Boolean]
  def updateSocialUser(user: User): Future[Boolean]
  def findUsersWithoutRegisterQuestion: Future[Seq[User]]
  def getFollowedUsers(userId: UserId): Future[Seq[String]]
  def removeAnonymizedUserFromFollowedUserTable(userId: UserId): Future[Unit]
  def followUser(followedUserId: UserId, userId: UserId): Future[Unit]
  def unfollowUser(followedUserId: UserId, userId: UserId): Future[Unit]
  def countOrganisations(ids: Option[Seq[UserId]], organisationName: Option[String]): Future[Int]
  def adminCountUsers(
    ids: Option[Seq[UserId]],
    email: Option[String],
    firstName: Option[String],
    lastName: Option[String],
    maybeRole: Option[Role],
    maybeUserType: Option[UserType]
  ): Future[Int]
  def findAllByEmail(emails: Seq[String]): Future[Seq[User]]
  def updateReconnectToken(
    userId: UserId,
    reconnectToken: String,
    reconnectTokenCreatedAt: ZonedDateTime
  ): Future[Boolean]
}

object PersistentUserService {
  final case class UpdateFailed() extends Exception
}
