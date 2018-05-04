package org.make.api.technical.businessconfig

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import org.make.core.reference.Theme

sealed trait BusinessConfig {
  val proposalMaxLength: Int
  val themes: Seq[Theme]
  val supportedCountries: Seq[CountryConfiguration]
}

case class CountryConfiguration(countryCode: String,
                                defaultLanguage: String,
                                supportedLanguages: Seq[String],
                                coreIsAvailable: Boolean)
object CountryConfiguration {
  implicit val encoder: ObjectEncoder[CountryConfiguration] = deriveEncoder[CountryConfiguration]
  implicit val decoder: Decoder[CountryConfiguration] = deriveDecoder[CountryConfiguration]
}

case class BackofficeConfiguration(override val proposalMaxLength: Int,
                                   override val themes: Seq[Theme],
                                   override val supportedCountries: Seq[CountryConfiguration],
                                   reasonsForRefusal: Seq[String])
    extends BusinessConfig

case class FrontConfiguration(proposalMinLength: Int,
                              override val proposalMaxLength: Int,
                              override val themes: Seq[Theme],
                              override val supportedCountries: Seq[CountryConfiguration])
    extends BusinessConfig

object BusinessConfig {
  val defaultProposalMaxLength: Int = 140
  val themes: Seq[Theme] = Seq.empty
  val supportedCountries = Seq(
    CountryConfiguration(
      countryCode = "FR",
      defaultLanguage = "fr",
      supportedLanguages = Seq("fr"),
      coreIsAvailable = true
    ),
    CountryConfiguration(
      countryCode = "IT",
      defaultLanguage = "it",
      supportedLanguages = Seq("it"),
      coreIsAvailable = false
    ),
    CountryConfiguration(
      countryCode = "GB",
      defaultLanguage = "en",
      supportedLanguages = Seq("en"),
      coreIsAvailable = false
    )
  )

  def validateCountry(country: String): String =
    supportedCountries.map(_.countryCode).find(_ == country).getOrElse("FR")

  def validateLanguage(country: String, language: String): String =
    supportedCountries
      .find(_.countryCode == country)
      .map { countryConfiguration =>
        countryConfiguration.supportedLanguages
          .find(_ == language)
          .getOrElse(countryConfiguration.defaultLanguage)
      }
      .getOrElse("fr")

  def coreIsAvailableForCountry(country: String): Boolean = {
    supportedCountries.find(_.countryCode == country).exists(_.coreIsAvailable)
  }

}

object FrontConfiguration {
  implicit val encoder: ObjectEncoder[FrontConfiguration] = deriveEncoder[FrontConfiguration]
  implicit val decoder: Decoder[FrontConfiguration] = deriveDecoder[FrontConfiguration]

  val defaultProposalMinLength: Int = 12

  def default(proposalMinLength: Int = defaultProposalMinLength,
              proposalMaxLength: Int = BusinessConfig.defaultProposalMaxLength,
              themes: Seq[Theme] = BusinessConfig.themes,
              supportedCountries: Seq[CountryConfiguration] = BusinessConfig.supportedCountries): FrontConfiguration =
    FrontConfiguration(
      proposalMinLength = proposalMinLength,
      proposalMaxLength = proposalMaxLength,
      themes = themes,
      supportedCountries = supportedCountries
    )
}

object BackofficeConfiguration {
  implicit val encoder: ObjectEncoder[BackofficeConfiguration] = deriveEncoder[BackofficeConfiguration]
  implicit val decoder: Decoder[BackofficeConfiguration] = deriveDecoder[BackofficeConfiguration]

  val defaultMaxProposalsPerSequence: Int = 12
  val defaultReasonsForRefusal: Seq[String] =
    Seq(
      "Incomprehensible",
      "Off-topic",
      "Partisan",
      "Legal",
      "Advertising",
      "MultipleIdeas",
      "InvalidLanguage",
      "Rudeness",
      "Test",
      "Other"
    )

  def default(
    proposalMaxLength: Int = BusinessConfig.defaultProposalMaxLength,
    themes: Seq[Theme] = BusinessConfig.themes,
    reasonsForRefusal: Seq[String] = defaultReasonsForRefusal,
    supportedCountries: Seq[CountryConfiguration] = BusinessConfig.supportedCountries
  ): BackofficeConfiguration =
    BackofficeConfiguration(
      proposalMaxLength = proposalMaxLength,
      themes = themes,
      reasonsForRefusal = reasonsForRefusal,
      supportedCountries = supportedCountries
    )
}
