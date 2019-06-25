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

package org.make.api.operation
import java.time.ZonedDateTime

import org.make.api.question.{PersistentQuestionServiceComponent, SearchQuestionRequest}
import org.make.api.sequence.{PersistentSequenceConfigurationComponent, SequenceConfiguration}
import org.make.api.technical.IdGeneratorComponent
import org.make.core.operation._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OperationOfQuestionService {
  // TODO: do we really need all these to be separate?
  def findByQuestionId(questionId: QuestionId): Future[Option[OperationOfQuestion]]
  def findByOperationId(operationId: OperationId): Future[Seq[OperationOfQuestion]]
  def findByQuestionSlug(slug: String): Future[Option[OperationOfQuestion]]
  def search(start: Int,
             end: Option[Int],
             sort: Option[String],
             order: Option[String],
             request: SearchOperationsOfQuestions): Future[Seq[OperationOfQuestion]]
  def update(operationOfQuestion: OperationOfQuestion, question: Question): Future[OperationOfQuestion]
  def count(request: SearchOperationsOfQuestions): Future[Int]

  /**
    * Deletes an OperationOfQuestion and all its associated objects:
    * - The associated Question
    * - The associated sequence configuration
    * @param questionId the operationOfQuestion to delete
    * @return a future to follow the completion
    */
  def delete(questionId: QuestionId): Future[Unit]

  /**
    * This function will:
    * - Create a new question
    * - Create a new sequence for this question
    * - Create a new OperationOfQuestion
    * @param parameters all needed parameters to create everything
    * @return the created OperationOfQuestion
    */
  def create(parameters: CreateOperationOfQuestion): Future[OperationOfQuestion]
}

final case class CreateOperationOfQuestion(operationId: OperationId,
                                           startDate: Option[ZonedDateTime],
                                           endDate: Option[ZonedDateTime],
                                           operationTitle: String,
                                           slug: String,
                                           country: Country,
                                           language: Language,
                                           question: String)

final case class SearchOperationsOfQuestions(questionIds: Option[Seq[QuestionId]],
                                             operationId: Option[OperationId],
                                             operationKind: Option[Seq[OperationKind]],
                                             openAt: Option[ZonedDateTime])

trait OperationOfQuestionServiceComponent {
  def operationOfQuestionService: OperationOfQuestionService
}

trait DefaultOperationOfQuestionServiceComponent extends OperationOfQuestionServiceComponent {
  this: PersistentQuestionServiceComponent
    with PersistentSequenceConfigurationComponent
    with PersistentOperationOfQuestionServiceComponent
    with IdGeneratorComponent =>

  override lazy val operationOfQuestionService: OperationOfQuestionService = new OperationOfQuestionService {

    override def search(start: Int,
                        end: Option[Int],
                        sort: Option[String],
                        order: Option[String],
                        request: SearchOperationsOfQuestions): Future[scala.Seq[OperationOfQuestion]] = {
      persistentOperationOfQuestionService.search(
        start,
        end,
        sort,
        order,
        request.questionIds,
        request.operationId,
        request.operationKind,
        request.openAt
      )
    }

    override def findByQuestionId(questionId: QuestionId): Future[Option[OperationOfQuestion]] = {
      persistentOperationOfQuestionService.getById(questionId)
    }

    override def findByOperationId(operationId: OperationId): Future[Seq[OperationOfQuestion]] = {
      persistentOperationOfQuestionService.find(Some(operationId))
    }

    override def findByQuestionSlug(slug: String): Future[Option[OperationOfQuestion]] = {
      persistentQuestionService.find(SearchQuestionRequest(maybeSlug = Some(slug))).flatMap { results =>
        results.headOption.map { question =>
          findByQuestionId(question.questionId)
        }.getOrElse(Future.successful(None))
      }
    }

    override def update(operationOfQuestion: OperationOfQuestion, question: Question): Future[OperationOfQuestion] = {
      for {
        _       <- persistentQuestionService.modify(question)
        updated <- persistentOperationOfQuestionService.modify(operationOfQuestion)
      } yield updated
    }

    /**
      * Deletes an OperationOfQuestion and all its associated objects:
      * - The associated Question
      * - The associated sequence configuration
      *
      * @param questionId the operationOfQuestion to delete
      * @return a future to follow the completion
      */
    override def delete(questionId: QuestionId): Future[Unit] = {

      for {
        _ <- persistentOperationOfQuestionService.delete(questionId)
        _ <- persistentSequenceConfigurationService.delete(questionId)
        _ <- persistentQuestionService.delete(questionId)
      } yield {}

    }

    /**
      * This function will:
      * - Create a new question
      * - Create a new sequence for this question
      * - Create a new OperationOfQuestion
      *
      * @param parameters all needed parameters to create everything
      * @return the created OperationOfQuestion
      */
    override def create(parameters: CreateOperationOfQuestion): Future[OperationOfQuestion] = {
      val questionId = idGenerator.nextQuestionId()
      val sequenceId = idGenerator.nextSequenceId()

      val question = Question(
        questionId = questionId,
        slug = parameters.slug,
        country = parameters.country,
        language = parameters.language,
        question = parameters.question,
        operationId = Some(parameters.operationId),
        themeId = None
      )

      val operationOfQuestion = OperationOfQuestion(
        questionId = questionId,
        operationId = parameters.operationId,
        startDate = parameters.startDate,
        endDate = parameters.endDate,
        operationTitle = parameters.operationTitle,
        landingSequenceId = sequenceId,
        canPropose = true,
        sequenceCardsConfiguration = SequenceCardsConfiguration.default,
        aboutUrl = None,
        metas = Metas(None, None, None),
        theme = QuestionTheme.default,
        description = OperationOfQuestion.defaultDescription
      )

      val sequenceConfiguration =
        SequenceConfiguration(sequenceId = sequenceId, questionId = questionId)

      for {
        _         <- persistentQuestionService.persist(question)
        _         <- persistentSequenceConfigurationService.persist(sequenceConfiguration)
        persisted <- persistentOperationOfQuestionService.persist(operationOfQuestion)
      } yield persisted
    }

    override def count(request: SearchOperationsOfQuestions): Future[Int] = {
      persistentOperationOfQuestionService.count(request.questionIds, request.operationId, request.openAt)
    }
  }
}
