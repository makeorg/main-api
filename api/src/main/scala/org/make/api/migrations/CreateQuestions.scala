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
import com.typesafe.scalalogging.StrictLogging
import org.make.api.MakeApi
import org.make.core.question.Question
import org.make.core.reference.{Country, Language}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object CreateQuestions extends Migration with StrictLogging {

  private val languages =
    Map(
      Country("FR") -> Language("fr"),
      Country("IT") -> Language("it"),
      Country("GB") -> Language("en"),
      Country("DE") -> Language("de")
    )

  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}
  override def migrate(api: MakeApi): Future[Unit] = {
    // Insert Themes
    api.themeService.findAll().flatMap { themes =>
      var result = Future.successful {}
      themes.foreach { theme =>
        result = result.flatMap { _ =>
          api.questionService
            .findQuestion(Some(theme.themeId), None, theme.country, languages(theme.country))
            .flatMap {
              case Some(_) => Future.successful {}
              case None =>
                api.persistentQuestionService
                  .persist(
                    Question(
                      questionId = api.idGenerator.nextQuestionId(),
                      slug = theme.translations.head.slug,
                      country = theme.country,
                      language = languages(theme.country),
                      question = theme.translations.headOption.map(_.title).getOrElse(theme.themeId.value),
                      operationId = None,
                      themeId = Some(theme.themeId),
                    )
                  )
                  .map(_ => ())
            }
        }.recoverWith {
          case e =>
            logger.error("", e)
            Future.successful {}
        }
      }
      result
    }
  }

  override def runInProduction: Boolean = false

}
