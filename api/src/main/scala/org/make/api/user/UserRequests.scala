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

import java.time.LocalDate

import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.boolean.{And, Or}
import eu.timepit.refined.collection.{Empty, MaxSize}
import eu.timepit.refined.string.Url
import io.circe.refined._
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.swagger.annotations.ApiModelProperty
import org.make.api.technical.RequestHelper
import org.make.core.{CirceFormatters, Validation}
import org.make.core.Validation.{
  mandatoryField,
  maxLength,
  requireNonEmpty,
  validate,
  validateAge,
  validateEmail,
  validateField,
  validateOptional,
  validateOptionalUserInput,
  validatePostalCode,
  validateUserInput
}
import org.make.core.auth.ClientId
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}

import scala.annotation.meta.field
import scala.util.{Success, Try}

case class ProfileRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "1970-01-01") dateOfBirth: Option[LocalDate],
  @(ApiModelProperty @field)(dataType = "string", example = "1970-01-01")
  avatarUrl: Option[String Refined And[Url, MaxSize[W.`2048`.T]]],
  profession: Option[String],
  phoneNumber: Option[String],
  description: Option[String Refined MaxSize[W.`450`.T]],
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "M,F,O") gender: Option[Gender],
  genderName: Option[String],
  postalCode: Option[String],
  locale: Option[String],
  optInNewsletter: Boolean = true,
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "FARM,AMCD,MHIO,INPR,EMPL,WORK,HSTU,STUD,APRE,O")
  socioProfessionalCategory: Option[SocioProfessionalCategory] = None,
  @(ApiModelProperty @field)(dataType = "boolean") optInPartner: Option[Boolean] = None,
  politicalParty: Option[String],
  website: Option[String Refined Url]
) {

  def mergeProfile(maybeProfile: Option[Profile]): Option[Profile] = maybeProfile match {
    case None => toProfile
    case Some(profile) =>
      Some(
        profile.copy(
          dateOfBirth = dateOfBirth.orElse(profile.dateOfBirth),
          avatarUrl = RequestHelper.updateValue(profile.avatarUrl, avatarUrl.map(_.value)),
          profession = RequestHelper.updateValue(profile.profession, profession),
          phoneNumber = RequestHelper.updateValue(profile.phoneNumber, phoneNumber),
          description = RequestHelper.updateValue(profile.description, description.map(_.value)),
          gender = gender.collect { case g if !g.shortName.isEmpty => g }.orElse(profile.gender),
          genderName = RequestHelper.updateValue(profile.genderName, genderName),
          postalCode = RequestHelper.updateValue(profile.postalCode, postalCode),
          locale = RequestHelper.updateValue(profile.locale, locale),
          optInNewsletter = optInNewsletter,
          socioProfessionalCategory = socioProfessionalCategory.collect { case spc if !spc.shortName.isEmpty => spc }
            .orElse(profile.socioProfessionalCategory),
          optInPartner = optInPartner.orElse(profile.optInPartner),
          politicalParty = RequestHelper.updateValue(profile.politicalParty, politicalParty),
          website = RequestHelper.updateValue(profile.website, website.map(_.value))
        )
      )
  }

  def toProfile: Option[Profile] = Profile.parseProfile(
    dateOfBirth = dateOfBirth,
    avatarUrl = avatarUrl.map(_.value),
    profession = profession,
    phoneNumber = phoneNumber,
    description = description.map(_.value),
    gender = gender,
    genderName = genderName,
    postalCode = postalCode,
    locale = locale,
    optInNewsletter = optInNewsletter,
    socioProfessionalCategory = socioProfessionalCategory,
    optInPartner = optInPartner,
    politicalParty = politicalParty,
    website = website.map(_.value)
  )
}

object ProfileRequest extends CirceFormatters {
  implicit val encoder: Encoder[ProfileRequest] = deriveEncoder[ProfileRequest]
  implicit val decoder: Decoder[ProfileRequest] = deriveDecoder[ProfileRequest]

  def validateProfileRequest(profileRequest: ProfileRequest): Unit = {
    Validation.validateOptional(
      profileRequest.avatarUrl.map(value      => validateUserInput("avatarUrl", value.value, None)),
      profileRequest.description.map(value    => validateUserInput("description", value.value, None)),
      profileRequest.genderName.map(value     => validateUserInput("genderName", value, None)),
      profileRequest.locale.map(value         => validateUserInput("locale", value, None)),
      profileRequest.phoneNumber.map(value    => validateUserInput("phoneNumber", value, None)),
      profileRequest.postalCode.map(value     => validatePostalCode("postalCode", value, None)),
      profileRequest.profession.map(value     => validateUserInput("profession", value, None)),
      profileRequest.politicalParty.map(value => validateUserInput("politicalParty", value, None)),
      profileRequest.website.map(value        => validateUserInput("website", value.value, None))
    )
  }

