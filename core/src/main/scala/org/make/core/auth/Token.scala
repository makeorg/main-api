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

package org.make.core.auth

import java.time.ZonedDateTime

import org.make.core.{DateHelper, Timestamped}
import org.make.core.question.QuestionId
import org.make.core.user.{Role, User, UserId}

final case class Token(
  accessToken: String,
  refreshToken: Option[String],
  scope: Option[String],
  expiresIn: Int,
  refreshExpiresIn: Int,
  user: UserRights,
  client: Client,
  override val createdAt: Option[ZonedDateTime] = None,
  override val updatedAt: Option[ZonedDateTime] = None
) extends Timestamped {

  def isAccessTokenExpired: Boolean = {
    isExpiredAfter(expiresIn)
  }
  def isRefreshTokenExpired: Boolean = {
    isExpiredAfter(refreshExpiresIn)
  }

  private def isExpiredAfter(interval: Int) = {
    createdAt.forall { date =>
      date.plusSeconds(interval).isBefore(DateHelper.now())
    }
  }
}

final case class UserRights(
  userId: UserId,
  roles: Seq[Role],
  availableQuestions: Seq[QuestionId],
  emailVerified: Boolean
)

object UserRights {
  def fromUser(user: User): UserRights = {
    UserRights(user.userId, user.roles, user.availableQuestions, user.emailVerified)
  }
}
