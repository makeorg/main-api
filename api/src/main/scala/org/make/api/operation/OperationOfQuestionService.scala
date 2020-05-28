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
import org.make.core.operation.indexed.OperationOfQuestionSearchResult
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OperationOfQuestionService {
  // TODO: do we really need all these to be separate?
  def findByQuestionId(questionId: QuestionId): Future[Option[OperationOfQuestion]]
  def findByOperationId(operationId: OperationId): Future[Seq[OperationOfQuestion]]
  def findByQuestionSlug(slug: String): Future[Option[OperationOfQuestion]]
  def find(
    start: Int = 0,
    end: Option[Int] = None,
    sort: Option[String] = None,
    order: Option[String] = None,
    request: SearchOperationsOfQuestions = SearchOperationsOfQuestions()
  ): Future[Seq[OperationOfQuestion]]
  def search(searchQuery: OperationOfQuestionSearchQuery): Future[OperationOfQuestionSearchResult]
  def updateWithQuestion(operationOfQuestion: OperationOfQuestion, question: Question): Future[OperationOfQuestion]
  def update(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion]
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

final case class CreateOperationOfQuestion(
  operationId: OperationId,
  startDate: Option[ZonedDateTime],
  endDate: Option[ZonedDateTime],
  operationTitle: String,
  slug: String,
  country: Country,
  language: Language,
  question: String,
  shortTitle: Option[String],
  consultationImage: Option[String],
  descriptionImage: Option[String],
  actions: Option[String]
)

final case class SearchOperationsOfQuestions(
  questionIds: Option[Seq[QuestionId]] = None,
  operationIds: Option[Seq[OperationId]] = None,
  operationKind: Option[Seq[OperationKind]] = None,
  openAt: Option[ZonedDateTime] = None
)

trait OperationOfQuestionServiceComponent {
  def operationOfQuestionService: OperationOfQuestionService
}

trait DefaultOperationOfQuestionServiceComponent extends OperationOfQuestionServiceComponent {
  this: PersistentQuestionServiceComponent
    with PersistentSequenceConfigurationComponent
    with PersistentOperationOfQuestionServiceComponent
    with OperationOfQuestionSearchEngineComponent
    with IdGeneratorComponent =>

  override lazy val operationOfQuestionService: OperationOfQuestionService = new DefaultOperationOfQuestionService

  class DefaultOperationOfQuestionService extends OperationOfQuestionService {

    override def find(
      start: Int = 0,
      end: Option[Int] = None,
      sort: Option[String] = None,
      order: Option[String] = None,
      request: SearchOperationsOfQuestions = SearchOperationsOfQuestions()
    ): Future[scala.Seq[OperationOfQuestion]] = {
      persistentOperationOfQuestionService.search(
        start,
        end,
        sort,
        order,
        request.questionIds,
        request.operationIds,
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

    override def search(searchQuery: OperationOfQuestionSearchQuery): Future[OperationOfQuestionSearchResult] = {
      elasticsearchOperationOfQuestionAPI.searchOperationOfQuestions(searchQuery)
    }

    override def updateWithQuestion(
      operationOfQuestion: OperationOfQuestion,
      question: Question
    ): Future[OperationOfQuestion] = {
      for {
        _       <- persistentQuestionService.modify(question)
        updated <- persistentOperationOfQuestionService.modify(operationOfQuestion)
      } yield updated
    }

    override def update(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion] = {
      persistentOperationOfQuestionService.modify(operationOfQuestion)
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
        shortTitle = parameters.shortTitle,
        operationId = Some(parameters.operationId)
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
        description = OperationOfQuestion.defaultDescription,
        consultationImage = parameters.consultationImage,
        descriptionImage = parameters.descriptionImage,
        displayResults = false,
        resultsLink = None,
        proposalsCount = 0,
        participantsCount = 0,
        actions = parameters.actions,
        featured = false
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
      persistentOperationOfQuestionService.count(request.questionIds, request.operationIds, request.openAt)
    }
  }
}
