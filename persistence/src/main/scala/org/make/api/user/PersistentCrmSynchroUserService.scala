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

import org.make.api.user.PersistentCrmSynchroUserService.CrmSynchroUser
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.user.{User, UserId, UserType}

import java.time.{LocalDate, ZonedDateTime}
import scala.concurrent.Future

trait PersistentCrmSynchroUserService {

  def findUsersForCrmSynchro(
    optIn: Option[Boolean],
    hardBounce: Option[Boolean],
    offset: Int,
    limit: Int
  ): Future[Seq[CrmSynchroUser]]

}

object PersistentCrmSynchroUserService {
  type CrmSynchroUser = MakeUser

  final case class MakeUser(
    uuid: UserId,
    email: String,
    firstName: Option[String],
    lastName: Option[String],
    organisationName: Option[String],
    postalCode: Option[String],
    dateOfBirth: Option[LocalDate],
    emailVerified: Boolean,
    isHardBounce: Boolean,
    optInNewsletter: Boolean,
    createdAt: ZonedDateTime,
    userType: UserType,
    country: Country,
    lastConnection: Option[ZonedDateTime],
    registerQuestionId: Option[QuestionId]
  ) {
    def fullName: Option[String] = {
      User.fullName(firstName, lastName, organisationName)
    }
  }

}

trait PersistentCrmSynchroUserServiceComponent {
  def persistentCrmSynchroUserService: PersistentCrmSynchroUserService
}
