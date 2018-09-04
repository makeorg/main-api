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
import org.make.api.sequence.SequenceResponse
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait CreateOperation extends Migration with OperationHelper {

  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}

  implicit override val executor: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  def operationSlug: String
  def defaultLanguage: Language
  def countryConfigurations: Seq[CountryConfiguration]

  override def migrate(api: MakeApi): Future[Unit] = {
    createOperationIfNeeded(api, defaultLanguage, operationSlug, countryConfigurations)
  }
}

object CreateOperation {
  final case class CountryConfiguration(country: Country,
                                        language: Language,
                                        tags: Seq[TagId],
                                        title: String,
                                        startDate: LocalDate,
                                        endDate: Option[LocalDate])

  final case class SequenceWithCountryLanguage(sequence: SequenceResponse, country: Country, language: Language)

}
