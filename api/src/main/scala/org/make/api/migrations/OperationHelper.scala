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

import java.util.concurrent.atomic.AtomicInteger

import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import org.make.api.MakeApi
import org.make.api.migrations.CreateOperation.CountryConfiguration
import org.make.api.sequence.CreateSequenceCommand
import org.make.core.operation.{Operation, OperationCountryConfiguration, OperationTranslation}
import org.make.core.question.Question
import org.make.core.reference.Language
import org.make.core.sequence.{SequenceId, SequenceStatus}
import org.make.core.tag.{TagDisplay, TagType}
import org.make.core.user.UserId
import org.make.core.{RequestContext, SlugHelper}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

trait OperationHelper extends StrictLogging {

  val emptyContext: RequestContext = RequestContext.empty
  val moderatorId = UserId("11111111-1111-1111-1111-111111111111")

  implicit def executor: ExecutionContext

  def createQuestionsAndSequences(api: MakeApi,
                                  operation: Operation,
                                  configuration: OperationCountryConfiguration,
                                  countryConfiguration: CountryConfiguration): Future[Unit] = {

    implicit val timeout: Timeout = Timeout(20.seconds)

    (
      api.sequenceCoordinator ? CreateSequenceCommand(
        sequenceId = configuration.landingSequenceId,
        title = countryConfiguration.title,
        slug = SlugHelper(countryConfiguration.title),
        themeIds = Seq.empty,
        operationId = Some(operation.operationId),
        requestContext = emptyContext,
        moderatorId = moderatorId,
        status = SequenceStatus.Published,
        searchable = false
      )
    ).flatMap { _ =>
      api.persistentQuestionService.persist(
        Question(
          questionId = api.idGenerator.nextQuestionId(),
          country = countryConfiguration.country,
          language = countryConfiguration.language,
          question = countryConfiguration.title,
          operationId = Some(operation.operationId),
          themeId = None
        )
      )
    }.flatMap { question =>
      val weights = new AtomicInteger()
      sequentially(countryConfiguration.tags) { tagId =>
        api.tagService
          .createTag(
            tagId.value,
            tagTypeId = TagType.LEGACY.tagTypeId,
            question = question,
            display = if (tagId.value.contains("--")) { TagDisplay.Hidden } else { TagDisplay.Displayed },
            weight = weights.getAndIncrement()
          )
          .map(_ => ())
      }
    }.map(_ => ())
  }

  def createOperation(api: MakeApi,
                      operationSlug: String,
                      defaultLanguage: Language,
                      countryConfigurations: Seq[CountryConfiguration],
                      allowedSources: Seq[String]): Future[Operation] = {

    val configurationsBySequence: Map[SequenceId, CountryConfiguration] =
      countryConfigurations.map(configuration => api.idGenerator.nextSequenceId() -> configuration).toMap

    api.operationService
      .create(
        userId = moderatorId,
        slug = operationSlug,
        defaultLanguage = defaultLanguage,
        translations = countryConfigurations.map { configuration =>
          OperationTranslation(title = configuration.title, language = configuration.language)
        },
        countriesConfiguration = configurationsBySequence.toSeq.map {
          case (questionId, configuration) =>
            OperationCountryConfiguration(
              countryCode = configuration.country,
              tagIds = Seq.empty,
              landingSequenceId = questionId,
              startDate = Some(configuration.startDate),
              endDate = configuration.endDate,
              questionId = None
            )
        },
        allowedSources = allowedSources
      )
      .flatMap(api.operationService.findOne)
      .map(_.get)
      .flatMap { operation =>
        Future
          .traverse(operation.countriesConfiguration) { configuration =>
            val countryConfiguration = configurationsBySequence(configuration.landingSequenceId)
            createQuestionsAndSequences(api, operation, configuration, countryConfiguration)
          }
          .map(_ => operation)
      }
  }

  def createOperationIfNeeded(api: MakeApi,
                              defaultLanguage: Language,
                              operationSlug: String,
                              countryConfigurations: Seq[CountryConfiguration],
                              allowedSources: Seq[String]): Future[Unit] = {

    api.operationService.findOneBySlug(operationSlug).flatMap {
      case Some(_) => Future.successful {}
      case None =>
        createOperation(api, operationSlug, defaultLanguage, countryConfigurations, allowedSources).map(_ => ())
    }
  }

}
