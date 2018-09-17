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
import java.util.concurrent.Executors

import org.make.api.MakeApi
import org.make.core.reference.{Country, Language}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object HuffingPostTags extends Migration with TagHelper {

  implicit override val executor: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}

  override def migrate(api: MakeApi): Future[Unit] = {
    val operationsTagsSource: Map[String, String] = Map(
      "politique-huffpost" -> "fixtures/huffingpost/tags_politique.csv",
      "economie-huffpost" -> "fixtures/huffingpost/tags_economie.csv",
      "international-huffpost" -> "fixtures/huffingpost/tags_international.csv",
      "culture-huffpost" -> "fixtures/huffingpost/tags_culture.csv",
      "ecologie-huffpost" -> "fixtures/huffingpost/tags_ecologie.csv",
      "societe-huffpost" -> "fixtures/huffingpost/tags_societe.csv",
      "education-huffpost" -> "fixtures/huffingpost/tags_education.csv"
    )

    sequentially(operationsTagsSource.toSeq) {
      case (operationSlug, file) =>
        api.operationService.findOneBySlug(operationSlug).flatMap {
          case None => throw new IllegalStateException(s"Unable to find operation $operationSlug")
          case Some(operation) =>
            api.questionService.findQuestion(None, Some(operation.operationId), Country("FR"), Language("fr")).flatMap {
              case None           => throw new IllegalStateException(s"Unable to find question for operation $operationSlug")
              case Some(question) => importTags(api, file, question)
            }
        }

    }

  }

  override def runInProduction: Boolean = false
}
