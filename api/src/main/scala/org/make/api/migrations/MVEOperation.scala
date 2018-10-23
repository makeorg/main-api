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

object MVEOperation extends CreateOperation {
  override val operationSlug: String = "mieux-vivre-ensemble"

  override val defaultLanguage: Language = Language("fr")

  override val countryConfigurations: Seq[CreateOperation.CountryConfiguration] = Seq(
    CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      slug = operationSlug,
      title = "Comment mieux vivre ensemble ?",
      startDate = LocalDate.parse("2018-03-12"),
      endDate = None,
      tags = Seq(
        TagId("curation"),
        TagId("prevention"),
        TagId("repression"),
        TagId("action-associations"),
        TagId("action-syndicats"),
        TagId("action-entreprises"),
        TagId("action-publique"),
        TagId("cible-citadins"),
        TagId("cible-ruraux"),
        TagId("cible-jeunes"),
        TagId("cible-personnes-agees"),
        TagId("cible-associations"),
        TagId("cible-syndicats"),
        TagId("cible-entreprises"),
        TagId("cible-individus"),
        TagId("cible-elus"),
        TagId("cible-collectivites-territoriales"),
        TagId("cible-etats-gouvernements"),
        TagId("numerique"),
        TagId("engagement-associatif"),
        TagId("partage"),
        TagId("participation-citoyenne"),
        TagId("effort-individuel"),
        TagId("rse"),
        TagId("fiscalite"),
        TagId("aides-subventions"),
        TagId("sanctions"),
        TagId("couverture-sociale"),
        TagId("regulation"),
        TagId("sensibilisation"),
        TagId("education"),
        TagId("laicite"),
        TagId("civisme"),
        TagId("sport"),
        TagId("culture"),
        TagId("solidarites"),
        TagId("sans-abri"),
        TagId("pauvrete"),
        TagId("mixite-sociale"),
        TagId("discriminations"),
        TagId("dialogue"),
        TagId("intergenerationnel"),
        TagId("vieillissement"),
        TagId("jeunesse"),
        TagId("handicap"),
        TagId("urbanisme"),
        TagId("ruralite"),
        TagId("fracture-numerique"),
        TagId("isolement")
      )
    )
  )

  override val allowedSources: Seq[String] = Seq("core")

  override val runInProduction: Boolean = false
}
