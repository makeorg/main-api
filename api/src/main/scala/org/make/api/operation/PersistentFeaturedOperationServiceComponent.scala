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
import org.make.api.technical.ShortenedNames
import org.make.api.operation.DefaultPersistentFeaturedOperationServiceComponent.PersistentFeaturedOperation
import org.make.api.technical.DatabaseTransactions._
import org.make.core.operation.{FeaturedOperation, FeaturedOperationId}
import org.make.core.question.QuestionId
import scalikejdbc._

import scala.concurrent.Future

trait PersistentFeaturedOperationServiceComponent {
  def persistentFeaturedOperationService: PersistentFeaturedOperationService
}

trait PersistentFeaturedOperationService {
  def getById(featuredOperationId: FeaturedOperationId): Future[Option[FeaturedOperation]]
  def getAll: Future[Seq[FeaturedOperation]]
  def persist(featuredOperation: FeaturedOperation): Future[FeaturedOperation]
  def modify(featuredOperation: FeaturedOperation): Future[FeaturedOperation]
  def delete(featuredOperationId: FeaturedOperationId): Future[Unit]
}

trait DefaultPersistentFeaturedOperationServiceComponent extends PersistentFeaturedOperationServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentFeaturedOperationService: DefaultPersistentFeaturedOperationService =
    new DefaultPersistentFeaturedOperationService

  class DefaultPersistentFeaturedOperationService extends PersistentFeaturedOperationService with ShortenedNames {

    private val featuredOperationAlias = PersistentFeaturedOperation.featuredOperationAlias

    private val column = PersistentFeaturedOperation.column

    override def getById(featuredOperationId: FeaturedOperationId): Future[Option[FeaturedOperation]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB(Symbol("READ")).retryableTx { implicit session =>
        withSQL {
          selectFrom(PersistentFeaturedOperation.as(featuredOperationAlias))
            .where(sqls.eq(column.id, featuredOperationId.value))
        }.map(PersistentFeaturedOperation.apply()).single.apply()
      }).map(_.map(_.toFeaturedOperation))
    }

    override def getAll: Future[Seq[FeaturedOperation]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB(Symbol("READ")).retryableTx { implicit session =>
        withSQL {
          selectFrom(PersistentFeaturedOperation.as(featuredOperationAlias))
        }.map(PersistentFeaturedOperation.apply()).list().apply()
      }).map(_.map(_.toFeaturedOperation))
    }

    override def persist(featuredOperation: FeaturedOperation): Future[FeaturedOperation] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB(Symbol("WRITE")).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentFeaturedOperation)
            .namedValues(
              column.id -> featuredOperation.featuredOperationId.value,
              column.questionId -> featuredOperation.questionId.map(_.value),
              column.title -> featuredOperation.title,
              column.description -> featuredOperation.description,
              column.landscapePicture -> featuredOperation.landscapePicture,
              column.portraitPicture -> featuredOperation.portraitPicture,
              column.altPicture -> featuredOperation.altPicture,
              column.label -> featuredOperation.label,
              column.buttonLabel -> featuredOperation.buttonLabel,
              column.internalLink -> featuredOperation.internalLink,
              column.externalLink -> featuredOperation.externalLink,
              column.slot -> featuredOperation.slot
            )
        }.execute().apply()
      }).map(_ => featuredOperation)
    }

    override def modify(featuredOperation: FeaturedOperation): Future[FeaturedOperation] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB(Symbol("WRITE")).retryableTx { implicit session =>
        withSQL {
          update(PersistentFeaturedOperation)
            .set(
              column.questionId -> featuredOperation.questionId.map(_.value),
              column.title -> featuredOperation.title,
              column.description -> featuredOperation.description,
              column.landscapePicture -> featuredOperation.landscapePicture,
              column.portraitPicture -> featuredOperation.portraitPicture,
              column.altPicture -> featuredOperation.altPicture,
              column.label -> featuredOperation.label,
              column.buttonLabel -> featuredOperation.buttonLabel,
              column.internalLink -> featuredOperation.internalLink,
              column.externalLink -> featuredOperation.externalLink,
              column.slot -> featuredOperation.slot
            )
            .where(sqls.eq(column.id, featuredOperation.featuredOperationId.value))
        }.execute().apply()
      }).map(_ => featuredOperation)
    }

    override def delete(featuredOperationId: FeaturedOperationId): Future[Unit] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB(Symbol("WRITE")).retryableTx { implicit session =>
        withSQL {
          deleteFrom(PersistentFeaturedOperation).where(sqls.eq(column.id, featuredOperationId.value))
        }.execute().apply()
      }).map(_ => ())
    }

  }
}

object DefaultPersistentFeaturedOperationServiceComponent {
  case class PersistentFeaturedOperation(id: String,
                                         questionId: Option[String],
                                         title: String,
                                         description: Option[String],
                                         landscapePicture: String,
                                         portraitPicture: String,
                                         altPicture: String,
                                         label: String,
                                         buttonLabel: String,
                                         internalLink: Option[String],
                                         externalLink: Option[String],
                                         slot: Int) {
    def toFeaturedOperation: FeaturedOperation = {
      FeaturedOperation(
        featuredOperationId = FeaturedOperationId(id),
        questionId = questionId.map(QuestionId(_)),
        title = title,
        description = description,
        landscapePicture = landscapePicture,
        portraitPicture = portraitPicture,
        altPicture = altPicture,
        label = label,
        buttonLabel = buttonLabel,
        internalLink = internalLink,
        externalLink = externalLink,
        slot = slot
      )
    }
  }

  object PersistentFeaturedOperation extends SQLSyntaxSupport[PersistentFeaturedOperation] with ShortenedNames {

    override val columnNames: Seq[String] = Seq(
      "id",
      "question_id",
      "title",
      "description",
      "landscape_picture",
      "portrait_picture",
      "alt_picture",
      "label",
      "button_label",
      "internal_link",
      "external_link",
      "slot"
    )

    override val tableName: String = "featured_operation"

    lazy val featuredOperationAlias: SyntaxProvider[PersistentFeaturedOperation] = syntax("featured_operation")

    def apply(
      featuredOperationResultName: ResultName[PersistentFeaturedOperation] = featuredOperationAlias.resultName
    )(resultSet: WrappedResultSet): PersistentFeaturedOperation = {
      PersistentFeaturedOperation.apply(
        id = resultSet.string(featuredOperationResultName.id),
        questionId = resultSet.stringOpt(featuredOperationResultName.questionId),
        title = resultSet.string(featuredOperationResultName.title),
        description = resultSet.stringOpt(featuredOperationResultName.description),
        landscapePicture = resultSet.string(featuredOperationResultName.landscapePicture),
        portraitPicture = resultSet.string(featuredOperationResultName.portraitPicture),
        altPicture = resultSet.string(featuredOperationResultName.altPicture),
        label = resultSet.string(featuredOperationResultName.label),
        buttonLabel = resultSet.string(featuredOperationResultName.buttonLabel),
        internalLink = resultSet.stringOpt(featuredOperationResultName.internalLink),
        externalLink = resultSet.stringOpt(featuredOperationResultName.externalLink),
        slot = resultSet.int(featuredOperationResultName.slot)
      )
    }

  }

}
