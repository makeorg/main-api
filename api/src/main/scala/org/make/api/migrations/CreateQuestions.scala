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
    val insertThemes = api.themeService.findAll().flatMap { themes =>
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
    val insertOperations = insertThemes.flatMap(_ => api.operationService.find(maybeSource = None)).flatMap {
      operations =>
        var future = Future.successful {}
        operations.flatMap { operation =>
          operation.countriesConfiguration.map { conf =>
            val question = operation.translations.find(_.language == languages(conf.countryCode)).map(_.title)
            (operation.operationId, operation.slug, question.getOrElse(operation.operationId.value), conf)
          }
        }.foreach {
          case (operationId, operationSlug, question, configuration) =>
            future = future.flatMap { _ =>
              api.questionService
                .findQuestion(None, Some(operationId), configuration.countryCode, languages(configuration.countryCode))
                .flatMap {
                  case Some(_) => Future.successful {}
                  case None    =>
                    // Trick to handle vff
                    val questionSlug = if (configuration.countryCode == Country("FR")) { operationSlug } else {
                      s"$operationSlug-${configuration.countryCode.value.toLowerCase()}"
                    }
                    api.persistentQuestionService
                      .persist(
                        Question(
                          questionId = api.idGenerator.nextQuestionId(),
                          slug = questionSlug,
                          country = configuration.countryCode,
                          language = languages(configuration.countryCode),
                          question = question,
                          operationId = Some(operationId),
                          themeId = None
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
        future
    }

    insertOperations
  }

  override def runInProduction: Boolean = false

}
