package org.make.core.profile

import java.time.LocalDate

import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import io.circe.generic.semiauto._
import org.make.core.MakeSerializable
import org.make.core.CirceFormatters

sealed trait Gender {
  def shortName: String
}

object Gender extends StrictLogging {
  implicit lazy val genderEncoder: Encoder[Gender] = (gender: Gender) => Json.fromString(gender.shortName)
  implicit lazy val genderDecoder: Decoder[Gender] =
    Decoder.decodeString.map(
      gender => Gender.matchGender(gender).getOrElse(throw new IllegalArgumentException(s"$gender is not a Gender"))
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
                   country: Option[String],
                   language: Option[String],
                   karmaLevel: Option[Int],
                   locale: Option[String],
                   optInNewsletter: Boolean = true)
    extends MakeSerializable

object Profile extends CirceFormatters {
  implicit val encoder: ObjectEncoder[Profile] = deriveEncoder[Profile]
  implicit val decoder: Decoder[Profile] = deriveDecoder[Profile]

  def isEmpty(profile: Profile): Boolean = profile match {
    case Profile(None, None, None, None, None, None, None, None, None, None, None, None, None, None, true) => true
    case _                                                                                                 => false
  }

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
                   country: Option[String] = None,
                   language: Option[String] = None,
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
      country = country,
      language = language,
      karmaLevel = karmaLevel,
      locale = locale,
      optInNewsletter = optInNewsletter
    )
    if (isEmpty(profile)) {
      None
    } else {
      Some(profile)
    }
  }
}
