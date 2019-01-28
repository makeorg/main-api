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
import io.swagger.annotations.ApiModelProperty
import org.make.core.Validation.validateUserInput
import org.make.core.question.QuestionId
import org.make.core.{CirceFormatters, MakeSerializable, Validation}

import scala.annotation.meta.field

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

sealed trait SocioProfessionalCategory {
  def shortName: String
}

object SocioProfessionalCategory extends StrictLogging {
  implicit lazy val genderEncoder: Encoder[SocioProfessionalCategory] =
    (socioProfessionalCategory: SocioProfessionalCategory) => Json.fromString(socioProfessionalCategory.shortName)
  implicit lazy val genderDecoder: Decoder[SocioProfessionalCategory] =
    Decoder.decodeString.emap(
      socioProfessionalCategory =>
        SocioProfessionalCategory
          .matchSocioProfessionalCategory(socioProfessionalCategory)
          .map(Right.apply)
          .getOrElse(Left(s"$socioProfessionalCategory is not a socio professional category"))
    )

  val socioProfessionalCategories: Map[String, SocioProfessionalCategory] =
    Map(
      Farmers.shortName -> Farmers,
      ArtisansMerchantsCompanyDirector.shortName -> ArtisansMerchantsCompanyDirector,
      ManagersAndHigherIntellectualOccupations.shortName -> ManagersAndHigherIntellectualOccupations,
      IntermediateProfessions.shortName -> IntermediateProfessions,
      Employee.shortName -> Employee,
      Workers.shortName -> Workers,
      HighSchoolStudent.shortName -> HighSchoolStudent,
      Student.shortName -> Student,
      Apprentice.shortName -> Apprentice,
      Other.shortName -> Other
    )

  def matchSocioProfessionalCategory(socioProfessionalCategoryOrNull: String): Option[SocioProfessionalCategory] = {
    Option(socioProfessionalCategoryOrNull).flatMap { socioProfessionalCategory =>
      val maybeSocioProfessionalCategory = socioProfessionalCategories.get(socioProfessionalCategory)
      if (maybeSocioProfessionalCategory.isEmpty) {
        logger.warn(s"$socioProfessionalCategory is not a socio professional category")
      }
      maybeSocioProfessionalCategory
    }
  }

  case object Farmers extends SocioProfessionalCategory {
    override val shortName: String = "FARM"
  }

  case object ArtisansMerchantsCompanyDirector extends SocioProfessionalCategory {
    override val shortName: String = "AMCD"
  }

  case object ManagersAndHigherIntellectualOccupations extends SocioProfessionalCategory {
    override val shortName: String = "MHIO"
  }

  case object IntermediateProfessions extends SocioProfessionalCategory {
    override val shortName: String = "INPR"
  }

  case object Employee extends SocioProfessionalCategory {
    override val shortName: String = "EMPL"
  }

  case object Workers extends SocioProfessionalCategory {
    override val shortName: String = "WORK"
  }

  case object HighSchoolStudent extends SocioProfessionalCategory {
    override val shortName: String = "HSTU"
  }

  case object Student extends SocioProfessionalCategory {
    override val shortName: String = "STUD"
  }

  case object Apprentice extends SocioProfessionalCategory {
    override val shortName: String = "APRE"
  }

  case object Other extends SocioProfessionalCategory {
    override val shortName: String = "O"
  }
}

case class Profile(
  @(ApiModelProperty @field)(dataType = "string", example = "1970-01-01") dateOfBirth: Option[LocalDate],
  avatarUrl: Option[String],
  profession: Option[String],
  phoneNumber: Option[String],
  description: Option[String],
  twitterId: Option[String],
  facebookId: Option[String],
  googleId: Option[String],
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "M,F,O") gender: Option[Gender],
  genderName: Option[String],
  postalCode: Option[String],
  @(ApiModelProperty @field)(dataType = "integer") karmaLevel: Option[Int],
  locale: Option[String],
  optInNewsletter: Boolean = true,
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "FARM,AMCD,MHIO,INPR,EMPL,WORK,HSTU,STUD,APRE,O") socioProfessionalCategory: Option[
    SocioProfessionalCategory
  ] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "e4805533-7b46-41b6-8ef6-58caabb2e4e5") registerQuestionId: Option[
    QuestionId
  ] = None,
  @(ApiModelProperty @field)(dataType = "boolean") optInPartner: Option[Boolean] = None
) extends MakeSerializable

object Profile extends CirceFormatters {
  implicit val encoder: ObjectEncoder[Profile] = deriveEncoder[Profile]
  implicit val decoder: Decoder[Profile] = deriveDecoder[Profile]

  def validateProfile(profile: Profile): Unit = {
    Validation.validate(
      profile.avatarUrl.map(value   => validateUserInput("avatarUrl", value, None)),
      profile.description.map(value => validateUserInput("description", value, None)),
      profile.facebookId.map(value  => validateUserInput("facebookId", value, None)),
      profile.genderName.map(value  => validateUserInput("genderName", value, None)),
      profile.googleId.map(value    => validateUserInput("googleId", value, None)),
      profile.locale.map(value      => validateUserInput("locale", value, None)),
      profile.phoneNumber.map(value => validateUserInput("phoneNumber", value, None)),
      profile.postalCode.map(value  => validateUserInput("postalCode", value, None)),
      profile.profession.map(value  => validateUserInput("profession", value, None)),
      profile.twitterId.map(value   => validateUserInput("twitterId", value, None))
    )
  }

  def default: Profile = Profile(
    dateOfBirth = None,
    avatarUrl = None,
    profession = None,
    phoneNumber = None,
    description = None,
    twitterId = None,
    facebookId = None,
    googleId = None,
    gender = None,
    genderName = None,
    postalCode = None,
    karmaLevel = None,
    locale = None,
    socioProfessionalCategory = None,
    registerQuestionId = None,
    optInPartner = None
  )

  def parseProfile(dateOfBirth: Option[LocalDate] = None,
                   avatarUrl: Option[String] = None,
                   profession: Option[String] = None,
                   phoneNumber: Option[String] = None,
                   description: Option[String] = None,
                   twitterId: Option[String] = None,
                   facebookId: Option[String] = None,
                   googleId: Option[String] = None,
                   gender: Option[Gender] = None,
                   genderName: Option[String] = None,
                   postalCode: Option[String] = None,
                   karmaLevel: Option[Int] = None,
                   locale: Option[String] = None,
                   optInNewsletter: Boolean = true,
                   socioProfessionalCategory: Option[SocioProfessionalCategory] = None,
                   registerQuestionId: Option[QuestionId] = None,
                   optInPartner: Option[Boolean] = None): Option[Profile] = {

    val profile = Profile(
      dateOfBirth = dateOfBirth,
      avatarUrl = avatarUrl,
      profession = profession,
      phoneNumber = phoneNumber,
      description = description,
      twitterId = twitterId,
      facebookId = facebookId,
      googleId = googleId,
      gender = gender,
      genderName = genderName,
      postalCode = postalCode,
      karmaLevel = karmaLevel,
      locale = locale,
      optInNewsletter = optInNewsletter,
      socioProfessionalCategory = socioProfessionalCategory,
      registerQuestionId = registerQuestionId,
      optInPartner = optInPartner
    )
    Some(profile)
  }
}
