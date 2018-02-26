package org.make.api.technical.businessconfig

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import org.make.core.reference.Theme

sealed trait BusinessConfig {
  val proposalMinLength: Int
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

case class BackofficeConfiguration(override val proposalMinLength: Int,
                                   override val proposalMaxLength: Int,
                                   override val themes: Seq[Theme],
                                   override val supportedCountries: Seq[CountryConfiguration],
                                   nVotesTriggerConnexion: Int,
                                   nPendingProposalsTriggerEmailModerator: Int,
                                   minProposalsPerSequence: Int,
                                   maxProposalsPerSequence: Int,
                                   reasonsForRefusal: Seq[String])
    extends BusinessConfig

case class FrontConfiguration(override val proposalMinLength: Int,
                              override val proposalMaxLength: Int,
                              override val themes: Seq[Theme],
                              override val supportedCountries: Seq[CountryConfiguration])
    extends BusinessConfig

object BusinessConfig {
  val defaultProposalMinLength: Int = 12
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

  def default(proposalMinLength: Int = BusinessConfig.defaultProposalMinLength,
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

  val defaultNumberVotesTriggerConnexion: Int = 5
  val defaultNumberPendingProposalsTriggerEmailModerator: Int = 50
  val defaultMinProposalsPerSequence: Int = 3
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
      "Other"
    )

  def default(
    proposalMinLength: Int = BusinessConfig.defaultProposalMinLength,
    proposalMaxLength: Int = BusinessConfig.defaultProposalMaxLength,
    themes: Seq[Theme] = BusinessConfig.themes,
    nVotesTriggerConnexion: Int = defaultNumberVotesTriggerConnexion,
    nPendingProposalsTriggerEmailModerator: Int = defaultNumberPendingProposalsTriggerEmailModerator,
    minProposalsPerSequence: Int = defaultMinProposalsPerSequence,
    maxProposalsPerSequence: Int = defaultMaxProposalsPerSequence,
    reasonsForRefusal: Seq[String] = defaultReasonsForRefusal,
    supportedCountries: Seq[CountryConfiguration] = BusinessConfig.supportedCountries
  ): BackofficeConfiguration =
    BackofficeConfiguration(
      proposalMinLength = proposalMinLength,
      proposalMaxLength = proposalMaxLength,
      themes = themes,
      reasonsForRefusal = reasonsForRefusal,
      nVotesTriggerConnexion = nVotesTriggerConnexion,
      nPendingProposalsTriggerEmailModerator = nPendingProposalsTriggerEmailModerator,
      minProposalsPerSequence = minProposalsPerSequence,
      maxProposalsPerSequence = maxProposalsPerSequence,
      supportedCountries = supportedCountries
    )
}
