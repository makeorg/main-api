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

package org.make.api.migrations
import java.time.LocalDate

import org.make.api.migrations.CreateOperation.CountryConfiguration
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId

object VffOperation extends CreateOperation {

  override val operationSlug: String = "vff"
  override val defaultLanguage: Language = Language("fr")
  override val countryConfigurations: Seq[CreateOperation.CountryConfiguration] = Seq(
    CountryConfiguration(
      Country("FR"),
      Language("fr"),
      slug = operationSlug,
      Seq(
        TagId("signalement"),
        TagId("police-justice"),
        TagId("education-sensibilisation"),
        TagId("image-des-femmes"),
        TagId("independance-financiere"),
        TagId("soutien-psychologique"),
        TagId("hebergement"),
        TagId("transports"),
        TagId("monde-du-travail"),
        TagId("monde-medical"),
        TagId("agissements-sexistes"),
        TagId("violences-sexuelles"),
        TagId("harcelement"),
        TagId("agressions-physiques"),
        TagId("violences-conjugales"),
        TagId("traditions-nefastes-mutilations"),
        TagId("action-publique"),
        TagId("prevention"),
        TagId("protection"),
        TagId("reponses")
      ),
      "comment-lutter-contre-les-violences-faites-aux-femmes",
      endDate = Some(LocalDate.parse("2018-03-01")),
      startDate = LocalDate.parse("2018-01-01")
    ),
    CountryConfiguration(
      Country("IT"),
      Language("it"),
      slug = "vff-it",
      Seq(
        TagId("avviso"),
        TagId("polizia-giustizia"),
        TagId("mondo-del-lavoro"),
        TagId("trasporti"),
        TagId("azione-pubblica"),
        TagId("sistemazione"),
        TagId("educazione-sensibilizzazione"),
        TagId("sostegno-psicologico"),
        TagId("indipendenza-finanziaria"),
        TagId("comportamento-sessista"),
        TagId("mutilazioni"),
        TagId("violenze-sessuali"),
        TagId("molestia"),
        TagId("tradizioni-dannose"),
        TagId("immagine-della-donna"),
        TagId("violenza-coniugale"),
        TagId("prevenzione"),
        TagId("protezione"),
        TagId("risposte"),
        TagId("aggressione")
      ),
      "Come far fronte alla violenza sulle donne?",
      endDate = Some(LocalDate.parse("2018-05-30")),
      startDate = LocalDate.parse("2018-03-01")
    ),
    CountryConfiguration(
      Country("GB"),
      Language("en"),
      slug = "vff-gb",
      Seq(
        TagId("description"),
        TagId("police-justice"),
        TagId("professional-environment"),
        TagId("transportation"),
        TagId("public-action"),
        TagId("accommodation"),
        TagId("education-awareness"),
        TagId("psychological-support"),
        TagId("financial-independence"),
        TagId("sexist-behaviour"),
        TagId("mutilations"),
        TagId("sexual-violence"),
        TagId("harassment"),
        TagId("harmful-questionnable-traditions"),
        TagId("image-of-women"),
        TagId("domestic-violence"),
        TagId("prevention-en"),
        TagId("protection"),
        TagId("responses"),
        TagId("aggression")
      ),
      "How to combat violence against women?",
      endDate = Some(LocalDate.parse("2018-05-30")),
      startDate = LocalDate.parse("2018-03-01")
    )
  )

  override val allowedSources: Seq[String] = Seq("core")

  override val runInProduction: Boolean = false
}
