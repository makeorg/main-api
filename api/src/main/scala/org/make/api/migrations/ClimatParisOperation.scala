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
import org.make.core.tag.TagId

object ClimatParisOperation extends CreateOperation {
  override val operationSlug: String = "climatparis"

  override val defaultLanguage: String = "fr"

  override val countryConfigurations: Seq[CreateOperation.CountryConfiguration] = Seq(
    CountryConfiguration(
      country = "FR",
      language = "fr",
      tags = Seq(
        TagId("pollution"),
        TagId("entreprises-emploi"),
        TagId("qualite-de-vie"),
        TagId("alimentation"),
        TagId("energies-renouvelables"),
        TagId("bio"),
        TagId("sante"),
        TagId("agriculture"),
        TagId("circuits-courts"),
        TagId("recyclage-zero-dechets"),
        TagId("consommation-responsable"),
        TagId("energies-traditionnelles"),
        TagId("nouvelles-technologies"),
        TagId("urbanisme-habitat"),
        TagId("transports"),
        TagId("fiscalite-subventions"),
        TagId("sensibilisation-education"),
        TagId("solidarite"),
        TagId("action-publique"),
        TagId("participation-citoyenne")
      ),
      title = "Comment lutter contre le changement climatique à Paris ?",
      startDate = LocalDate.parse("2018-01-01"),
      endDate = None
    )
  )

  override val runInProduction: Boolean = false
}