  def parseProfileRequest(
    dateOfBirth: Option[LocalDate] = None,
    avatarUrl: Option[String Refined And[Url, MaxSize[W.`2048`.T]]] = None,
    profession: Option[String] = None,
    phoneNumber: Option[String] = None,
    description: Option[String Refined MaxSize[W.`450`.T]] = None,
    gender: Option[Gender] = None,
    genderName: Option[String] = None,
    postalCode: Option[String] = None,
    locale: Option[String] = None,
    optInNewsletter: Boolean = true,
    socioProfessionalCategory: Option[SocioProfessionalCategory] = None,
    optInPartner: Option[Boolean] = None,
    politicalParty: Option[String] = None,
    website: Option[String Refined Url] = None
  ): Option[ProfileRequest] = {

    val profile = ProfileRequest(
      dateOfBirth = dateOfBirth,
      avatarUrl = avatarUrl,
      profession = profession,
      phoneNumber = phoneNumber,
      description = description,
      gender = gender,
      genderName = genderName,
      postalCode = postalCode,
      locale = locale,
      optInNewsletter = optInNewsletter,
      socioProfessionalCategory = socioProfessionalCategory,
      optInPartner = optInPartner,
      politicalParty = politicalParty,
      website = website
    )
    Some(profile)
  }
}

case class RegisterUserRequest(
  email: String,
  password: String,
  @(ApiModelProperty @field)(dataType = "string", example = "1970-01-01") dateOfBirth: Option[LocalDate],
  firstName: Option[String],
  lastName: Option[String],
  profession: Option[String],
  postalCode: Option[String],
  @(ApiModelProperty @field)(dataType = "string") country: Option[Country],
  @(ApiModelProperty @field)(dataType = "string") language: Option[Language],
  @(ApiModelProperty @field)(dataType = "boolean") optIn: Option[Boolean],
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "M,F,O") gender: Option[Gender],
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "FARM,AMCD,MHIO,INPR,EMPL,WORK,HSTU,STUD,APRE,O") socioProfessionalCategory: Option[
    SocioProfessionalCategory
  ],
  @(ApiModelProperty @field)(dataType = "string", example = "e4805533-7b46-41b6-8ef6-58caabb2e4e5") questionId: Option[
    QuestionId
  ],
  @(ApiModelProperty @field)(dataType = "boolean") optInPartner: Option[Boolean],
  politicalParty: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "http://example.com") website: Option[String Refined Url]
) {

  validate(
    mandatoryField("firstName", firstName),
    validateOptionalUserInput("firstName", firstName, None),
    mandatoryField("email", email),
    validateEmail("email", email.toLowerCase),
    validateUserInput("email", email, None),
    mandatoryField("password", password),
    validateField(
      "password",
      "invalid_password",
      Option(password).exists(_.length >= 8),
      "Password must be at least 8 characters"
    ),
    validateOptionalUserInput("lastName", lastName, None),
    validateOptionalUserInput("profession", profession, None),
    validateOptionalUserInput("postalCode", postalCode, None),
    mandatoryField("language", language),
    mandatoryField("country", country),
    validateAge("dateOfBirth", dateOfBirth)
  )
  validateOptional(postalCode.map(value => validatePostalCode("postalCode", value, None)))
}

object RegisterUserRequest extends CirceFormatters {
  implicit val decoder: Decoder[RegisterUserRequest] = deriveDecoder[RegisterUserRequest]
}

