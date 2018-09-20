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

package org.make.api.question

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.question.DefaultPersistentQuestionServiceComponent.PersistentQuestion
import org.make.api.technical.DatabaseTransactions.RichDatabase
import org.make.api.technical.ShortenedNames
import org.make.core.DateHelper
import org.make.core.operation.OperationId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language, ThemeId}
import scalikejdbc.{ResultName, WrappedResultSet, _}

import scala.concurrent.Future

trait PersistentQuestionServiceComponent {
  def persistentQuestionService: PersistentQuestionService
}

trait PersistentQuestionService {
  def find(country: Option[Country],
           language: Option[Language],
           operation: Option[OperationId],
           theme: Option[ThemeId]): Future[Seq[Question]]

  def getById(questionId: QuestionId): Future[Option[Question]]
  def persist(question: Question): Future[Question]

}

trait DefaultPersistentQuestionServiceComponent extends PersistentQuestionServiceComponent {
  this: MakeDBExecutionContextComponent =>

  lazy val persistentQuestionService: PersistentQuestionService = new PersistentQuestionService with ShortenedNames {

    private val column = PersistentQuestion.column
    private val questionAlias = PersistentQuestion.questionAlias

    override def find(country: Option[Country],
                      language: Option[Language],
                      operation: Option[OperationId],
                      theme: Option[ThemeId]): Future[Seq[Question]] = {

      implicit val context: EC = readExecutionContext
      Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentQuestion.as(questionAlias))
            .where(
              sqls.toAndConditionOpt(
                country.map(c    => sqls.eq(questionAlias.country, c.value)),
                language.map(l   => sqls.eq(questionAlias.language, l.value)),
                operation.map(op => sqls.eq(questionAlias.operationId, op.value)),
                theme.map(th     => sqls.eq(questionAlias.themeId, th.value))
              )
            )
        }.map(PersistentQuestion.apply()).list().apply()
      }).map(_.map(_.toQuestion))

    }

    override def getById(questionId: QuestionId): Future[Option[Question]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentQuestion.as(questionAlias))
            .where(sqls.eq(questionAlias.questionId, questionId.value))
        }.map(PersistentQuestion.apply()).single.apply()
      }).map(_.map(_.toQuestion))
    }

    override def persist(question: Question): Future[Question] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          val now = DateHelper.now()
          insert
            .into(PersistentQuestion)
            .namedValues(
              column.questionId -> question.questionId.value,
              column.createdAt -> now,
              column.updatedAt -> now,
              column.question -> question.question,
              column.country -> question.country.value,
              column.language -> question.language.value,
              column.themeId -> question.themeId.map(_.value),
              column.operationId -> question.operationId.map(_.value)
            )
        }.execute().apply()
      }).map(_ => question)
    }
  }
}

object DefaultPersistentQuestionServiceComponent {

  case class PersistentQuestion(questionId: String,
                                country: String,
                                language: String,
                                question: String,
                                createdAt: ZonedDateTime,
                                updatedAt: ZonedDateTime,
                                operationId: Option[String],
                                themeId: Option[String]) {

    def toQuestion: Question = {
      Question(
        questionId = QuestionId(this.questionId),
        country = Country(this.country),
        language = Language(this.language),
        question = this.question,
        operationId = this.operationId.map(OperationId(_)),
        themeId = this.themeId.map(ThemeId(_))
      )
    }
  }

  object PersistentQuestion extends SQLSyntaxSupport[PersistentQuestion] with ShortenedNames with StrictLogging {

    override val columnNames: Seq[String] =
      Seq("question_id", "country", "language", "question", "created_at", "updated_at", "operation_id", "theme_id")

    override val tableName: String = "question"

    lazy val questionAlias: SyntaxProvider[PersistentQuestion] = syntax("question")

    private lazy val resultName: ResultName[PersistentQuestion] = questionAlias.resultName

    def apply(
      questionResultName: ResultName[PersistentQuestion] = resultName
    )(resultSet: WrappedResultSet): PersistentQuestion = {

      PersistentQuestion(
        questionId = resultSet.string(questionResultName.questionId),
        country = resultSet.string(questionResultName.country),
        language = resultSet.string(questionResultName.language),
        question = resultSet.string(questionResultName.question),
        createdAt = resultSet.zonedDateTime(questionResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(questionResultName.updatedAt),
        operationId = resultSet.stringOpt(questionResultName.operationId),
        themeId = resultSet.stringOpt(questionResultName.themeId)
      )
    }
  }
}