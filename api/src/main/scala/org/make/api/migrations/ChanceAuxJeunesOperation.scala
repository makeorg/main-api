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

object ChanceAuxJeunesOperation extends CreateOperation {
  override val operationSlug: String = "chance-aux-jeunes"

  override val defaultLanguage: Language = Language("fr")

  override val countryConfigurations: Seq[CreateOperation.CountryConfiguration] = Seq(
    CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      tags = Seq(
        TagId("fracture-numerique"),
        TagId("ruralite"),
        TagId("villes"),
        TagId("mixite-sociale"),
        TagId("discriminations"),
        TagId("handicap"),
        TagId("pauvrete-precarite"),
        TagId("egalite-des-chances"),
        TagId("consommation"),
        TagId("emploi"),
        TagId("entreprenariat"),
        TagId("formation"),
        TagId("recherche"),
        TagId("orientation"),
        TagId("logement"),
        TagId("sante"),
        TagId("culture"),
        TagId("sport"),
        TagId("mobilite"),
        TagId("medias"),
        TagId("justice"),
        TagId("sécurite"),
        TagId("laicite-religions"),
        TagId("droits-libertes"),
        TagId("civisme"),
        TagId("solidarites"),
        TagId("democratie"),
        TagId("ecologie"),
        TagId("international"),
        TagId("education"),
        TagId("sensibilisation"),
        TagId("numerique"),
        TagId("urbanisme"),
        TagId("politique-economique"),
        TagId("budget"),
        TagId("regulation"),
        TagId("sanctions"),
        TagId("services-publics"),
        TagId("aides-subventions"),
        TagId("fiscalite"),
        TagId("rse"),
        TagId("effort-individuel"),
        TagId("engagement-associatif"),
        TagId("participation-citoyenne"),
        TagId("cible--collectivites-territoriales"),
        TagId("cible--associations"),
        TagId("cible--citadins"),
        TagId("cible--elus"),
        TagId("cible--entreprises"),
        TagId("cible--etats--gouvernements"),
        TagId("cible--individus"),
        TagId("cible--ruraux"),
        TagId("action--publique"),
        TagId("action--des-individus"),
        TagId("action--des-entreprises"),
        TagId("action--des-syndicats"),
        TagId("action--des-associations"),
        TagId("prevention"),
        TagId("repression"),
        TagId("curation"),
        TagId("couverture-sociale"),
      ),
      title = "Comment donner une chance à chaque jeune ?",
      startDate = LocalDate.parse("2018-04-04"),
      endDate = Some(LocalDate.parse("2018-07-02"))
    )
  )

  override val runInProduction: Boolean = true

}
