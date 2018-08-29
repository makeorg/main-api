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
import org.make.api.migrations.CreateOperation.{CountryConfiguration, SequenceWithCountryLanguage}
import org.make.api.sequence.SequenceResponse
import org.make.core.operation.{OperationCountryConfiguration, OperationTranslation}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceStatus
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait CreateOperation extends Migration {

  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}

  implicit val executor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  val emptyContext: RequestContext = RequestContext.empty
  val moderatorId = UserId("11111111-1111-1111-1111-111111111111")

  def operationSlug: String
  def defaultLanguage: Language

  def countryConfigurations: Seq[CountryConfiguration]

  private def findConfiguration(country: Country, language: Language): CountryConfiguration = {
    countryConfigurations
      .find(configuration => configuration.country == country && configuration.language == language)
      .getOrElse(CountryConfiguration(country, language, Seq.empty, "", startDate = LocalDate.now(), endDate = None))
  }

  override def migrate(api: MakeApi): Future[Unit] = {

    api.operationService.findOneBySlug(operationSlug).flatMap {
      case Some(_) => Future.successful {}
      case None =>
        Future
          .traverse(countryConfigurations) { configuration =>
            retryableFuture(
              api.sequenceService
                .create(
                  userId = moderatorId,
                  requestContext = emptyContext,
                  createdAt = DateHelper.now(),
                  title = operationSlug,
                  themeIds = Seq.empty,
                  operationId = None,
                  searchable = true
                )
                .map(_.get)
                .map(response => SequenceWithCountryLanguage(response, configuration.country, configuration.language))
            )
          }
          .flatMap { sequenceResponses =>
            retryableFuture(
              api.operationService
                .create(
                  userId = moderatorId,
                  slug = operationSlug,
                  defaultLanguage = defaultLanguage,
                  translations = sequenceResponses.map { response =>
                    val configuration = findConfiguration(response.country, response.language)
                    OperationTranslation(title = configuration.title, language = response.language)
                  },
                  countriesConfiguration = sequenceResponses.map { response =>
                    val configuration = findConfiguration(response.country, response.language)
                    OperationCountryConfiguration(
                      countryCode = response.country,
                      tagIds = configuration.tags,
                      landingSequenceId = response.sequence.sequenceId,
                      startDate = Some(configuration.startDate),
                      endDate = configuration.endDate,
                      questionId = None
                    )
                  }
                )
                .map { operationId =>
                  sequenceResponses.map {
                    sequence =>
                      api.sequenceService.update(
                        sequenceId = sequence.sequence.sequenceId,
                        moderatorId = moderatorId,
                        requestContext = emptyContext,
                        title = None,
                        status = Some(SequenceStatus.Published),
                        operationId = Some(operationId),
                        themeIds = Seq.empty
                      )

                      val configuration: CountryConfiguration = findConfiguration(sequence.country, sequence.language)
                      Future
                        .traverse(configuration.tags) { tag =>
                          retryableFuture(api.tagService.getTag(tag).map { maybeFoundTag =>
                            maybeFoundTag.map { foundTag =>
                              api.tagService.updateTag(
                                tagId = foundTag.tagId,
                                label = foundTag.label,
                                display = foundTag.display,
                                tagTypeId = foundTag.tagTypeId,
                                weight = foundTag.weight,
                                operationId = Some(operationId),
                                themeId = None,
                                country = sequence.country,
                                language = sequence.language
                              )
                            }
                          })
                        }
                  }
                }
            )
          }
    }
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
