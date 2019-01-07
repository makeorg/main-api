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

import org.make.core.Timestamped
import org.make.core.question.QuestionId
import org.make.core.user.{Role, UserId}

case class Token(accessToken: String,
                 refreshToken: Option[String],
                 scope: Option[String],
                 expiresIn: Int,
                 user: UserRights,
                 client: Client,
                 override val createdAt: Option[ZonedDateTime] = None,
                 override val updatedAt: Option[ZonedDateTime] = None)
    extends Timestamped

case class UserRights(userId: UserId, roles: Seq[Role], availableQuestions: Seq[QuestionId])
