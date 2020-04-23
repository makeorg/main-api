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
import org.make.api.technical.PersistentServiceUtils.sortOrderQuery
import org.make.api.technical.ShortenedNames
import org.make.core.DateHelper
import org.make.core.operation.OperationId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import scalikejdbc.{ResultName, WrappedResultSet, _}

import scala.concurrent.Future

trait PersistentQuestionServiceComponent {
  def persistentQuestionService: PersistentQuestionService
}

trait PersistentQuestionService {
  def count(request: SearchQuestionRequest): Future[Int]
  def find(request: SearchQuestionRequest): Future[Seq[Question]]
  def getById(questionId: QuestionId): Future[Option[Question]]
  def getByIds(questionIds: Seq[QuestionId]): Future[Seq[Question]]
  def getByQuestionIdValueOrSlug(questionIdValueOrSlug: String): Future[Option[Question]]
  def persist(question: Question): Future[Question]
  def modify(question: Question): Future[Question]
  def delete(question: QuestionId): Future[Unit]
}

trait DefaultPersistentQuestionServiceComponent extends PersistentQuestionServiceComponent {
  this: MakeDBExecutionContextComponent =>

  lazy val persistentQuestionService: PersistentQuestionService = new DefaultPersistentQuestionService

  class DefaultPersistentQuestionService extends PersistentQuestionService with ShortenedNames with StrictLogging {

    private val column = PersistentQuestion.column
    private val questionAlias = PersistentQuestion.questionAlias

    override def find(request: SearchQuestionRequest): Future[Seq[Question]] = {

      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          val query: scalikejdbc.ConditionSQLBuilder[WrappedResultSet] = select
            .from(PersistentQuestion.as(questionAlias))
            .where(
              sqls.toAndConditionOpt(
                request.country.map(country   => sqls.eq(questionAlias.country, country.value)),
                request.language.map(language => sqls.eq(questionAlias.language, language.value)),
                request.maybeOperationIds
                  .map(operationIds                      => sqls.in(questionAlias.operationId, operationIds.map(_.value))),
                request.maybeSlug.map(slug               => sqls.eq(questionAlias.slug, slug)),
                request.maybeQuestionIds.map(questionIds => sqls.in(questionAlias.questionId, questionIds.map(_.value)))
              )
            )

          sortOrderQuery(
            start = request.skip.getOrElse(0),
            end = request.limit,
            order = request.order,
            sort = request.sort,
            query = query,
            columns = PersistentQuestion.columnNames,
            alias = questionAlias,
            defaultSort = questionAlias.question
          )
        }.map(PersistentQuestion.apply()).list().apply()
      }).map(_.map(_.toQuestion))

    }

    override def count(request: SearchQuestionRequest): Future[Int] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(sqls.count)
            .from(PersistentQuestion.as(questionAlias))
            .where(
              sqls.toAndConditionOpt(
                request.country.map(country   => sqls.eq(questionAlias.country, country.value)),
                request.language.map(language => sqls.eq(questionAlias.language, language.value)),
                request.maybeOperationIds
                  .map(operationIds                      => sqls.in(questionAlias.operationId, operationIds.map(_.value))),
                request.maybeQuestionIds.map(questionIds => sqls.in(questionAlias.questionId, questionIds.map(_.value)))
              )
            )
        }.map(_.int(1)).single.apply().getOrElse(0)
      })
    }

    override def getById(questionId: QuestionId): Future[Option[Question]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentQuestion.as(questionAlias))
            .where(sqls.eq(questionAlias.questionId, questionId.value))
        }.map(PersistentQuestion.apply()).single.apply()
      }).map(_.map(_.toQuestion))
    }

    override def getByQuestionIdValueOrSlug(questionIdValueOrSlug: String): Future[Option[Question]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentQuestion.as(questionAlias))
            .where(
              sqls
                .eq(questionAlias.slug, questionIdValueOrSlug)
                .or(sqls.eq(questionAlias.questionId, questionIdValueOrSlug))
            )
        }.map(PersistentQuestion.apply()).single.apply()
      }).map(_.map(_.toQuestion))
    }

    override def getByIds(questionIds: Seq[QuestionId]): Future[Seq[Question]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentQuestion.as(questionAlias))
            .where(sqls.in(questionAlias.questionId, questionIds.map(_.value)))
        }.map(PersistentQuestion.apply()).list.apply()
      }).map(_.map(_.toQuestion))
    }

    override def persist(question: Question): Future[Question] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          val now = DateHelper.now()
          insert
            .into(PersistentQuestion)
            .namedValues(
              column.questionId -> question.questionId.value,
              column.slug -> question.slug,
              column.createdAt -> now,
              column.updatedAt -> now,
              column.question -> question.question,
              column.shortTitle -> question.shortTitle,
              column.country -> question.country.value,
              column.language -> question.language.value,
              column.operationId -> question.operationId.map(_.value)
            )
        }.execute().apply()
      }).map(_ => question)
    }

    override def modify(question: Question): Future[Question] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          val now = DateHelper.now()
          update(PersistentQuestion)
            .set(
              PersistentQuestion.column.country -> question.country.value,
              PersistentQuestion.column.language -> question.language.value,
              PersistentQuestion.column.question -> question.question,
              PersistentQuestion.column.slug -> question.slug,
              PersistentQuestion.column.shortTitle -> question.shortTitle,
              PersistentQuestion.column.operationId -> question.operationId.map(_.value),
              PersistentQuestion.column.updatedAt -> now
            )
            .where(sqls.eq(PersistentQuestion.column.questionId, question.questionId.value))
        }.execute().apply()
      }).map(_ => question)
    }

    override def delete(questionId: QuestionId): Future[Unit] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          deleteFrom(PersistentQuestion)
            .where(sqls.eq(PersistentQuestion.column.questionId, questionId.value))
        }.execute().apply()
      }).map(_ => ())
    }
  }
}

