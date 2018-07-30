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

object LpaeOperation extends CreateOperation {
  override val operationSlug: String = "lpae"

  override val defaultLanguage: Language = Language("fr")

  override val countryConfigurations: Seq[CreateOperation.CountryConfiguration] = Seq(
    CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      tags = Seq(
        TagId("lpae-prevention"),
        TagId("lpae-repression"),
        TagId("lpae-curation"),
        TagId("lpae-democratie"),
        TagId("lpae-efficacite-de-l-etat"),
        TagId("lpae-ecologie"),
        TagId("lpae-agriculture"),
        TagId("lpae-pauvrete"),
        TagId("lpae-chomage"),
        TagId("lpae-conditions-de-travail"),
        TagId("lpae-finance"),
        TagId("lpae-entreprenariat"),
        TagId("lpae-recherche-innovation"),
        TagId("lpae-bouleversements-technologiques"),
        TagId("lpae-jeunesse"),
        TagId("lpae-inegalites"),
        TagId("lpae-discriminations"),
        TagId("lpae-culture"),
        TagId("lpae-vieillissement"),
        TagId("lpae-handicap"),
        TagId("lpae-logement"),
        TagId("lpae-sante"),
        TagId("lpae-solidarites"),
        TagId("lpae-justice"),
        TagId("lpae-sécurite"),
        TagId("lpae-terrorisme"),
        TagId("lpae-developpement"),
        TagId("lpae-guerres"),
        TagId("lpae-ue"),
        TagId("lpae-gouvernance-mondiale"),
        TagId("lpae-conquete-spatiale"),
        TagId("lpae-politique-eco-regulation"),
        TagId("lpae-aides-subventions"),
        TagId("lpae-fiscalite"),
        TagId("lpae-politique-monetaire"),
        TagId("lpae-couverture-sociale"),
        TagId("lpae-medical"),
        TagId("lpae-mobilites"),
        TagId("lpae-sensibilisation"),
        TagId("lpae-education"),
        TagId("lpae-sanctions"),
        TagId("lpae-rse"),
        TagId("lpae-nouvelles-technologies"),
        TagId("lpae-fonctionnement-des-institutions"),
        TagId("lpae-participation-citoyenne"),
        TagId("lpae-cible-etats-gouvernements"),
        TagId("lpae-cible-collectivites-territoriales"),
        TagId("lpae-cible-elus"),
        TagId("lpae-cible-individus"),
        TagId("lpae-cible-entreprises"),
        TagId("lpae-cible-syndicats"),
        TagId("lpae-cible-associations-ong"),
        TagId("lpae-cible-instances-mondiales"),
        TagId("lpae-action-publique"),
        TagId("lpae-action-des-individus"),
        TagId("lpae-action-entreprises"),
        TagId("lpae-action-syndicats"),
        TagId("lpae-action-associations-ong"),
        TagId("lpae-action-instances-mondiales")
      ),
      title = "Vous avez les clés du monde, que changez-vous ?",
      startDate = LocalDate.parse("2018-01-01"),
      endDate = None
    )
  )

  override val runInProduction: Boolean = false
}
