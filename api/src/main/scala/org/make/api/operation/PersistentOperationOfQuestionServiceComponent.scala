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
import java.time.{LocalDate, ZonedDateTime}

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.question.DefaultPersistentQuestionServiceComponent.PersistentQuestion
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.core.DateHelper
import org.make.core.operation.{OperationId, OperationOfQuestion, QuestionWithDetails}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import scalikejdbc._

import scala.concurrent.Future

trait PersistentOperationOfQuestionService {
  def search(questionIds: Option[Seq[QuestionId]],
             operationId: Option[OperationId],
             openAt: Option[LocalDate]): Future[Seq[OperationOfQuestion]]
  def persist(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion]
  def modify(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion]
  def getById(id: QuestionId): Future[Option[OperationOfQuestion]]
  def find(operationId: Option[OperationId] = None): Future[Seq[OperationOfQuestion]]
  def delete(questionId: QuestionId): Future[Unit]
}

trait PersistentOperationOfQuestionServiceComponent {
  def persistentOperationOfQuestionService: PersistentOperationOfQuestionService
}

trait DefaultPersistentOperationOfQuestionServiceComponent extends PersistentOperationOfQuestionServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentOperationOfQuestionService: PersistentOperationOfQuestionService =
    new PersistentOperationOfQuestionService with ShortenedNames with StrictLogging {

      override def search(questionIds: Option[Seq[QuestionId]],
                          operationId: Option[OperationId],
                          openAt: Option[LocalDate]): Future[scala.Seq[OperationOfQuestion]] = {
        implicit val context: EC = readExecutionContext
        Future(NamedDB('READ).retryableTx { implicit session =>
          withSQL[PersistentOperationOfQuestion] {
            select
              .from(PersistentOperationOfQuestion.as(PersistentOperationOfQuestion.alias))
              .where(
                sqls.toAndConditionOpt(
                  operationId
                    .map(operation => sqls.eq(PersistentOperationOfQuestion.column.operationId, operation.value)),
                  questionIds.map(
                    questionIds => sqls.in(PersistentOperationOfQuestion.column.questionId, questionIds.map(_.value))
                  ),
                  openAt.map(
                    openAt =>
                      sqls
                        .isNull(PersistentOperationOfQuestion.column.startDate)
                        .or(sqls.le(PersistentOperationOfQuestion.column.startDate, openAt))
                        .and(
                          sqls
                            .isNull(PersistentOperationOfQuestion.column.endDate)
                            .or(sqls.ge(PersistentOperationOfQuestion.column.endDate, openAt))
                      )
                  )
                )
              )
          }.map(PersistentOperationOfQuestion(PersistentOperationOfQuestion.alias.resultName)).list().apply()
        }).map(_.map(_.toOperationOfQuestion))
      }

      override def persist(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion] = {
        implicit val context: EC = writeExecutionContext
        Future(NamedDB('WRITE).retryableTx { implicit session =>
          withSQL {
            val now = DateHelper.now()
            insert
              .into(PersistentOperationOfQuestion)
              .namedValues(
                PersistentOperationOfQuestion.column.questionId -> operationOfQuestion.questionId.value,
                PersistentOperationOfQuestion.column.operationId -> operationOfQuestion.operationId.value,
                PersistentOperationOfQuestion.column.startDate -> operationOfQuestion.startDate,
                PersistentOperationOfQuestion.column.endDate -> operationOfQuestion.endDate,
                PersistentOperationOfQuestion.column.operationTitle -> operationOfQuestion.operationTitle,
                PersistentOperationOfQuestion.column.landingSequenceId -> operationOfQuestion.landingSequenceId.value,
                PersistentOperationOfQuestion.column.createdAt -> now,
                PersistentOperationOfQuestion.column.updatedAt -> now,
                PersistentOperationOfQuestion.column.canPropose -> operationOfQuestion.canPropose
              )
          }.execute().apply()
        }).map(_ => operationOfQuestion)
      }

      override def modify(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion] = {
        implicit val context: EC = writeExecutionContext
        Future(NamedDB('WRITE).retryableTx { implicit session =>
          withSQL {
            val now = DateHelper.now()
            update(PersistentOperationOfQuestion)
              .set(
                PersistentOperationOfQuestion.column.startDate -> operationOfQuestion.startDate,
                PersistentOperationOfQuestion.column.endDate -> operationOfQuestion.endDate,
                PersistentOperationOfQuestion.column.operationTitle -> operationOfQuestion.operationTitle,
                PersistentOperationOfQuestion.column.updatedAt -> now,
                PersistentOperationOfQuestion.column.canPropose -> operationOfQuestion.canPropose
              )
              .where(sqls.eq(PersistentOperationOfQuestion.column.questionId, operationOfQuestion.questionId.value))
          }.execute().apply()
        }).map(_ => operationOfQuestion)
      }

      override def getById(id: QuestionId): Future[Option[OperationOfQuestion]] = {
        implicit val context: EC = readExecutionContext
        Future(NamedDB('READ).retryableTx { implicit session =>
          withSQL[PersistentOperationOfQuestion] {
            select
              .from(PersistentOperationOfQuestion.as(PersistentOperationOfQuestion.alias))
              .where(sqls.eq(PersistentOperationOfQuestion.column.questionId, id.value))
          }.map(PersistentOperationOfQuestion(PersistentOperationOfQuestion.alias.resultName)(_)).single.apply()
        }).map(_.map(_.toOperationOfQuestion))
      }

      override def find(operationId: Option[OperationId]): Future[Seq[OperationOfQuestion]] = {
        implicit val context: EC = readExecutionContext
        Future(NamedDB('READ).retryableTx { implicit session =>
          withSQL[PersistentOperationOfQuestion] {
            select
              .from(PersistentOperationOfQuestion.as(PersistentOperationOfQuestion.alias))
              .where(
                sqls.toAndConditionOpt(
                  operationId
                    .map(operation => sqls.eq(PersistentOperationOfQuestion.column.operationId, operation.value))
                )
              )
          }.map(PersistentOperationOfQuestion(PersistentOperationOfQuestion.alias.resultName)).list().apply()
        }).map(_.map(_.toOperationOfQuestion))
      }

      override def delete(questionId: QuestionId): Future[Unit] = {
        implicit val context: EC = readExecutionContext
        Future(NamedDB('WRITE).retryableTx { implicit session =>
          withSQL {
            deleteFrom(PersistentOperationOfQuestion)
              .where(sqls.eq(PersistentOperationOfQuestion.column.questionId, questionId.value))
          }.execute().apply()
        }).map(_ => ())
      }
    }

}

