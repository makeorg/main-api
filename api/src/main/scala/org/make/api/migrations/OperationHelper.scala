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

import org.make.api.MakeApi
import org.make.api.migrations.CreateOperation.QuestionConfiguration
import org.make.core.RequestContext
import org.make.core.operation.{
  FinalCard,
  IntroCard,
  Metas,
  Operation,
  OperationKind,
  OperationOfQuestion,
  PushProposalCard,
  QuestionTheme,
  SequenceCardsConfiguration,
  SignUpCard
}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.Language
import org.make.core.sequence.SequenceId
import org.make.core.user.UserId

import scala.concurrent.{ExecutionContext, Future}

trait OperationHelper {

  val emptyContext: RequestContext = RequestContext.empty
  val moderatorId = UserId("11111111-1111-1111-1111-111111111111")

  implicit def executor: ExecutionContext

  def createQuestionsAndSequences(api: MakeApi,
                                  operation: Operation,
                                  configuration: QuestionConfiguration): Future[Unit] = {

    val questionId: QuestionId = api.idGenerator.nextQuestionId()
    val sequenceId: SequenceId = api.idGenerator.nextSequenceId()

    api.persistentQuestionService
      .persist(
        Question(
          questionId = questionId,
          country = configuration.country,
          slug = configuration.slug,
          language = configuration.language,
          question = configuration.question,
          operationId = Some(operation.operationId)
        )
      )
      .flatMap { _ =>
        api.persistentOperationOfQuestionService.persist(
          OperationOfQuestion(
            questionId = questionId,
            operationId = operation.operationId,
            startDate = configuration.startDate,
            endDate = configuration.endDate,
            operationTitle = configuration.title,
            landingSequenceId = sequenceId,
            canPropose = configuration.canPropose,
            aboutUrl = configuration.aboutUrl,
            sequenceCardsConfiguration = SequenceCardsConfiguration(
              introCard = IntroCard(enabled = true, title = None, description = None),
              pushProposalCard = PushProposalCard(enabled = true),
              signUpCard = SignUpCard(enabled = true, title = None, nextCtaText = None),
              finalCard = FinalCard(
                enabled = true,
                sharingEnabled = false,
                title = None,
                shareDescription = None,
                learnMoreTitle = None,
                learnMoreTextButton = None,
                linkUrl = None
              )
            ),
            metas = Metas(title = None, description = None, picture = None),
            theme = QuestionTheme.default,
            description = OperationOfQuestion.defaultDescription,
            consultationImage = configuration.consultationImage,
            descriptionImage = None,
            displayResults = false
          )
        )
      }
      .flatMap { _ =>
        api.persistentSequenceConfigurationService
          .persist(
            configuration.sequenceConfiguration
              .copy(sequenceId = sequenceId, questionId = questionId)
          )
          .map(_ => ())
      }
  }

  def createOperation(api: MakeApi,
                      operationSlug: String,
                      defaultLanguage: Language,
                      countryConfigurations: Seq[QuestionConfiguration],
                      allowedSources: Seq[String]): Future[Operation] = {

    api.operationService
      .create(
        userId = moderatorId,
        slug = operationSlug,
        defaultLanguage = defaultLanguage,
        allowedSources = allowedSources,
        operationKind = OperationKind.PublicConsultation
      )
      .flatMap(api.operationService.findOne)
      .map(_.get)
      .flatMap { operation =>
        sequentially(countryConfigurations) { configuration =>
          createQuestionsAndSequences(api, operation, configuration)
        }.map(_ => operation)
      }
  }

  def createOperationIfNeeded(api: MakeApi,
                              defaultLanguage: Language,
                              operationSlug: String,
                              countryConfigurations: Seq[QuestionConfiguration],
                              allowedSources: Seq[String]): Future[Unit] = {

    api.operationService.findOneBySlug(operationSlug).flatMap {
      case Some(_) => Future.successful {}
      case None =>
        createOperation(api, operationSlug, defaultLanguage, countryConfigurations, allowedSources).map(_ => ())
    }
  }

}
