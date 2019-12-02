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

package org.make.api.user

import java.time.{LocalDate, ZonedDateTime}

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.swagger.annotations.ApiModelProperty
import org.make.core.CirceFormatters
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.user.{MailingErrorLog, Role, User, UserId}

import scala.annotation.meta.field

case class UserResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55") userId: UserId,
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  organisationName: Option[String],
  enabled: Boolean,
  emailVerified: Boolean,
  isOrganisation: Boolean,
  @(ApiModelProperty @field)(dataType = "string", example = "2019-01-21T16:33:21.523+01:00[Europe/Paris]")
  lastConnection: ZonedDateTime,
  @(ApiModelProperty @field)(dataType = "list[string]", allowableValues = "ROLE_CITIZEN,ROLE_MODERATOR,ROLE_ADMIN")
  roles: Seq[Role],
  profile: Option[ProfileResponse],
  @(ApiModelProperty @field)(dataType = "string") country: Country,
  @(ApiModelProperty @field)(dataType = "string") language: Language,
  isHardBounce: Boolean,
  @(ApiModelProperty @field)(dataType = "org.make.api.user.MailingErrorLogResponse")
  lastMailingError: Option[MailingErrorLogResponse],
  hasPassword: Boolean,
  @(ApiModelProperty @field)(dataType = "list[string]") followedUsers: Seq[UserId] = Seq.empty
)

object UserResponse extends CirceFormatters {
  implicit val encoder: Encoder[UserResponse] = deriveEncoder[UserResponse]
  implicit val decoder: Decoder[UserResponse] = deriveDecoder[UserResponse]

  def apply(user: User): UserResponse = UserResponse(user, Seq.empty)

  def apply(user: User, followedUsers: Seq[UserId]): UserResponse = UserResponse(
    userId = user.userId,
    email = user.email,
    firstName = user.firstName,
    lastName = user.lastName,
    organisationName = user.organisationName,
    enabled = user.enabled,
    emailVerified = user.emailVerified,
    isOrganisation = user.isOrganisation,
    lastConnection = user.lastConnection,
    roles = user.roles,
    profile = user.profile.map(ProfileResponse.fromProfile),
    country = user.country,
    language = user.language,
    isHardBounce = user.isHardBounce,
    lastMailingError = user.lastMailingError.map(MailingErrorLogResponse(_)),
    hasPassword = user.hashedPassword.isDefined,
    followedUsers = followedUsers
  )
}

case class MailingErrorLogResponse(error: String,
                                   @(ApiModelProperty @field)(
                                     dataType = "string",
                                     example = "2019-01-21T16:33:21.523+01:00[Europe/Paris]"
                                   ) date: ZonedDateTime)
object MailingErrorLogResponse extends CirceFormatters {
  implicit val encoder: Encoder[MailingErrorLogResponse] = deriveEncoder[MailingErrorLogResponse]
  implicit val decoder: Decoder[MailingErrorLogResponse] = deriveDecoder[MailingErrorLogResponse]

  def apply(mailingErrorLog: MailingErrorLog): MailingErrorLogResponse =
    MailingErrorLogResponse(error = mailingErrorLog.error, date = mailingErrorLog.date)
}

case class ProfileResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "1970-01-01") dateOfBirth: Option[LocalDate],
  avatarUrl: Option[String],
  profession: Option[String],
  phoneNumber: Option[String],
  description: Option[String],
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "M,F,O") gender: Option[Gender],
  genderName: Option[String],
  postalCode: Option[String],
  locale: Option[String],
  optInNewsletter: Boolean = true,
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "FARM,AMCD,MHIO,INPR,EMPL,WORK,HSTU,STUD,APRE,O")
  socioProfessionalCategory: Option[SocioProfessionalCategory] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "e4805533-7b46-41b6-8ef6-58caabb2e4e5")
  registerQuestionId: Option[QuestionId] = None,
  @(ApiModelProperty @field)(dataType = "boolean") optInPartner: Option[Boolean] = None,
  politicalParty: Option[String],
  website: Option[String]
)

object ProfileResponse extends CirceFormatters {
  implicit val encoder: Encoder[ProfileResponse] = deriveEncoder[ProfileResponse]
  implicit val decoder: Decoder[ProfileResponse] = deriveDecoder[ProfileResponse]

  def fromProfile(profile: Profile): ProfileResponse = {
    ProfileResponse(
      dateOfBirth = profile.dateOfBirth,
      avatarUrl = profile.avatarUrl,
      profession = profile.profession,
      phoneNumber = profile.phoneNumber,
      description = profile.description,
      gender = profile.gender,
      genderName = profile.genderName,
      postalCode = profile.postalCode,
      locale = profile.locale,
      optInNewsletter = profile.optInNewsletter,
      socioProfessionalCategory = profile.socioProfessionalCategory,
      registerQuestionId = profile.registerQuestionId,
      optInPartner = profile.optInPartner,
      politicalParty = profile.politicalParty,
      website = profile.website
    )
  }
}
