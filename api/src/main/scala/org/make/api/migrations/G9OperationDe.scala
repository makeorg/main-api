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

import com.typesafe.scalalogging.StrictLogging
import org.make.api.MakeApi
import org.make.api.migrations.CreateOperation.QuestionConfiguration
import org.make.core.reference.{Country, Language}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object G9OperationDe extends Migration with OperationHelper with StrictLogging {

  implicit val executor: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}

  val question: QuestionConfiguration =
    QuestionConfiguration(
      country = Country("DE"),
      language = Language("de"),
      slug = "digital-champion-de",
      title = "Wie kann man europäische digitale Champions hervorbringen?",
      question = "Wie kann man europäische digitale Champions hervorbringen?",
      startDate = LocalDate.parse("2018-10-25"),
      endDate = Some(LocalDate.parse("2019-01-14"))
    )

  override def migrate(api: MakeApi): Future[Unit] = {
    api.operationService.findOneBySlug(G9Operation.operationSlug).map {
      case None =>
        logger.error("Unable to find G9+ operation to add it the DE question!")
        Future.failed(new IllegalStateException("Unable to find G9+ operation to add it the DE question!"))
      case Some(operation) =>
        if (operation.questions.exists(_.question.slug == question.slug)) {
          Future.successful {}
        } else {
          createQuestionsAndSequences(api, operation, question)
        }
    }
  }

  override val runInProduction: Boolean = true
}
