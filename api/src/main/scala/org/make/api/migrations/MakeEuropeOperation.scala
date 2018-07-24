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

import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId

object MakeEuropeOperation extends CreateOperation {
  override val operationSlug: String = "make-europe"

  override val defaultLanguage: Language = Language("en")

  override val countryConfigurations: Seq[CreateOperation.CountryConfiguration] = Seq(
    CreateOperation.CountryConfiguration(
      country = Country("GB"),
      language = Language("en"),
      title = "Consultation européenne (démo)",
      startDate = LocalDate.parse("2018-03-26"),
      endDate = Some(LocalDate.parse("2018-04-30")),
      tags = Seq(
        TagId("european-commission"),
        TagId("social"),
        TagId("taxes"),
        TagId("environment"),
        TagId("minimum-wage"),
        TagId("finance"),
        TagId("budget"),
        TagId("students"),
        TagId("erasmus")
      )
    )
  )

  override val runInProduction: Boolean = false
}
