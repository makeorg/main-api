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

import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.operation.DefaultPersistentCurrentOperationServiceComponent.PersistentCurrentOperation
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.core.operation.{CurrentOperation, CurrentOperationId}
import org.make.core.question.QuestionId
import scalikejdbc._

import scala.concurrent.Future

trait PersistentCurrentOperationServiceComponent {
  def persistentCurrentOperationService: PersistentCurrentOperationService
}

trait PersistentCurrentOperationService {
  def getById(currentOperationId: CurrentOperationId): Future[Option[CurrentOperation]]
  def getAll: Future[Seq[CurrentOperation]]
  def persist(currentOperation: CurrentOperation): Future[CurrentOperation]
  def modify(currentOperation: CurrentOperation): Future[CurrentOperation]
  def delete(currentOperationId: CurrentOperationId): Future[Unit]
}

trait DefaultPersistentCurrentOperationServiceComponent extends PersistentCurrentOperationServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentCurrentOperationService: DefaultPersistentCurrentOperationService =
    new DefaultPersistentCurrentOperationService

  class DefaultPersistentCurrentOperationService extends PersistentCurrentOperationService with ShortenedNames {

    private val currentOperationAlias = PersistentCurrentOperation.currentOperationAlias

    private val column = PersistentCurrentOperation.column

    override def getById(currentOperationId: CurrentOperationId): Future[Option[CurrentOperation]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          selectFrom(PersistentCurrentOperation.as(currentOperationAlias))
            .where(sqls.eq(column.id, currentOperationId.value))
        }.map(PersistentCurrentOperation.apply()).single.apply()
      }).map(_.map(_.toCurrentOperation))
    }

    override def getAll: Future[Seq[CurrentOperation]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          selectFrom(PersistentCurrentOperation.as(currentOperationAlias))
        }.map(PersistentCurrentOperation.apply()).list().apply()
      }).map(_.map(_.toCurrentOperation))
    }

    override def persist(currentOperation: CurrentOperation): Future[CurrentOperation] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentCurrentOperation)
            .namedValues(
              column.id -> currentOperation.currentOperationId.value,
              column.questionId -> currentOperation.questionId.value,
              column.description -> currentOperation.description,
              column.label -> currentOperation.label,
              column.picture -> currentOperation.picture,
              column.altPicture -> currentOperation.altPicture,
              column.linkLabel -> currentOperation.linkLabel,
              column.internalLink -> currentOperation.internalLink,
              column.externalLink -> currentOperation.externalLink
            )
        }.execute().apply()
      }).map(_ => currentOperation)
    }

    override def modify(currentOperation: CurrentOperation): Future[CurrentOperation] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentCurrentOperation)
            .set(
              column.questionId -> currentOperation.questionId.value,
              column.description -> currentOperation.description,
              column.label -> currentOperation.label,
              column.picture -> currentOperation.picture,
              column.altPicture -> currentOperation.altPicture,
              column.linkLabel -> currentOperation.linkLabel,
              column.internalLink -> currentOperation.internalLink,
              column.externalLink -> currentOperation.externalLink
            )
            .where(sqls.eq(column.id, currentOperation.currentOperationId.value))
        }.execute().apply()
      }).map(_ => currentOperation)
    }

    override def delete(currentOperationId: CurrentOperationId): Future[Unit] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          deleteFrom(PersistentCurrentOperation).where(sqls.eq(column.id, currentOperationId.value))
        }.execute().apply()
      }).map(_ => ())
    }

  }
}

object DefaultPersistentCurrentOperationServiceComponent {
  case class PersistentCurrentOperation(
    id: String,
    questionId: String,
    description: String,
    label: String,
    picture: String,
    altPicture: String,
    linkLabel: String,
    internalLink: Option[String],
    externalLink: Option[String]
  ) {
    def toCurrentOperation: CurrentOperation = {
      CurrentOperation(
        currentOperationId = CurrentOperationId(id),
        questionId = QuestionId(questionId),
        description = description,
        label = label,
        picture = picture,
        altPicture = altPicture,
        linkLabel = linkLabel,
        internalLink = internalLink,
        externalLink = externalLink
      )
    }
  }

  object PersistentCurrentOperation extends SQLSyntaxSupport[PersistentCurrentOperation] with ShortenedNames {

    override val columnNames: Seq[String] =
      Seq(
        "id",
        "question_id",
        "description",
        "label",
        "picture",
        "alt_picture",
        "link_label",
        "internal_link",
        "external_link"
      )

    override val tableName: String = "current_operation"

    lazy val currentOperationAlias: SyntaxProvider[PersistentCurrentOperation] = syntax("current_operation")

    def apply(
      currentOperationResultName: ResultName[PersistentCurrentOperation] = currentOperationAlias.resultName
    )(resultSet: WrappedResultSet): PersistentCurrentOperation = {
      PersistentCurrentOperation.apply(
        id = resultSet.string(currentOperationResultName.id),
        questionId = resultSet.string(currentOperationResultName.questionId),
        description = resultSet.string(currentOperationResultName.description),
        label = resultSet.string(currentOperationResultName.label),
        picture = resultSet.string(currentOperationResultName.picture),
        altPicture = resultSet.string(currentOperationResultName.altPicture),
        linkLabel = resultSet.string(currentOperationResultName.linkLabel),
        internalLink = resultSet.stringOpt(currentOperationResultName.internalLink),
        externalLink = resultSet.stringOpt(currentOperationResultName.externalLink)
      )
    }

  }

}
