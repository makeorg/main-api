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
                      api.idGenerator.nextQuestionId(),
                      theme.country,
                      languages(theme.country),
                      theme.translations.headOption.map(_.title).getOrElse(theme.themeId.value),
                      None,
                      Some(theme.themeId)
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
    val insertOperations = insertThemes
      .flatMap(_ => api.operationService.find(slug = None, country = None, maybeSource = None, openAt = None))
      .flatMap { operations =>
        var future = Future.successful {}
        operations.flatMap { operation =>
          operation.countriesConfiguration.map { conf =>
            val question = operation.translations.find(_.language == languages(conf.countryCode)).map(_.title)
            (operation.operationId, question.getOrElse(operation.operationId.value), conf)
          }
        }.foreach {
          case (operationId, question, configuration) =>
            future = future.flatMap { _ =>
              api.questionService
                .findQuestion(None, Some(operationId), configuration.countryCode, languages(configuration.countryCode))
                .flatMap {
                  case Some(_) => Future.successful {}
                  case None =>
                    api.persistentQuestionService
                      .persist(
                        Question(
                          api.idGenerator.nextQuestionId(),
                          configuration.countryCode,
                          languages(configuration.countryCode),
                          question,
                          Some(operationId),
                          None
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

  override def runInProduction: Boolean = true

}
