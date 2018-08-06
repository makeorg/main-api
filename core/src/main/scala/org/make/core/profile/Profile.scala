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

package org.make.core.profile

import java.time.LocalDate

import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import org.make.core.{CirceFormatters, MakeSerializable}

sealed trait Gender {
  def shortName: String
}

object Gender extends StrictLogging {
  implicit lazy val genderEncoder: Encoder[Gender] = (gender: Gender) => Json.fromString(gender.shortName)
  implicit lazy val genderDecoder: Decoder[Gender] =
    Decoder.decodeString.emap(
      gender => Gender.matchGender(gender).map(Right.apply).getOrElse(Left(s"$gender is not a Gender"))
    )

  val genders: Map[String, Gender] = Map(Male.shortName -> Male, Female.shortName -> Female, Other.shortName -> Other)

  def matchGender(genderOrNull: String): Option[Gender] = {
    Option(genderOrNull).flatMap { gender =>
      val maybeGender = genders.get(gender)
      if (maybeGender.isEmpty) {
        logger.warn(s"$gender is not a gender")
      }
      maybeGender
    }
  }

  case object Male extends Gender {
    override val shortName: String = "M"
  }

  case object Female extends Gender {
    override val shortName: String = "F"
  }

  case object Other extends Gender {
    override val shortName: String = "O"
  }
}

case class Profile(dateOfBirth: Option[LocalDate],
                   avatarUrl: Option[String],
                   profession: Option[String],
                   phoneNumber: Option[String],
                   twitterId: Option[String],
                   facebookId: Option[String],
                   googleId: Option[String],
                   gender: Option[Gender],
                   genderName: Option[String],
                   postalCode: Option[String],
                   karmaLevel: Option[Int],
                   locale: Option[String],
                   optInNewsletter: Boolean = true)
    extends MakeSerializable

object Profile extends CirceFormatters {
  implicit val encoder: ObjectEncoder[Profile] = deriveEncoder[Profile]
  implicit val decoder: Decoder[Profile] = deriveDecoder[Profile]

  def default: Profile = Profile(
    dateOfBirth = None,
    avatarUrl = None,
    profession = None,
    phoneNumber = None,
    twitterId = None,
    facebookId = None,
    googleId = None,
    gender = None,
    genderName = None,
    postalCode = None,
    karmaLevel = None,
    locale = None
  )

  def parseProfile(dateOfBirth: Option[LocalDate] = None,
                   avatarUrl: Option[String] = None,
                   profession: Option[String] = None,
                   phoneNumber: Option[String] = None,
                   twitterId: Option[String] = None,
                   facebookId: Option[String] = None,
                   googleId: Option[String] = None,
                   gender: Option[Gender] = None,
                   genderName: Option[String] = None,
                   postalCode: Option[String] = None,
                   karmaLevel: Option[Int] = None,
                   locale: Option[String] = None,
                   optInNewsletter: Boolean = true): Option[Profile] = {

    val profile = Profile(
      dateOfBirth = dateOfBirth,
      avatarUrl = avatarUrl,
      profession = profession,
      phoneNumber = phoneNumber,
      twitterId = twitterId,
      facebookId = facebookId,
      googleId = googleId,
      gender = gender,
      genderName = genderName,
      postalCode = postalCode,
      karmaLevel = karmaLevel,
      locale = locale,
      optInNewsletter = optInNewsletter
    )
    Some(profile)
  }
}
