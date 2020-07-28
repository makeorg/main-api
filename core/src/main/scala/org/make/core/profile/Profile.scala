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

import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import org.make.core.question.QuestionId
import org.make.core.CirceFormatters
import org.make.core.technical.enumeratum.EnumKeys.StringEnumKeys

sealed abstract class Gender(val value: String) extends StringEnumEntry

object Gender extends StringEnum[Gender] with StringCirceEnum[Gender] with StringEnumKeys[Gender] {

  case object Male extends Gender("M")
  case object Female extends Gender("F")
  case object Other extends Gender("O")

  override def values: IndexedSeq[Gender] = findValues

}

sealed abstract class SocioProfessionalCategory(val value: String) extends StringEnumEntry

object SocioProfessionalCategory
    extends StringEnum[SocioProfessionalCategory]
    with StringCirceEnum[SocioProfessionalCategory]
    with StringEnumKeys[SocioProfessionalCategory] {

  case object Farmers extends SocioProfessionalCategory("FARM")

  case object ArtisansMerchantsCompanyDirector extends SocioProfessionalCategory("AMCD")

  case object ManagersAndHigherIntellectualOccupations extends SocioProfessionalCategory("MHIO")

  case object IntermediateProfessions extends SocioProfessionalCategory("INPR")

  case object Employee extends SocioProfessionalCategory("EMPL")

  case object Workers extends SocioProfessionalCategory("WORK")

  case object HighSchoolStudent extends SocioProfessionalCategory("HSTU")

  case object Student extends SocioProfessionalCategory("STUD")

  case object Apprentice extends SocioProfessionalCategory("APRE")

  case object Other extends SocioProfessionalCategory("O")

  override def values: IndexedSeq[SocioProfessionalCategory] = findValues

}

case class Profile(
  dateOfBirth: Option[LocalDate],
  avatarUrl: Option[String],
  profession: Option[String],
  phoneNumber: Option[String],
  description: Option[String],
  twitterId: Option[String],
  facebookId: Option[String],
  googleId: Option[String],
  gender: Option[Gender],
  genderName: Option[String],
  postalCode: Option[String],
  karmaLevel: Option[Int],
  locale: Option[String],
  optInNewsletter: Boolean = true,
  socioProfessionalCategory: Option[SocioProfessionalCategory] = None,
  registerQuestionId: Option[QuestionId] = None,
  optInPartner: Option[Boolean] = None,
  politicalParty: Option[String],
  website: Option[String],
  legalMinorConsent: Option[Boolean],
  legalAdvisorApproval: Option[Boolean]
)

object Profile extends CirceFormatters {
  implicit val encoder: Encoder[Profile] = deriveEncoder[Profile]
  implicit val decoder: Decoder[Profile] = deriveDecoder[Profile]

  def parseProfile(
    dateOfBirth: Option[LocalDate] = None,
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
    optInPartner: Option[Boolean] = None,
    politicalParty: Option[String] = None,
    website: Option[String] = None,
    legalMinorConsent: Option[Boolean] = None,
    legalAdvisorApproval: Option[Boolean] = None
  ): Some[Profile] = {

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
      optInPartner = optInPartner,
      politicalParty = politicalParty,
      website = website,
      legalMinorConsent = legalMinorConsent,
      legalAdvisorApproval = legalAdvisorApproval
    )
    Some(profile)
  }
}
