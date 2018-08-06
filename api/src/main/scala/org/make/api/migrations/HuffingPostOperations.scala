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
import org.make.core.operation.{OperationCountryConfiguration, OperationTranslation}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceStatus
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object HuffingPostOperations extends Migration {
  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}

  implicit val executor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
  val emptyContext: RequestContext = RequestContext.empty
  val moderatorId = UserId("11111111-1111-1111-1111-111111111111")

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
    )
  )

  val defaultLanguage: Language = Language("fr")

  override def migrate(api: MakeApi): Future[Unit] = {

    Future {
      operations.foreach {
        case (operationSlug, countryConfiguration) =>
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
              .map { sequenceResponse =>
                SequenceWithCountryLanguage(
                  sequence = sequenceResponse,
                  country = countryConfiguration.country,
                  language = countryConfiguration.language
                )
              }
          ).flatMap { sequenceWithCountryLanguage =>
            retryableFuture(
              api.operationService
                .create(
                  userId = moderatorId,
                  slug = operationSlug,
                  defaultLanguage = defaultLanguage,
                  translations = Seq(
                    OperationTranslation(
                      title = countryConfiguration.title,
                      language = sequenceWithCountryLanguage.language
                    )
                  ),
                  countriesConfiguration = Seq(
                    OperationCountryConfiguration(
                      countryCode = sequenceWithCountryLanguage.country,
                      tagIds = countryConfiguration.tags,
                      landingSequenceId = sequenceWithCountryLanguage.sequence.sequenceId,
                      startDate = Some(countryConfiguration.startDate),
                      endDate = countryConfiguration.endDate
                    )
                  )
                )
            ).map { operationId =>
              api.sequenceService.update(
                sequenceId = sequenceWithCountryLanguage.sequence.sequenceId,
                moderatorId = moderatorId,
                requestContext = emptyContext,
                title = None,
                status = Some(SequenceStatus.Published),
                operationId = Some(operationId),
                themeIds = Seq.empty
              )

              Future
                .traverse(countryConfiguration.tags) { tag =>
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
                        country = sequenceWithCountryLanguage.country,
                        language = sequenceWithCountryLanguage.language
                      )
                    }
                  })
                }
            }
          }

        case _ => Future.failed(new RuntimeException(s"Error When importingHuffingPost operations"))
      }
    }
  }

  override val runInProduction: Boolean = true
}
