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

import cats.data.NonEmptyList
import grizzled.slf4j.Logging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.operation.DefaultPersistentOperationOfQuestionServiceComponent.PersistentOperationOfQuestion
import org.make.api.operation.DefaultPersistentOperationOfQuestionServiceComponent.PersistentOperationOfQuestion.FlatQuestionWithDetails
import org.make.api.operation.DefaultPersistentOperationServiceComponent.{
  PersistentOperation,
  PersistentOperationAction
}
import org.make.api.question.DefaultPersistentQuestionServiceComponent.PersistentQuestion
import org.make.api.tag.DefaultPersistentTagServiceComponent
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.PersistentServiceUtils.sortOrderQuery
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.api.technical.ScalikeSupport._
import org.make.core.{DateHelper, Order}
import org.make.core.operation._
import org.make.core.reference.Country
import org.make.core.user.UserId
import scalikejdbc._
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait DefaultPersistentOperationServiceComponent extends PersistentOperationServiceComponent {
  this: MakeDBExecutionContextComponent with DefaultPersistentTagServiceComponent =>

  override lazy val persistentOperationService: PersistentOperationService = new DefaultPersistentOperationService

  class DefaultPersistentOperationService extends PersistentOperationService with ShortenedNames with Logging {

    private val operationAlias = PersistentOperation.alias
    private val operationOfQuestionAlias = PersistentOperationOfQuestion.alias
    private val operationActionAlias = PersistentOperationAction.operationActionAlias
    private val questionAlias = PersistentQuestion.alias

    private val column = PersistentOperation.column
    private val operationActionColumn = PersistentOperationAction.column
    private def selectOperation[T]: scalikejdbc.SelectSQLBuilder[T] =
      select
        .from(PersistentOperation.as(operationAlias))
        .leftJoin(PersistentOperationOfQuestion.as(operationOfQuestionAlias))
        .on(operationAlias.uuid, operationOfQuestionAlias.operationId)
        .leftJoin(PersistentOperationAction.as(operationActionAlias))
        .on(operationAlias.uuid, operationActionAlias.operationUuid)
        .leftJoin(PersistentQuestion.as(questionAlias))
        .on(questionAlias.questionId, operationOfQuestionAlias.questionId)
    private def operationWhereOpts(
      slug: Option[String],
      operationKinds: Option[Seq[OperationKind]],
      country: Option[Country],
      openAt: Option[LocalDate]
    ): Option[SQLSyntax] =
      sqls.toAndConditionOpt(
        slug.map(slug              => sqls.like(operationAlias.slug, s"%$slug%")),
        operationKinds.map(opKinds => sqls.in(operationAlias.operationKind, opKinds)),
        country.map(country        => sqls.like(questionAlias.countries, s"%${country.value}%")),
        openAt.map(
          openAt =>
            sqls
              .le(operationOfQuestionAlias.startDate, openAt)
              .or(sqls.isNull(operationOfQuestionAlias.startDate))
        ),
        openAt.map(
          openAt =>
            sqls
              .ge(operationOfQuestionAlias.endDate, openAt)
              .or(sqls.isNull(operationOfQuestionAlias.endDate))
        )
      )

    override def find(
      slug: Option[String] = None,
      country: Option[Country] = None,
      openAt: Option[LocalDate] = None
    ): Future[Seq[Operation]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentOperations: Future[List[PersistentOperation]] = Future(NamedDB("READ").retryableTx {
        implicit session =>
          withSQL[PersistentOperation] {
            selectOperation.where(operationWhereOpts(slug, None, country, openAt))
          }.one(PersistentOperation.apply())
            .toManies(
              PersistentOperationAction.opt(operationActionAlias),
              PersistentOperationOfQuestion.withQuestion(questionAlias.resultName, operationOfQuestionAlias.resultName)
            )
            .map { (operation, actions, questions) =>
              operation.copy(operationActions = actions.toVector, questions = questions.toVector)
            }
            .list()
            .apply()
      })

      futurePersistentOperations.map(_.map(_.toOperation))
    }

    override def findSimple(
      start: Start,
      end: Option[End],
      sort: Option[String],
      order: Option[Order],
      slug: Option[String] = None,
      operationKinds: Option[Seq[OperationKind]]
    ): Future[Seq[SimpleOperation]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentOperations: Future[List[PersistentOperation]] = Future(NamedDB("READ").retryableTx {
        implicit session =>
          withSQL {
            val query: scalikejdbc.PagingSQLBuilder[PersistentOperation] =
              select
                .from(PersistentOperation.as(operationAlias))
                .where(operationWhereOpts(slug, operationKinds, None, None))
            sortOrderQuery(start, end, sort, order, query)
          }.map(PersistentOperation.apply()).list().apply()
      })

      futurePersistentOperations.map(_.map(_.toSimpleOperation))
    }

    override def persist(operation: SimpleOperation): Future[SimpleOperation] = {
      implicit val context: EC = writeExecutionContext
      val nowDate: ZonedDateTime = DateHelper.now()
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentOperation)
            .namedValues(
              column.uuid -> operation.operationId.value,
              column.status -> operation.status,
              column.slug -> operation.slug,
              column.operationKind -> operation.operationKind,
              column.createdAt -> nowDate,
              column.updatedAt -> nowDate
            )
        }.execute().apply()
      }).map(_ => operation)
    }

    override def getById(operationId: OperationId): Future[Option[Operation]] = {
      implicit val context: EC = readExecutionContext
      val futureMaybePersistentOperation: Future[Option[PersistentOperation]] = Future(NamedDB("READ").retryableTx {
        implicit session =>
          withSQL[PersistentOperation] {
            selectOperation
              .where(sqls.eq(operationAlias.uuid, operationId.value))
          }.one(PersistentOperation.apply())
            .toManies(
              PersistentOperationAction.opt(operationActionAlias),
              PersistentOperationOfQuestion.withQuestion(questionAlias.resultName, operationOfQuestionAlias.resultName)
            )
            .map { (operation, actions, questions) =>
              operation.copy(operationActions = actions.toVector, questions = questions.toVector)
            }
            .single()
            .apply()
      })

      futureMaybePersistentOperation.map(_.map(_.toOperation))
    }

    override def getSimpleById(operationId: OperationId): Future[Option[SimpleOperation]] = {
      implicit val context: EC = readExecutionContext
      val futureMaybePersistentOperation: Future[Option[PersistentOperation]] = Future(NamedDB("READ").retryableTx {
        implicit session =>
          withSQL[PersistentOperation] {
            select
              .from(PersistentOperation.as(operationAlias))
              .where(sqls.eq(operationAlias.uuid, operationId.value))
          }.map(PersistentOperation.apply()).single().apply()
      })

      futureMaybePersistentOperation.map(_.map(_.toSimpleOperation))
    }

    override def getBySlug(slug: String): Future[Option[Operation]] = {
      implicit val context: EC = readExecutionContext
      val futureMaybePersistentOperation: Future[Option[PersistentOperation]] = Future(NamedDB("READ").retryableTx {
        implicit session =>
          withSQL[PersistentOperation] {
            selectOperation
              .where(sqls.eq(operationAlias.slug, slug))
          }.one(PersistentOperation.apply())
            .toManies(
              PersistentOperationAction.opt(operationActionAlias),
              PersistentOperationOfQuestion.withQuestion(questionAlias.resultName, operationOfQuestionAlias.resultName)
            )
            .map { (operation, actions, questions) =>
              operation.copy(operationActions = actions.toVector, questions = questions.toVector)
            }
            .single()
            .apply()
      })

      futureMaybePersistentOperation.map(_.map(_.toOperation))
    }

    override def modify(operation: SimpleOperation): Future[SimpleOperation] = {
      implicit val ctx: EC = writeExecutionContext
      val nowDate: ZonedDateTime = DateHelper.now()
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentOperation)
            .set(
              column.status -> operation.status,
              column.slug -> operation.slug,
              column.operationKind -> operation.operationKind,
              column.updatedAt -> nowDate
            )
            .where(
              sqls
                .eq(column.uuid, operation.operationId.value)
            )
        }.executeUpdate().apply()
      }).map(_ => operation)
    }

    override def addActionToOperation(action: OperationAction, operationId: OperationId): Future[Boolean] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentOperationAction)
            .namedValues(
              operationActionColumn.operationUuid -> operationId.value,
              operationActionColumn.makeUserUuid -> action.makeUserId.value,
              operationActionColumn.actionDate -> action.date,
              operationActionColumn.actionType -> action.actionType,
              operationActionColumn.arguments -> action.arguments.toJson.compactPrint
            )
        }.execute().apply()
      })
    }

    override def count(slug: Option[String] = None, operationKinds: Option[Seq[OperationKind]]): Future[Int] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL[PersistentOperation] {
          select(sqls.count)
            .from(PersistentOperation.as(operationAlias))
            .where(operationWhereOpts(slug, operationKinds, None, None))
        }.map(_.int(1)).single().apply().getOrElse(0)
      })
    }

  }
}

