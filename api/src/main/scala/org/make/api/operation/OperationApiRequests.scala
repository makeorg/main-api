package org.make.api.operation

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations.ApiModel
import org.make.core.Validation.{maxLength, requireValidSlug, validChoices, validate}
import org.make.core.operation.{OperationCountryConfiguration, OperationStatus, OperationTranslation}

@ApiModel
final case class ModerationCreateOperationRequest(slug: String,
                                                  translations: Seq[OperationTranslation],
                                                  defaultLanguage: String,
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
                                                  defaultLanguage: String,
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
                     defaultLanguage: String,
                     countriesConfiguration: Seq[OperationCountryConfiguration],
                     slug: String): Unit = {
    translations.foreach { translation =>
      validate(
        maxLength(s"translation.title[${translation.language}]", maxTitleLength, translation.title),
        maxLength("translation.language", maxLanguageLength, translation.language)
      )
    }
    countriesConfiguration.foreach { countryConfiguration =>
      validate(maxLength("countriesConfiguration.country", maxCountryLength, countryConfiguration.countryCode))
    }
    validate(
      maxLength("defaultLanguage", maxLanguageLength, defaultLanguage),
      maxLength("countryConfiguration", maxLanguageLength, defaultLanguage),
      requireValidSlug("slug", Some(slug), Some("Invalid slug"))
    )
  }

  def validateUpdate(translations: Seq[OperationTranslation],
                     defaultLanguage: String,
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