final case class PersistentOperationOfQuestion(questionId: String,
                                               operationId: String,
                                               startDate: Option[LocalDate],
                                               endDate: Option[LocalDate],
                                               operationTitle: String,
                                               landingSequenceId: String,
                                               createdAt: ZonedDateTime,
                                               updatedAt: ZonedDateTime,
                                               canPropose: Boolean) {
  def toOperationOfQuestion: OperationOfQuestion = OperationOfQuestion(
    questionId = QuestionId(this.questionId),
    operationId = OperationId(this.operationId),
    startDate = this.startDate,
    endDate = this.endDate,
    operationTitle = this.operationTitle,
    landingSequenceId = SequenceId(this.landingSequenceId),
    canPropose = this.canPropose
  )
}

object PersistentOperationOfQuestion
    extends SQLSyntaxSupport[PersistentOperationOfQuestion]
    with ShortenedNames
    with StrictLogging {

  final case class FlatQuestionWithDetails(questionId: String,
                                           country: String,
                                           language: String,
                                           question: String,
                                           slug: String,
                                           operationId: String,
                                           startDate: Option[LocalDate],
                                           endDate: Option[LocalDate],
                                           operationTitle: String,
                                           landingSequenceId: String,
                                           canPropose: Boolean) {
    def toQuestionAndDetails: QuestionWithDetails = {
      QuestionWithDetails(
        question = Question(
          questionId = QuestionId(questionId),
          country = Country(country),
          language = Language(language),
          slug = slug,
          question = question,
          operationId = Some(OperationId(operationId)),
          themeId = None
        ),
        details = OperationOfQuestion(
          questionId = QuestionId(questionId),
          operationId = OperationId(operationId),
          startDate = startDate,
          endDate = endDate,
          operationTitle = operationTitle,
          landingSequenceId = SequenceId(landingSequenceId),
          canPropose = canPropose
        )
      )

    }
  }

  def withQuestion(
    questionAlias: ResultName[PersistentQuestion],
    operationOfQuestionAlias: ResultName[PersistentOperationOfQuestion]
  )(resultSet: WrappedResultSet): Option[FlatQuestionWithDetails] = {

    for {
      country           <- resultSet.stringOpt(questionAlias.country)
      language          <- resultSet.stringOpt(questionAlias.language)
      questionId        <- resultSet.stringOpt(operationOfQuestionAlias.questionId)
      questionSlug      <- resultSet.stringOpt(questionAlias.slug)
      operationId       <- resultSet.stringOpt(operationOfQuestionAlias.operationId)
      question          <- resultSet.stringOpt(questionAlias.question)
      landingSequenceId <- resultSet.stringOpt(operationOfQuestionAlias.landingSequenceId)
      operationTitle    <- resultSet.stringOpt(operationOfQuestionAlias.operationTitle)
    } yield
      FlatQuestionWithDetails(
        questionId = questionId,
        country = country,
        language = language,
        question = question,
        slug = questionSlug,
        operationId = operationId,
        startDate = resultSet.localDateOpt(operationOfQuestionAlias.startDate),
        endDate = resultSet.localDateOpt(operationOfQuestionAlias.endDate),
        operationTitle = operationTitle,
        landingSequenceId = landingSequenceId,
        canPropose = resultSet.boolean(operationOfQuestionAlias.canPropose)
      )
  }

  override val columnNames: Seq[String] =
    Seq(
      "question_id",
      "operation_id",
      "start_date",
      "end_date",
      "operation_title",
      "landing_sequence_id",
      "created_at",
      "updated_at",
      "can_propose"
    )

  override val tableName: String = "operation_of_question"

  lazy val alias: SyntaxProvider[PersistentOperationOfQuestion] = syntax("operationOfquestion")

  def apply(
    resultName: ResultName[PersistentOperationOfQuestion] = alias.resultName
  )(resultSet: WrappedResultSet): PersistentOperationOfQuestion = {
    PersistentOperationOfQuestion(
      questionId = resultSet.string(resultName.questionId),
      operationId = resultSet.string(resultName.operationId),
      startDate = resultSet.localDateOpt(resultName.startDate),
      endDate = resultSet.localDateOpt(resultName.endDate),
      operationTitle = resultSet.string(resultName.operationTitle),
      landingSequenceId = resultSet.string(resultName.landingSequenceId),
      createdAt = resultSet.zonedDateTime(resultName.createdAt),
      updatedAt = resultSet.zonedDateTime(resultName.updatedAt),
      canPropose = resultSet.boolean(resultName.canPropose)
    )
  }
}