object DefaultPersistentOperationServiceComponent {

  final case class PersistentOperationAction(
    operationUuid: String,
    makeUserUuid: String,
    actionDate: ZonedDateTime,
    actionType: String,
    arguments: Option[String]
  )

  final case class PersistentOperation(
    uuid: String,
    questions: Seq[FlatQuestionWithDetails],
    operationActions: Seq[PersistentOperationAction],
    status: String,
    slug: String,
    operationKind: String,
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime
  ) {

    def toOperation: Operation =
      Operation(
        operationId = OperationId(uuid),
        createdAt = Some(createdAt),
        updatedAt = Some(updatedAt),
        status = OperationStatus.withValue(status),
        slug = slug,
        operationKind = OperationKind.withValue(operationKind),
        events = operationActions
          .map(
            action =>
              OperationAction(
                date = action.actionDate,
                makeUserId = UserId(action.makeUserUuid),
                actionType = action.actionType,
                arguments = action.arguments.getOrElse("{}").parseJson.convertTo[Map[String, String]]
              )
          )
          .toList,
        questions = questions.map(_.toQuestionAndDetails)
      )

    def toSimpleOperation: SimpleOperation =
      SimpleOperation(
        operationId = OperationId(uuid),
        status = OperationStatus.withValue(status),
        slug = slug,
        createdAt = Some(createdAt),
        updatedAt = Some(updatedAt),
        operationKind = OperationKind.withValue(operationKind)
      )
  }

