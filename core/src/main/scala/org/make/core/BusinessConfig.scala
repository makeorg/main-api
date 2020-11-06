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

package org.make.core

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations.ApiModelProperty
import org.make.core.reference.{Country, Language}

import scala.annotation.meta.field

sealed trait BusinessConfig {
  val proposalMaxLength: Int
  val supportedCountries: Seq[CountryConfiguration]
}

final case class CountryConfiguration(
  @(ApiModelProperty @field)(dataType = "string", example = "BE") countryCode: Country,
  @(ApiModelProperty @field)(dataType = "string", example = "fr") defaultLanguage: Language,
  @(ApiModelProperty @field)(dataType = "list[string]") supportedLanguages: Seq[Language]
)
object CountryConfiguration {
  implicit val encoder: Encoder[CountryConfiguration] = deriveEncoder[CountryConfiguration]
  implicit val decoder: Decoder[CountryConfiguration] = deriveDecoder[CountryConfiguration]
}

final case class FrontConfiguration(
  proposalMinLength: Int,
  override val proposalMaxLength: Int,
  override val supportedCountries: Seq[CountryConfiguration]
) extends BusinessConfig

object BusinessConfig {
  val defaultProposalMaxLength: Int = 140
  val supportedCountries: Seq[CountryConfiguration] = Seq(
    CountryConfiguration(
      countryCode = Country("FR"),
      defaultLanguage = Language("fr"),
      supportedLanguages = Seq(Language("fr"))
    ),
    CountryConfiguration(
      countryCode = Country("IT"),
      defaultLanguage = Language("it"),
      supportedLanguages = Seq(Language("it"))
    ),
    CountryConfiguration(
      countryCode = Country("GB"),
      defaultLanguage = Language("en"),
      supportedLanguages = Seq(Language("en"))
    ),
    CountryConfiguration(
      countryCode = Country("DE"),
      defaultLanguage = Language("de"),
      supportedLanguages = Seq(Language("de"))
    ),
    CountryConfiguration(
      countryCode = Country("AT"),
      defaultLanguage = Language("de"),
      supportedLanguages = Seq(Language("de"))
    ),
    CountryConfiguration(
      countryCode = Country("BE"),
      defaultLanguage = Language("nl"),
      supportedLanguages = Seq(Language("fr"), Language("nl"))
    ),
    CountryConfiguration(
      countryCode = Country("BG"),
      defaultLanguage = Language("bg"),
      supportedLanguages = Seq(Language("bg"))
    ),
    CountryConfiguration(
      countryCode = Country("CY"),
      defaultLanguage = Language("el"),
      supportedLanguages = Seq(Language("el"))
    ),
    CountryConfiguration(
      countryCode = Country("CZ"),
      defaultLanguage = Language("cs"),
      supportedLanguages = Seq(Language("cs"))
    ),
    CountryConfiguration(
      countryCode = Country("DK"),
      defaultLanguage = Language("da"),
      supportedLanguages = Seq(Language("da"))
    ),
    CountryConfiguration(
      countryCode = Country("EE"),
      defaultLanguage = Language("et"),
      supportedLanguages = Seq(Language("et"))
    ),
    CountryConfiguration(
      countryCode = Country("ES"),
      defaultLanguage = Language("es"),
      supportedLanguages = Seq(Language("es"))
    ),
    CountryConfiguration(
      countryCode = Country("FI"),
      defaultLanguage = Language("fi"),
      supportedLanguages = Seq(Language("fi"))
    ),
    CountryConfiguration(
      countryCode = Country("GR"),
      defaultLanguage = Language("el"),
      supportedLanguages = Seq(Language("el"))
    ),
    CountryConfiguration(
      countryCode = Country("HR"),
      defaultLanguage = Language("hr"),
      supportedLanguages = Seq(Language("hr"))
    ),
    CountryConfiguration(
      countryCode = Country("HU"),
      defaultLanguage = Language("hu"),
      supportedLanguages = Seq(Language("hu"))
    ),
    CountryConfiguration(
      countryCode = Country("IE"),
      defaultLanguage = Language("en"),
      supportedLanguages = Seq(Language("en"))
    ),
    CountryConfiguration(
      countryCode = Country("LT"),
      defaultLanguage = Language("lt"),
      supportedLanguages = Seq(Language("lt"))
    ),
    CountryConfiguration(
      countryCode = Country("LU"),
      defaultLanguage = Language("fr"),
      supportedLanguages = Seq(Language("fr"), Language("de"))
    ),
    CountryConfiguration(
      countryCode = Country("LV"),
      defaultLanguage = Language("lv"),
      supportedLanguages = Seq(Language("lv"))
    ),
    CountryConfiguration(
      countryCode = Country("MT"),
      defaultLanguage = Language("mt"),
      supportedLanguages = Seq(Language("mt"))
    ),
    CountryConfiguration(
      countryCode = Country("NL"),
      defaultLanguage = Language("nl"),
      supportedLanguages = Seq(Language("nl"))
    ),
    CountryConfiguration(
      countryCode = Country("PL"),
      defaultLanguage = Language("pl"),
      supportedLanguages = Seq(Language("pl"))
    ),
    CountryConfiguration(
      countryCode = Country("PT"),
      defaultLanguage = Language("pt"),
      supportedLanguages = Seq(Language("pt"))
    ),
    CountryConfiguration(
      countryCode = Country("RO"),
      defaultLanguage = Language("ro"),
      supportedLanguages = Seq(Language("ro"))
    ),
    CountryConfiguration(
      countryCode = Country("SE"),
      defaultLanguage = Language("sv"),
      supportedLanguages = Seq(Language("sv"))
    ),
    CountryConfiguration(
      countryCode = Country("SI"),
      defaultLanguage = Language("sl"),
      supportedLanguages = Seq(Language("sl"))
    ),
    CountryConfiguration(
      countryCode = Country("SK"),
      defaultLanguage = Language("sk"),
      supportedLanguages = Seq(Language("sk"))
    )
  )

  implicit class CountryLanguageOps(val country: Country) extends AnyVal {
    def language: Language =
      supportedCountries.find(_.countryCode == country).map(_.defaultLanguage).getOrElse(Language("en"))
  }

}

object FrontConfiguration {
  implicit val encoder: Encoder[FrontConfiguration] = deriveEncoder[FrontConfiguration]
  implicit val decoder: Decoder[FrontConfiguration] = deriveDecoder[FrontConfiguration]

  val defaultProposalMinLength: Int = 12

  def default(
    proposalMinLength: Int = defaultProposalMinLength,
    proposalMaxLength: Int = BusinessConfig.defaultProposalMaxLength,
    supportedCountries: Seq[CountryConfiguration] = BusinessConfig.supportedCountries
  ): FrontConfiguration =
    FrontConfiguration(
      proposalMinLength = proposalMinLength,
      proposalMaxLength = proposalMaxLength,
      supportedCountries = supportedCountries
    )
}
