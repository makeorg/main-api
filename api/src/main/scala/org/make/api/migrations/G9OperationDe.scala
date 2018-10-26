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

import akka.pattern.ask
import akka.util.Timeout
import cats.data.OptionT
import cats.implicits._
import org.make.api.MakeApi
import org.make.api.migrations.CreateOperation.CountryConfiguration
import org.make.api.sequence.CreateSequenceCommand
import org.make.core.operation.{Operation, OperationCountryConfiguration, OperationId, OperationTranslation}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.{SequenceId, SequenceStatus}
import org.make.core.user.UserId
import org.make.core.{RequestContext, SlugHelper}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object G9OperationDe extends Migration {

  implicit val executor: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}

  val emptyContext: RequestContext = RequestContext.empty
  val moderatorId = UserId("11111111-1111-1111-1111-111111111111")

  val countryConfiguration: CountryConfiguration =
    CountryConfiguration(
      country = Country("DE"),
      language = Language("de"),
      title = "Wie kann man europäische digitale Champions hervorbringen?",
      startDate = LocalDate.parse("2018-10-25"),
      endDate = Some(LocalDate.parse("2019-01-14")),
      tags = Seq.empty
    )

  def updateOperation(api: MakeApi,
                      operation: Operation,
                      sequenceId: SequenceId,
                      questionId: QuestionId): Future[Option[OperationId]] = {
    val countryConfigurations: Seq[OperationCountryConfiguration] =
      operation.countriesConfiguration ++ Seq(
        OperationCountryConfiguration(
          countryCode = countryConfiguration.country,
          tagIds = Seq.empty,
          landingSequenceId = sequenceId,
          startDate = Some(countryConfiguration.startDate),
          endDate = countryConfiguration.endDate,
          questionId = Some(questionId)
        )
      )
    val translations = operation.translations ++ Seq(
      OperationTranslation(title = countryConfiguration.title, language = countryConfiguration.language)
    )

    api.operationService.update(
      operation.operationId,
      moderatorId,
      countriesConfiguration = Some(countryConfigurations),
      translations = Some(translations)
    )
  }

  def createSequence(api: MakeApi,
                     operationId: OperationId,
                     countryConfiguration: CountryConfiguration): Future[Option[SequenceId]] = {

    implicit val timeout: Timeout = Timeout(20.seconds)

    val landingSequenceId = api.idGenerator.nextSequenceId()

    (api.sequenceCoordinator ? CreateSequenceCommand(
      sequenceId = landingSequenceId,
      title = countryConfiguration.title,
      slug = SlugHelper(countryConfiguration.title),
      themeIds = Seq.empty,
      operationId = Some(operationId),
      requestContext = emptyContext,
      moderatorId = moderatorId,
      status = SequenceStatus.Published,
      searchable = false
    )).map(_ => Some(landingSequenceId))
  }

  def createQuestion(api: MakeApi,
                     operationId: OperationId,
                     countryConfiguration: CountryConfiguration): Future[Option[Question]] = {
    api.persistentQuestionService
      .persist(
        Question(
          questionId = api.idGenerator.nextQuestionId(),
          country = countryConfiguration.country,
          language = countryConfiguration.language,
          question = countryConfiguration.title,
          operationId = Some(operationId),
          themeId = None
        )
      )
      .map(question => Some(question))
  }

  override def migrate(api: MakeApi): Future[Unit] = {

    api.operationService.findOneBySlug(G9Operation.operationSlug).map { maybeOperation =>
      if (maybeOperation.forall(!_.countriesConfiguration.exists(_.countryCode.value == "DE"))) {
        val updatedOperation: OptionT[Future, OperationId] = for {
          g9Operation      <- OptionT(Future.successful(maybeOperation))
          sequenceId       <- OptionT(createSequence(api, g9Operation.operationId, countryConfiguration))
          question         <- OptionT(createQuestion(api, g9Operation.operationId, countryConfiguration))
          updatedOperation <- OptionT(updateOperation(api, g9Operation, sequenceId, question.questionId))
        } yield updatedOperation

        updatedOperation.value.map(_ => ())
      } else {
        Future.successful({})
      }
    }
  }

  override val runInProduction: Boolean = true
}
