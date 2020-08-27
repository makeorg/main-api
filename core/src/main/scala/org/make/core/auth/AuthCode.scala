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

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.swagger.annotations.ApiModelProperty
import org.make.core.CirceFormatters
import org.make.core.user.UserId

import scala.annotation.meta.field

final case class AuthCode(
  authorizationCode: String,
  scope: Option[String],
  redirectUri: Option[String],
  createdAt: ZonedDateTime,
  expiresIn: Int,
  @(ApiModelProperty @field)(dataType = "string", example = "a58da5a1-90c9-4216-8de0-5a5b18d1d398")
  user: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "7951a086-fa88-4cd0-815a-d76f514b2f1d")
  client: ClientId
)

object AuthCode extends CirceFormatters {
  implicit val encoder: Encoder[AuthCode] = deriveEncoder[AuthCode]
}
