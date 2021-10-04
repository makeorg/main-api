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

import java.net.URL
import java.time.LocalDate

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.make.api.user.social.models
import org.make.core.CirceFormatters

final case class MetadataSource(`type`: String, id: String)

object MetadataSource {
  implicit val decoder: Decoder[MetadataSource] = deriveDecoder
}

final case class ItemMetadata(primary: Option[Boolean], verified: Option[Boolean], source: MetadataSource) {
  def isPrimary: Boolean = primary.exists(identity)
}

object ItemMetadata {
  implicit val decoder: Decoder[ItemMetadata] = deriveDecoder
}

final case class PeopleName(
  metadata: ItemMetadata,
  displayName: String,
  familyName: Option[String],
  givenName: String,
  displayNameLastFirst: String,
  unstructuredName: String
)

object PeopleName {
  implicit val decoder: Decoder[PeopleName] = deriveDecoder
}

final case class PeoplePhoto(metadata: ItemMetadata, url: URL)

object PeoplePhoto extends CirceFormatters {
  implicit val decoder: Decoder[PeoplePhoto] = deriveDecoder
}

final case class PeopleEmailAddress(metadata: ItemMetadata, value: String)

object PeopleEmailAddress {
  implicit val decoder: Decoder[PeopleEmailAddress] = deriveDecoder
}

final case class GoogleDate(year: Option[Int], month: Int, day: Int) {
  def toLocalDate: Option[LocalDate] = {
    year.map(LocalDate.of(_, month, day))
  }
}

object GoogleDate {
  implicit val decoder: Decoder[GoogleDate] = deriveDecoder
}

final case class Birthday(metadata: ItemMetadata, text: Option[String], date: GoogleDate)

object Birthday {
  implicit val decoder: Decoder[Birthday] = deriveDecoder
}

final case class PeopleInfo(
  resourceName: String,
  etag: String,
  names: Seq[PeopleName],
  photos: Seq[PeoplePhoto],
  emailAddresses: Seq[PeopleEmailAddress],
  birthdays: Option[Seq[Birthday]] // make it optional until all the fronts use the right scopes
) {
  def toUserInfo(): models.UserInfo = {
    val maybeEmail = emailAddresses.find(_.metadata.isPrimary).map(_.value)
    models.UserInfo(
      email = maybeEmail,
      firstName = names.find(_.metadata.isPrimary).map(_.givenName),
      gender = None,
      googleId = Some(resourceName.split("/").last),
      facebookId = None,
      picture = photos.find(_.metadata.isPrimary).map(_.url.toString),
      domain = maybeEmail.map(_.split("@").last),
      dateOfBirth = birthdays.flatMap(_.sortBy(!_.metadata.isPrimary).flatMap(_.date.toLocalDate.toList).headOption)
    )
  }
}

object PeopleInfo {
  implicit val decoder: Decoder[PeopleInfo] = deriveDecoder

  val MODERATOR_DOMAIN: String = "make.org"
}