case class UpdateUserRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "1970-01-01") dateOfBirth: Option[String],
  firstName: Option[String],
  lastName: Option[String],
  organisationName: Option[String],
  profession: Option[String],
  postalCode: Option[String],
  phoneNumber: Option[String],
  description: Option[String],
  @(ApiModelProperty @field)(dataType = "boolean") optInNewsletter: Option[Boolean],
  gender: Option[String],
  genderName: Option[String],
  @(ApiModelProperty @field)(dataType = "string") country: Option[Country],
  @(ApiModelProperty @field)(dataType = "string") language: Option[Language],
  socioProfessionalCategory: Option[String],
  politicalParty: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "http://example.com") website: Option[
    String Refined Or[Url, Empty]
  ]
) {
  private val maxLanguageLength = 3
  private val maxCountryLength = 3
  private val maxDescriptionLength = 450

  validateOptional(
    firstName.map(value => requireNonEmpty("firstName", value, Some("firstName should not be an empty string"))),
    Some(validateOptionalUserInput("firstName", firstName, None)),
    organisationName.map(
      value => requireNonEmpty("organisationName", value, Some("organisationName should not be an empty string"))
    ),
    Some(validateOptionalUserInput("organisationName", organisationName, None)),
    postalCode.map(value => validatePostalCode("postalCode", value, None)),
    language.map(lang    => maxLength("language", maxLanguageLength, lang.value)),
    country.map(country  => maxLength("country", maxCountryLength, country.value)),
    gender.map(
      value =>
        validateField(
          "gender",
          "invalid_value",
          value == "" || Gender.matchGender(value).isDefined,
          s"gender should be on of this specified values: ${Gender.genders.keys.mkString(",")}"
        )
    ),
    socioProfessionalCategory.map(
      value =>
        validateField(
          "socio professional category",
          "invalid_value",
          value == "" || SocioProfessionalCategory.matchSocioProfessionalCategory(value).isDefined,
          s"CSP should be on of this specified values: ${SocioProfessionalCategory.socioProfessionalCategories.keys.mkString(",")}"
        )
    ),
    description.map(value => maxLength("description", maxDescriptionLength, value)),
    Some(validateOptionalUserInput("phoneNumber", phoneNumber, None)),
    Some(validateOptionalUserInput("description", description, None)),
    dateOfBirth match {
      case Some("") => None
      case None     => None
      case Some(date) =>
        val localDate = Try(LocalDate.parse(date)) match {
          case Success(parsedDate) => Some(parsedDate)
          case _                   => None
        }
        Some(validateAge("dateOfBirth", localDate))
    }
  )
}

object UpdateUserRequest extends CirceFormatters {
  implicit val decoder: Decoder[UpdateUserRequest] = deriveDecoder[UpdateUserRequest]
}

case class SocialLoginRequest(
  provider: String,
  token: String,
  @(ApiModelProperty @field)(dataType = "string") country: Option[Country],
  @(ApiModelProperty @field)(dataType = "string") language: Option[Language],
  clientId: Option[ClientId]
) {
  validate(mandatoryField("language", language), mandatoryField("country", country))
}

object SocialLoginRequest {
  implicit val decoder: Decoder[SocialLoginRequest] = deriveDecoder[SocialLoginRequest]
}

final case class ResetPasswordRequest(email: String) {
  validate(
    mandatoryField("email", email),
    validateEmail("email", email.toLowerCase),
    validateUserInput("email", email, None)
  )
}

object ResetPasswordRequest {
  implicit val decoder: Decoder[ResetPasswordRequest] = deriveDecoder[ResetPasswordRequest]
}

final case class ResetPassword(resetToken: String, password: String) {
  validate(mandatoryField("resetToken", resetToken))
  validate(
    mandatoryField("password", password),
    validateField(
      "password",
      "invalid_password",
      Option(password).exists(_.length >= 8),
      "Password must be at least 8 characters"
    )
  )
}

object ResetPassword {
  implicit val decoder: Decoder[ResetPassword] = deriveDecoder[ResetPassword]
}

final case class ChangePasswordRequest(actualPassword: Option[String], newPassword: String) {
  validate(
    mandatoryField("newPassword", newPassword),
    validateField(
      "newPassword",
      "invalid_password",
      Option(newPassword).exists(_.length >= 8),
      "Password must be at least 8 characters"
    )
  )
}

object ChangePasswordRequest {
  implicit val decoder: Decoder[ChangePasswordRequest] = deriveDecoder[ChangePasswordRequest]
}

final case class DeleteUserRequest(password: Option[String])

object DeleteUserRequest {
  implicit val decoder: Decoder[DeleteUserRequest] = deriveDecoder[DeleteUserRequest]
}

final case class SubscribeToNewsLetter(email: String) {
  validate(
    mandatoryField("email", email),
    validateEmail("email", email.toLowerCase),
    validateUserInput("email", email, None)
  )
}

object SubscribeToNewsLetter {
  implicit val decoder: Decoder[SubscribeToNewsLetter] = deriveDecoder[SubscribeToNewsLetter]
}

final case class ResendValidationEmailRequest(email: String)

object ResendValidationEmailRequest {
  implicit val decoder: Decoder[ResendValidationEmailRequest] = deriveDecoder[ResendValidationEmailRequest]
}
