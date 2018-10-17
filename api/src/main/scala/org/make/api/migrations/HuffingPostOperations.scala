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
import org.make.api.migrations.CreateOperation.CountryConfiguration
import org.make.core.reference.{Country, Language}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object HuffingPostOperations extends OperationHelper with Migration {

  implicit override val executor: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  val operations: Map[String, CreateOperation.CountryConfiguration] = Map(
    "politique-huffpost" -> CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      title = "Politique",
      startDate = LocalDate.parse("2018-08-06"),
      endDate = Some(LocalDate.parse("2020-08-06")),
      tags = Seq.empty
    ),
    "economie-huffpost" -> CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      title = "Économie",
      startDate = LocalDate.parse("2018-08-06"),
      endDate = Some(LocalDate.parse("2020-08-06")),
      tags = Seq.empty
    ),
    "international-huffpost" -> CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      title = "International",
      startDate = LocalDate.parse("2018-08-06"),
      endDate = Some(LocalDate.parse("2020-08-06")),
      tags = Seq.empty
    ),
    "culture-huffpost" -> CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      title = "Culture",
      startDate = LocalDate.parse("2018-08-06"),
      endDate = Some(LocalDate.parse("2020-08-06")),
      tags = Seq.empty
    ),
    "ecologie-huffpost" -> CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      title = "Écologie",
      startDate = LocalDate.parse("2018-08-06"),
      endDate = Some(LocalDate.parse("2020-08-06")),
      tags = Seq.empty
    ),
    "societe-huffpost" -> CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      title = "Société",
      startDate = LocalDate.parse("2018-08-06"),
      endDate = Some(LocalDate.parse("2020-08-06")),
      tags = Seq.empty
    ),
    "education-huffpost" -> CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      title = "Éducation",
      startDate = LocalDate.parse("2018-08-06"),
      endDate = Some(LocalDate.parse("2020-08-06")),
      tags = Seq.empty
    )
  )

  val allowedSources: Seq[String] = Seq("huffingpost")

  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}

  override def migrate(api: MakeApi): Future[Unit] = {
    sequentially(operations.toSeq) {
      case (slug: String, countryConfiguration: CountryConfiguration) =>
        createOperationIfNeeded(api, countryConfiguration.language, slug, Seq(countryConfiguration), allowedSources)
    }
  }

  override def runInProduction: Boolean = false
}
