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

package org.make.api.technical.businessconfig

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import org.make.core.reference.{Country, Language, Theme}

sealed trait BusinessConfig {
  val proposalMaxLength: Int
  val themes: Seq[Theme]
  val supportedCountries: Seq[CountryConfiguration]
}

case class CountryConfiguration(countryCode: Country,
                                defaultLanguage: Language,
                                supportedLanguages: Seq[Language],
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
      countryCode = Country("FR"),
      defaultLanguage = Language("fr"),
      supportedLanguages = Seq(Language("fr")),
      coreIsAvailable = true
    ),
    CountryConfiguration(
      countryCode = Country("IT"),
      defaultLanguage = Language("it"),
      supportedLanguages = Seq(Language("it")),
      coreIsAvailable = false
    ),
    CountryConfiguration(
      countryCode = Country("GB"),
      defaultLanguage = Language("en"),
      supportedLanguages = Seq(Language("en")),
      coreIsAvailable = false
    ),
    CountryConfiguration(
      countryCode = Country("DE"),
      defaultLanguage = Language("de"),
      supportedLanguages = Seq(Language("de")),
      coreIsAvailable = false
    )
  )

  def validateCountry(country: Country): Country =
    supportedCountries.map(_.countryCode).find(_ == country).getOrElse(Country("FR"))

  def validateLanguage(country: Country, language: Language): Language =
    supportedCountries
      .find(_.countryCode == country)
      .map { countryConfiguration =>
        countryConfiguration.supportedLanguages
          .find(_ == language)
          .getOrElse(countryConfiguration.defaultLanguage)
      }
      .getOrElse(Language("fr"))

  def coreIsAvailableForCountry(country: Country): Boolean = {
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
