/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.technical.auth

import io.circe.Encoder
import io.swagger.annotations.ApiModelProperty
import org.make.api.technical.auth.MakeDataHandler.{CreatedAtParameter, RefreshTokenExpirationParameter}
import scalaoauth2.provider.AccessToken

import scala.annotation.meta.field

final case class TokenResponse(
  @(ApiModelProperty @field)(name = "token_type")
  tokenType: String,
  @(ApiModelProperty @field)(name = "access_token")
  accessToken: String,
  @(ApiModelProperty @field)(name = "expires_in")
  expiresIn: Long,
  @(ApiModelProperty @field)(name = "refresh_token")
  refreshToken: Option[String],
  @(ApiModelProperty @field)(name = "refresh_expires_in", dataType = "int")
  refreshExpiresIn: Option[Long],
  @(ApiModelProperty @field)(name = "created_at")
  createdAt: String
)

object TokenResponse {
  def fromAccessToken(tokenResult: AccessToken): TokenResponse = {
    TokenResponse(
      "Bearer",
      tokenResult.token,
      tokenResult.expiresIn.getOrElse(1L),
      tokenResult.refreshToken,
      tokenResult.params.get(RefreshTokenExpirationParameter).map(_.toLong),
      tokenResult.params.getOrElse(CreatedAtParameter, "")
    )
  }

  implicit val encoder: Encoder[TokenResponse] = {
    Encoder.forProduct6("token_type", "access_token", "expires_in", "refresh_token", "refresh_expires_in", "created_at") {
      response =>
        (
          response.tokenType,
          response.accessToken,
          response.expiresIn,
          response.refreshToken,
          response.refreshExpiresIn,
          response.createdAt
        )
    }
  }
}
