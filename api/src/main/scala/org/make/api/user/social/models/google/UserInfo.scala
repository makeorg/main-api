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

package org.make.api.user.social.models.google

import io.circe.Decoder

final case class UserInfo(azp: Option[String],
                          aud: Option[String],
                          sub: Option[String],
                          hd: Option[String],
                          email: Option[String],
                          emailVerified: String,
                          atHash: Option[String],
                          iss: Option[String],
                          iat: Option[String],
                          exp: Option[String],
                          name: Option[String],
                          picture: String,
                          givenName: Option[String],
                          familyName: Option[String],
                          local: Option[String],
                          alg: Option[String],
                          kid: Option[String]) {
  def pictureUrl: String = {
    val end = "/photo.jpg"
    if (picture.endsWith(end)) {
      s"${picture.dropRight(end.length)}-s512$end"
    } else {
      s"$picture-s512"
    }
  }
}

object UserInfo {

  val MODERATOR_DOMAIN = "make.org"

  implicit val decoder: Decoder[UserInfo] =
    Decoder.forProduct17(
      "azp",
      "aud",
      "sub",
      "hd",
      "email",
      "email_verified",
      "at_hash",
      "iss",
      "iat",
      "exp",
      "name",
      "picture",
      "given_name",
      "family_name",
      "locale",
      "alg",
      "kid"
    )(UserInfo.apply)
}
