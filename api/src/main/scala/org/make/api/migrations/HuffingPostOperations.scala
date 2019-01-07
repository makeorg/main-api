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
import java.util.concurrent.Executors

import org.make.api.MakeApi
import org.make.api.migrations.CreateOperation.QuestionConfiguration
import org.make.core.reference.{Country, Language}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object HuffingPostOperations extends OperationHelper with Migration {

  implicit override val executor: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  val operations: Map[String, QuestionConfiguration] = Map(
    "politique-huffpost" -> QuestionConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      slug = "politique-huffpost",
      question = "politique-huffpost",
      title = "Politique",
      startDate = Some(LocalDate.parse("2018-08-06")),
      endDate = Some(LocalDate.parse("2020-08-06"))
    ),
    "economie-huffpost" -> QuestionConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      slug = "economie-huffpost",
      title = "Économie",
      question = "Économie",
      startDate = Some(LocalDate.parse("2018-08-06")),
      endDate = Some(LocalDate.parse("2020-08-06"))
    ),
    "international-huffpost" -> QuestionConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      slug = "international-huffpost",
      title = "International",
      question = "International",
      startDate = Some(LocalDate.parse("2018-08-06")),
      endDate = Some(LocalDate.parse("2020-08-06"))
    ),
    "culture-huffpost" -> QuestionConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      slug = "culture-huffpost",
      title = "Culture",
      question = "Culture",
      startDate = Some(LocalDate.parse("2018-08-06")),
      endDate = Some(LocalDate.parse("2020-08-06"))
    ),
    "ecologie-huffpost" -> QuestionConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      slug = "ecologie-huffpost",
      title = "Écologie",
      question = "Écologie",
      startDate = Some(LocalDate.parse("2018-08-06")),
      endDate = Some(LocalDate.parse("2020-08-06"))
    ),
    "societe-huffpost" -> QuestionConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      slug = "societe-huffpost",
      title = "Société",
      question = "Société",
      startDate = Some(LocalDate.parse("2018-08-06")),
      endDate = Some(LocalDate.parse("2020-08-06"))
    ),
    "education-huffpost" -> QuestionConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      slug = "education-huffpost",
      title = "Éducation",
      question = "Éducation",
      startDate = Some(LocalDate.parse("2018-08-06")),
      endDate = Some(LocalDate.parse("2020-08-06"))
    )
  )

  val allowedSources: Seq[String] = Seq("huffingpost")

  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}

  override def migrate(api: MakeApi): Future[Unit] = {
    sequentially(operations.toSeq) {
      case (slug: String, questionConfiguration: QuestionConfiguration) =>
        createOperationIfNeeded(api, questionConfiguration.language, slug, Seq(questionConfiguration), allowedSources)
    }
  }

  override def runInProduction: Boolean = false
}
