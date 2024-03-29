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
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}
import io.swagger.annotations.ApiModelProperty
import org.make.api.technical.auth.TokenResponse
import org.make.core.CirceFormatters
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.user.{MailingErrorLog, Role, User, UserId, UserType}

import scala.annotation.meta.field

final case class UserResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55") userId: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "yopmail+test@make.org") email: String,
  firstName: Option[String],
  lastName: Option[String],
  organisationName: Option[String],
  enabled: Boolean,
  emailVerified: Boolean,
  isOrganisation: Boolean,
  @(ApiModelProperty @field)(dataType = "dateTime")
  lastConnection: Option[ZonedDateTime],
  @(ApiModelProperty @field)(
    dataType = "list[string]",
    allowableValues = "ROLE_CITIZEN,ROLE_MODERATOR,ROLE_ADMIN,ROLE_POLITICAL,ROLE_ACTOR"
  )
  roles: Seq[Role],
  profile: Option[ProfileResponse],
  @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Country,
  isHardBounce: Boolean,
  @(ApiModelProperty @field)(dataType = "org.make.api.user.MailingErrorLogResponse")
  lastMailingError: Option[MailingErrorLogResponse],
  hasPassword: Boolean,
  @(ApiModelProperty @field)(
    dataType = "list[string]",
    example = "dfd03792-cd78-4390-92c0-d3084f584d0b,3622a467-74d7-4dad-b203-144751e4bc05"
  )
  followedUsers: Seq[UserId] = Seq.empty,
  @(ApiModelProperty @field)(dataType = "string", example = "USER", allowableValues = "USER,ORGANISATION,PERSONALITY") userType: UserType
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
    isHardBounce = user.isHardBounce,
    lastMailingError = user.lastMailingError.map(MailingErrorLogResponse(_)),
    hasPassword = user.hashedPassword.isDefined,
    followedUsers = followedUsers,
    userType = user.userType
  )
}

final case class CurrentUserResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55")
  userId: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "yopmail+test@make.org")
  email: String,
  displayName: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "USER")
  userType: UserType,
  @(ApiModelProperty @field)(
    dataType = "list[string]",
    allowableValues = "ROLE_CITIZEN,ROLE_MODERATOR,ROLE_ADMIN,ROLE_POLITICAL,ROLE_ACTOR"
  )
  roles: Seq[Role],
  hasPassword: Boolean,
  enabled: Boolean,
  emailVerified: Boolean,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/avatar.png")
  avatarUrl: Option[String],
  @(ApiModelProperty @field)(dataType = "dateTime")
  privacyPolicyApprovalDate: Option[ZonedDateTime]
)

object CurrentUserResponse {
  implicit val encoder: Encoder[CurrentUserResponse] = deriveEncoder[CurrentUserResponse]
  implicit val decoder: Decoder[CurrentUserResponse] = deriveDecoder[CurrentUserResponse]
}

final case class UserProfileResponse(
  firstName: Option[String],
  lastName: Option[String],
  dateOfBirth: Option[LocalDate],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/avatar.png") avatarUrl: Option[String],
  profession: Option[String],
  description: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "12345") postalCode: Option[String],
  optInNewsletter: Boolean,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/website") website: Option[String]
)

object UserProfileResponse {
  implicit val encoder: Encoder[UserProfileResponse] = deriveEncoder[UserProfileResponse]
  implicit val decoder: Decoder[UserProfileResponse] = deriveDecoder[UserProfileResponse]
}

final case class MailingErrorLogResponse(
  error: String,
  @(ApiModelProperty @field)(dataType = "dateTime") date: ZonedDateTime
)

object MailingErrorLogResponse extends CirceFormatters {
  implicit val encoder: Encoder[MailingErrorLogResponse] = deriveEncoder[MailingErrorLogResponse]
  implicit val decoder: Decoder[MailingErrorLogResponse] = deriveDecoder[MailingErrorLogResponse]

  def apply(mailingErrorLog: MailingErrorLog): MailingErrorLogResponse =
    MailingErrorLogResponse(error = mailingErrorLog.error, date = mailingErrorLog.date)
}

final case class ProfileResponse(
  @(ApiModelProperty @field)(dataType = "date", example = "1970-01-01") dateOfBirth: Option[LocalDate],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/avatar.png")
  avatarUrl: Option[String],
  profession: Option[String],
  phoneNumber: Option[String],
  description: Option[String],
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "M,F,O") gender: Option[Gender],
  genderName: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "12345")
  postalCode: Option[String],
  locale: Option[String],
  optInNewsletter: Boolean = true,
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "FARM,AMCD,MHIO,INPR,EMPL,WORK,HSTU,STUD,APRE,O")
  socioProfessionalCategory: Option[SocioProfessionalCategory] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "e4805533-7b46-41b6-8ef6-58caabb2e4e5")
  registerQuestionId: Option[QuestionId] = None,
  @(ApiModelProperty @field)(dataType = "boolean") optInPartner: Option[Boolean] = None,
  politicalParty: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/website")
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

final case class SocialLoginResponse(
  @(ApiModelProperty @field)(name = "token_type")
  tokenType: String,
  @(ApiModelProperty @field)(name = "access_token")
  accessToken: String,
  @(ApiModelProperty @field)(name = "expires_in")
  expiresIn: Long,
  @(ApiModelProperty @field)(name = "refresh_token")
  refreshToken: Option[String],
  @(ApiModelProperty @field)(name = "account_creation")
  accountCreation: Boolean,
  @(ApiModelProperty @field)(name = "refresh_expires_in", dataType = "int")
  refreshExpiresIn: Option[Long],
  @(ApiModelProperty @field)(name = "created_at")
  createdAt: String
) {

  def toTokenResponse: TokenResponse = {
    TokenResponse(
      tokenType = tokenType,
      accessToken = accessToken,
      expiresIn = expiresIn,
      refreshToken = refreshToken,
      refreshExpiresIn = refreshExpiresIn,
      createdAt = createdAt
    )
  }
}

object SocialLoginResponse {

  def apply(token: TokenResponse, accountCreation: Boolean): SocialLoginResponse = {
    SocialLoginResponse(
      tokenType = token.tokenType,
      accessToken = token.accessToken,
      expiresIn = token.expiresIn,
      refreshToken = token.refreshToken,
      accountCreation = accountCreation,
      refreshExpiresIn = token.refreshExpiresIn,
      createdAt = token.createdAt
    )
  }

  implicit val encoder: Encoder[SocialLoginResponse] =
    Encoder.forProduct7(
      "token_type",
      "access_token",
      "expires_in",
      "refresh_token",
      "account_creation",
      "refresh_expires_in",
      "created_at"
    ) { response =>
      (
        response.tokenType,
        response.accessToken,
        response.expiresIn,
        response.refreshToken,
        response.accountCreation,
        response.refreshExpiresIn,
        response.createdAt
      )
    }

}

final case class UserPrivacyPolicyResponse(privacyPolicyApprovalDate: Option[ZonedDateTime])

object UserPrivacyPolicyResponse extends CirceFormatters {
  implicit val codec: Codec[UserPrivacyPolicyResponse] = deriveCodec
}
