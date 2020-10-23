/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

package org.make.api.technical.graphql

import java.time.ZonedDateTime

import cats.Id
import cats.data.NonEmptyList
import org.make.api.operation.{OperationOfQuestionServiceComponent, SearchOperationsOfQuestions}
import org.make.api.question.{QuestionServiceComponent, SimpleQuestionWordingResponse}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import zio.query.DataSource

import scala.concurrent.{ExecutionContext, Future}

trait GraphQLQuestionServiceComponent {
  def questionDataSource: DataSource[Any, GetQuestion]
}

trait DefaultGraphQLQuestionServiceComponent extends GraphQLQuestionServiceComponent {
  this: QuestionServiceComponent with OperationOfQuestionServiceComponent =>

  override val questionDataSource: DataSource[Any, GetQuestion] = {
    def findFromIds(
      questionIds: Seq[QuestionId]
    )(executionContext: ExecutionContext): Future[Map[QuestionId, GraphQLQuestion]] = {
      implicit val ec: ExecutionContext = executionContext
      for {
        questions <- questionService.getQuestions(questionIds)
        ooqs      <- operationOfQuestionService.find(request = SearchOperationsOfQuestions(questionIds = Some(questionIds)))
      } yield {
        val questionMap = questions.map(q => q.questionId -> q).toMap
        val ooqMap = ooqs.map(ooq         => ooq.questionId -> ooq).toMap
        questionMap.keys.toSeq.flatMap { questionId =>
          for {
            question <- questionMap.get(questionId)
            ooq      <- ooqMap.get(questionId)
          } yield questionId -> GraphQLQuestion(
            questionId = questionId,
            slug = question.slug,
            wording = SimpleQuestionWordingResponse(title = ooq.operationTitle, question = question.question),
            countries = question.countries,
            language = question.language,
            startDate = ooq.startDate,
            endDate = ooq.endDate
          )
        }.toMap
      }
    }

    DataSourceHelper.one("questions-datasource", findFromIds)
  }
}
final case class GetQuestion(ids: QuestionId) extends IdsRequest[Id, QuestionId, GraphQLQuestion]

final case class GraphQLQuestion(
  questionId: QuestionId,
  slug: String,
  wording: SimpleQuestionWordingResponse,
  countries: NonEmptyList[Country],
  language: Language,
  startDate: ZonedDateTime,
  endDate: ZonedDateTime
)
