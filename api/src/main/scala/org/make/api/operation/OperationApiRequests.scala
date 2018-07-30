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

package org.make.api.operation

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations.ApiModel
import org.make.core.Validation.{maxLength, requireValidSlug, validChoices, validate}
import org.make.core.operation.{OperationCountryConfiguration, OperationStatus, OperationTranslation}
import org.make.core.reference.Language

@ApiModel
final case class ModerationCreateOperationRequest(slug: String,
                                                  translations: Seq[OperationTranslation],
                                                  defaultLanguage: Language,
                                                  countriesConfiguration: Seq[OperationCountryConfiguration]) {
  OperationValidation.validateCreate(
    translations = translations,
    defaultLanguage = defaultLanguage,
    countriesConfiguration = countriesConfiguration,
    slug = slug
  )
}

object ModerationCreateOperationRequest {
  implicit val decoder: Decoder[ModerationCreateOperationRequest] = deriveDecoder[ModerationCreateOperationRequest]
}
@ApiModel
final case class ModerationUpdateOperationRequest(status: String,
                                                  slug: String,
                                                  translations: Seq[OperationTranslation],
                                                  defaultLanguage: Language,
                                                  countriesConfiguration: Seq[OperationCountryConfiguration]) {
  OperationValidation.validateUpdate(
    translations = translations,
    defaultLanguage = defaultLanguage,
    countriesConfiguration = countriesConfiguration,
    status = status,
    slug = slug
  )
}

object ModerationUpdateOperationRequest {
  implicit val decoder: Decoder[ModerationUpdateOperationRequest] = deriveDecoder[ModerationUpdateOperationRequest]
}

private object OperationValidation {
  private val maxTitleLength = 256
  private val maxLanguageLength = 3
  private val maxCountryLength = 3

  def validateCreate(translations: Seq[OperationTranslation],
                     defaultLanguage: Language,
                     countriesConfiguration: Seq[OperationCountryConfiguration],
                     slug: String): Unit = {
    translations.foreach { translation =>
      validate(
        maxLength(s"translation.title[${translation.language}]", maxTitleLength, translation.title),
        maxLength("translation.language", maxLanguageLength, translation.language.value)
      )
    }
    countriesConfiguration.foreach { countryConfiguration =>
      validate(maxLength("countriesConfiguration.country", maxCountryLength, countryConfiguration.countryCode.value))
    }
    validate(
      maxLength("defaultLanguage", maxLanguageLength, defaultLanguage.value),
      maxLength("countryConfiguration", maxLanguageLength, defaultLanguage.value),
      requireValidSlug("slug", Some(slug), Some("Invalid slug"))
    )
  }

  def validateUpdate(translations: Seq[OperationTranslation],
                     defaultLanguage: Language,
                     countriesConfiguration: Seq[OperationCountryConfiguration],
                     status: String,
                     slug: String): Unit = {
    validateCreate(translations, defaultLanguage, countriesConfiguration, slug)
    val validStatusChoices: Seq[String] = OperationStatus.statusMap.toSeq.map {
      case (name, _) => name
    }
    validate(validChoices(fieldName = "status", userChoices = Seq(status), validChoices = validStatusChoices))
  }
}
