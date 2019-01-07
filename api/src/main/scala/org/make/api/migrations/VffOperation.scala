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

object VffOperation extends CreateOperation {

  override val operationSlug: String = "vff"
  override val defaultLanguage: Language = Language("fr")
  override val questions: Seq[QuestionConfiguration] = Seq(
    QuestionConfiguration(
      Country("FR"),
      Language("fr"),
      slug = operationSlug,
      title = "comment-lutter-contre-les-violences-faites-aux-femmes",
      question = "comment-lutter-contre-les-violences-faites-aux-femmes",
      endDate = Some(LocalDate.parse("2018-03-01")),
      startDate = Some(LocalDate.parse("2018-01-01"))
    ),
    QuestionConfiguration(
      Country("IT"),
      Language("it"),
      slug = "vff-it",
      title = "Come far fronte alla violenza sulle donne?",
      question = "Come far fronte alla violenza sulle donne?",
      endDate = Some(LocalDate.parse("2018-05-30")),
      startDate = Some(LocalDate.parse("2018-03-01"))
    ),
    QuestionConfiguration(
      Country("GB"),
      Language("en"),
      slug = "vff-gb",
      title = "How to combat violence against women?",
      question = "How to combat violence against women?",
      endDate = Some(LocalDate.parse("2018-05-30")),
      startDate = Some(LocalDate.parse("2018-03-01"))
    )
  )

  override val allowedSources: Seq[String] = Seq("core")

  override val runInProduction: Boolean = false
}