  object PersistentOperationAction
      extends SQLSyntaxSupport[PersistentOperationAction]
      with ShortenedNames
      with Logging {

    override val columnNames: Seq[String] =
      Seq("operation_uuid", "make_user_uuid", "action_date", "action_type", "arguments")

    override val tableName: String = "operation_action"

    lazy val operationActionAlias: SyntaxProvider[PersistentOperationAction] = syntax("oa")

    def opt(
      operationAction: SyntaxProvider[PersistentOperationAction]
    )(resultSet: WrappedResultSet): Option[PersistentOperationAction] =
      resultSet
        .stringOpt(operationAction.resultName.operationUuid)
        .map(_ => PersistentOperationAction(operationAction.resultName)(resultSet))

    def apply(
      operationActionResultName: ResultName[PersistentOperationAction] = operationActionAlias.resultName
    )(resultSet: WrappedResultSet): PersistentOperationAction = {
      PersistentOperationAction.apply(
        operationUuid = resultSet.string(operationActionResultName.operationUuid),
        makeUserUuid = resultSet.string(operationActionResultName.makeUserUuid),
        actionDate = resultSet.zonedDateTime(operationActionResultName.actionDate),
        actionType = resultSet.string(operationActionResultName.actionType),
        arguments = resultSet.stringOpt(operationActionResultName.arguments)
      )
    }
  }

  implicit object PersistentOperation
      extends PersistentCompanion[PersistentOperation, Operation]
      with ShortenedNames
      with Logging {

    override val columnNames: Seq[String] =
      Seq("uuid", "status", "slug", "operation_kind", "created_at", "updated_at")

    override val tableName: String = "operation"

    override lazy val alias: SyntaxProvider[PersistentOperation] = syntax("op")

    override lazy val defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.slug)

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def apply(
      operationResultName: ResultName[PersistentOperation] = alias.resultName
    )(resultSet: WrappedResultSet): PersistentOperation = {
      PersistentOperation.apply(
        uuid = resultSet.string(operationResultName.uuid),
        status = resultSet.string(operationResultName.status),
        slug = resultSet.string(operationResultName.slug),
        operationKind = resultSet.string(operationResultName.operationKind),
        operationActions = Seq.empty,
        questions = Seq.empty,
        createdAt = resultSet.zonedDateTime(operationResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(operationResultName.updatedAt)
      )
    }
  }

}