object DefaultPersistentQuestionServiceComponent {

  case class PersistentQuestion(questionId: String,
                                country: String,
                                language: String,
                                question: String,
                                shortTitle: Option[String],
                                slug: String,
                                createdAt: ZonedDateTime,
                                updatedAt: ZonedDateTime,
                                operationId: Option[String]) {

    def toQuestion: Question = {
      Question(
        questionId = QuestionId(this.questionId),
        slug = this.slug,
        country = Country(this.country),
        language = Language(this.language),
        question = this.question,
        shortTitle = this.shortTitle,
        operationId = this.operationId.map(OperationId(_))
      )
    }
  }

  object PersistentQuestion extends SQLSyntaxSupport[PersistentQuestion] with ShortenedNames with StrictLogging {

    override val columnNames: Seq[String] =
      Seq(
        "question_id",
        "country",
        "language",
        "question",
        "created_at",
        "updated_at",
        "operation_id",
        "slug",
        "short_title"
      )

    override val tableName: String = "question"

    lazy val questionAlias: SyntaxProvider[PersistentQuestion] = syntax("question")

    private lazy val resultName: ResultName[PersistentQuestion] = questionAlias.resultName

    def apply(
      questionResultName: ResultName[PersistentQuestion] = resultName
    )(resultSet: WrappedResultSet): PersistentQuestion = {

      PersistentQuestion(
        questionId = resultSet.string(questionResultName.questionId),
        slug = resultSet.string(questionResultName.slug),
        country = resultSet.string(questionResultName.country),
        language = resultSet.string(questionResultName.language),
        question = resultSet.string(questionResultName.question),
        shortTitle = resultSet.stringOpt(questionResultName.shortTitle),
        createdAt = resultSet.zonedDateTime(questionResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(questionResultName.updatedAt),
        operationId = resultSet.stringOpt(questionResultName.operationId)
      )
    }
  }
}
