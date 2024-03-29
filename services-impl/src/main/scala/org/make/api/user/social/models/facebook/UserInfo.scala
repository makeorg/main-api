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

package org.make.api.user.social.models.facebook

import org.make.api.user.social.models
import io.circe.Decoder

final case class UserInfo(id: String, email: Option[String], firstName: Option[String]) {
  def pictureUrl: String = s"https://graph.facebook.com/v12.0/$id/picture?width=512&height=512"

  def toUserInfo(): models.UserInfo = {
    models.UserInfo(
      email = email,
      firstName = firstName,
      facebookId = Some(id),
      picture = Option(pictureUrl),
      dateOfBirth = None
    )
  }
}

object UserInfo {
  implicit val decoder: Decoder[UserInfo] =
    Decoder.forProduct3("id", "email", "first_name")(UserInfo.apply)
}
