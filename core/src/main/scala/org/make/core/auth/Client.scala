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

import io.circe.{Decoder, Encoder, Json}
import org.make.core.user.{Role, UserId}
import org.make.core.{StringValue, Timestamped}

case class Client(
  clientId: ClientId,
  name: String,
  allowedGrantTypes: Seq[String],
  secret: Option[String],
  scope: Option[String],
  redirectUri: Option[String],
  override val createdAt: Option[ZonedDateTime] = None,
  override val updatedAt: Option[ZonedDateTime] = None,
  defaultUserId: Option[UserId],
  roles: Seq[Role],
  tokenExpirationSeconds: Int
) extends Timestamped

case class ClientId(value: String) extends StringValue

object ClientId {
  implicit lazy val proposalIdEncoder: Encoder[ClientId] =
    (a: ClientId) => Json.fromString(a.value)
  implicit lazy val proposalIdDecoder: Decoder[ClientId] =
    Decoder.decodeString.map(ClientId(_))
}
