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

import org.make.api.migrations.CreateOperation.QuestionConfiguration
import org.make.core.reference.{Country, Language}

object ClimatParisOperation extends CreateOperation {
  override val operationSlug: String = "climatparis"

  override val defaultLanguage: Language = Language("fr")

  override val questions: Seq[QuestionConfiguration] = Seq(
    QuestionConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      slug = operationSlug,
      title = "Comment lutter contre le changement climatique à Paris ?",
      question = "Comment lutter contre le changement climatique à Paris ?",
      startDate = None,
      endDate = None
    )
  )

  override val allowedSources: Seq[String] = Seq("core")

  override val runInProduction: Boolean = false
}
