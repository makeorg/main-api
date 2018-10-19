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

object NiceMatinOperation extends CreateOperation {
  override val operationSlug: String = "plan-climat"

  override val defaultLanguage: Language = Language("fr")

  override val countryConfigurations: Seq[CreateOperation.CountryConfiguration] = Seq(
    CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      title = "#MaSolutionPourLeClimat",
      startDate = LocalDate.parse("2018-10-18"),
      endDate = Some(LocalDate.parse("2018-12-06")),
      tags = Seq.empty
    )
  )

  override val allowedSources: Seq[String] = Seq("core")

  override val runInProduction: Boolean = true
}
