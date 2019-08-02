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

package org.make.api.technical.elasticsearch

import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import com.sksamuel.elastic4s.IndexAndType
import com.typesafe.scalalogging.StrictLogging
import org.make.api.operation.{
  OperationOfQuestionSearchEngine,
  OperationOfQuestionSearchEngineComponent,
  OperationServiceComponent
}
import org.make.api.question.QuestionServiceComponent
import org.make.core.operation.indexed.IndexedOperationOfQuestion
import org.make.core.operation.{OperationOfQuestion, SimpleOperation}
import org.make.core.question.Question

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OperationOfQuestionIndexationStream
    extends IndexationStream
    with OperationOfQuestionSearchEngineComponent
    with QuestionServiceComponent
    with OperationServiceComponent
    with StrictLogging {

  object OperationOfQuestionStream {

    def flowIndexOrganisations(operationOfQuestionIndexName: String): Flow[OperationOfQuestion, Done, NotUsed] =
      grouped[OperationOfQuestion].via(runIndexOperationOfQuestion(operationOfQuestionIndexName))

    def runIndexOperationOfQuestion(
      operationOfQuestionIndexName: String
    ): Flow[Seq[OperationOfQuestion], Done, NotUsed] = {
      Flow[Seq[OperationOfQuestion]]
        .mapAsync(singleAsync)(
          organisations => executeIndexOperationOfQuestions(organisations, operationOfQuestionIndexName)
        )
    }

    private def executeIndexOperationOfQuestions(operationOfQuestions: Seq[OperationOfQuestion],
                                                 operationOfQuestionIndexName: String): Future[Done] = {

      val futureQuestion: Future[Seq[Question]] =
        questionService.getQuestions(operationOfQuestions.map(_.questionId))
      val futureOperation: Future[Seq[SimpleOperation]] =
        operationService.findSimple()

      val futureIndexedOperationOfQuestion = for {
        questions  <- futureQuestion
        operations <- futureOperation
      } yield (questions, operations)

      futureIndexedOperationOfQuestion.map {
        case (questions, operations) =>
          operationOfQuestions.map { operationOfQuestion =>
            for {
              question  <- questions.find(_.questionId == operationOfQuestion.questionId)
              operation <- operations.find(_.operationId == operationOfQuestion.operationId)
            } yield
              IndexedOperationOfQuestion
                .createFromOperationOfQuestion(operationOfQuestion, operation, question)
          }
      }.map(_.flatten).flatMap { operationOfQuestions =>
        elasticsearchOperationOfQuestionAPI
          .indexOperationOfQuestions(
            operationOfQuestions,
            Some(
              IndexAndType(operationOfQuestionIndexName, OperationOfQuestionSearchEngine.operationOfQuestionIndexName)
            )
          )
          .recoverWith {
            case e =>
              logger.error("Indexing organisations failed", e)
              Future.successful(Done)
          }
      }
    }
  }

}
