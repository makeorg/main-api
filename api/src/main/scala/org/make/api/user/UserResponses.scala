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
import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.core.CirceFormatters
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.user.{MailingErrorLog, Role, User, UserId, UserType}

import scala.annotation.meta.field
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Url
import io.circe.refined._
import org.make.core.Validation.{
  maxLength,
  requireNonEmpty,
  validateAge,
  validateOptional,
  validateOptionalUserInput,
  validatePostalCode,
  validateUserInput
}

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
  @(ApiModelProperty @field)(dataType = "list[string]") followedUsers: Seq[UserId] = Seq.empty,
  @(ApiModelProperty @field)(dataType = "string", example = "USER") userType: UserType
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
    isOrganisation = user.userType == UserType.UserTypeOrganisation,
    lastConnection = user.lastConnection,
    roles = user.roles,
    profile = user.profile.map(ProfileResponse.fromProfile),
    country = user.country,
    language = user.language,
    isHardBounce = user.isHardBounce,
    lastMailingError = user.lastMailingError.map(MailingErrorLogResponse(_)),
    hasPassword = user.hashedPassword.isDefined,
    followedUsers = followedUsers,
    userType = user.userType
  )
}

case class CurrentUserResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55")
  userId: UserId,
  email: String,
  displayName: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "USER")
  userType: UserType,
  @(ApiModelProperty @field)(dataType = "list[string]", allowableValues = "ROLE_CITIZEN,ROLE_MODERATOR,ROLE_ADMIN")
  roles: Seq[Role],
  hasPassword: Boolean,
  enabled: Boolean,
  emailVerified: Boolean,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language,
  avatarUrl: Option[String]
)

object CurrentUserResponse {
  implicit val encoder: Encoder[CurrentUserResponse] = deriveEncoder[CurrentUserResponse]
  implicit val decoder: Decoder[CurrentUserResponse] = deriveDecoder[CurrentUserResponse]
}

case class UserProfileResponse(firstName: Option[String],
                               lastName: Option[String],
                               dateOfBirth: Option[LocalDate],
                               avatarUrl: Option[String],
                               profession: Option[String],
                               description: Option[String],
                               postalCode: Option[String],
                               optInNewsletter: Boolean,
                               website: Option[String])

object UserProfileResponse {
  implicit val encoder: Encoder[UserProfileResponse] = deriveEncoder[UserProfileResponse]
  implicit val decoder: Decoder[UserProfileResponse] = deriveDecoder[UserProfileResponse]
}

case class UserProfileRequest(firstName: String,
                              lastName: Option[String],
                              dateOfBirth: Option[LocalDate],
                              @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/logo.jpg")
                              avatarUrl: Option[String Refined Url],
                              profession: Option[String],
                              description: Option[String],
                              postalCode: Option[String],
                              optInNewsletter: Boolean,
                              @(ApiModelProperty @field)(dataType = "string", example = "https://make.org")
                              website: Option[String Refined Url]) {
  private val maxDescriptionLength = 450

  validateOptional(
    Some(requireNonEmpty("firstName", firstName, Some("firstName should not be an empty string"))),
    Some(validateUserInput("firstName", firstName, None)),
    lastName.map(value => requireNonEmpty("lastName", value, Some("lastName should not be an empty string"))),
    Some(validateOptionalUserInput("lastName", lastName, None)),
    Some(validateOptionalUserInput("profession", profession, None)),
    description.map(value => maxLength("description", maxDescriptionLength, value)),
    Some(validateOptionalUserInput("description", description, None)),
    postalCode.map(value => validatePostalCode("postalCode", value, None)),
    Some(validateAge("dateOfBirth", dateOfBirth))
  )
}

object UserProfileRequest {
  implicit val encoder: Encoder[UserProfileRequest] = deriveEncoder[UserProfileRequest]
  implicit val decoder: Decoder[UserProfileRequest] = deriveDecoder[UserProfileRequest]
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

final case class SocialLoginResponse(token_type: String,
                                     access_token: String,
                                     expires_in: Long,
                                     refresh_token: String,
                                     account_creation: Boolean)

object SocialLoginResponse {

  def apply(token: TokenResponse, accountCreation: Boolean): SocialLoginResponse = SocialLoginResponse(
    token_type = token.token_type,
    access_token = token.access_token,
    expires_in = token.expires_in,
    refresh_token = token.refresh_token,
    account_creation = accountCreation
  )

  implicit val encoder: Encoder[SocialLoginResponse] = deriveEncoder

}
