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

import org.make.api.migrations.CreateOperation.QuestionConfiguration
import org.make.core.reference.{Country, Language}

object MakeEuropeOperation extends CreateOperation {
  override val operationSlug: String = "make-europe"

  override val defaultLanguage: Language = Language("en")

  override val questions: Seq[QuestionConfiguration] = Seq(
    QuestionConfiguration(
      country = Country("GB"),
      language = Language("en"),
      slug = operationSlug,
      title = "Consultation européenne (démo)",
      question = "Consultation européenne (démo)",
      startDate = LocalDate.parse("2018-03-26"),
      endDate = Some(LocalDate.parse("2018-04-30"))
    )
  )

  override val allowedSources: Seq[String] = Seq("core")

  override val runInProduction: Boolean = false
}
