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

package org.make.api.tagtype

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.tagtype.DefaultPersistentTagTypeServiceComponent.PersistentTagType
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.core.DateHelper
import org.make.core.tag.{TagType, TagTypeDisplay, TagTypeId}
import scalikejdbc._

import scala.concurrent.Future
import scala.util.Success

trait PersistentTagTypeServiceComponent {
  def persistentTagTypeService: PersistentTagTypeService
}

trait PersistentTagTypeService {
  def get(tagTypeId: TagTypeId): Future[Option[TagType]]
  def findAll(): Future[Seq[TagType]]
  def findAllFromIds(tagTypesIds: Seq[TagTypeId]): Future[Seq[TagType]]
  def persist(tagType: TagType): Future[TagType]
  def update(tagType: TagType): Future[Option[TagType]]
  def remove(tagTypeId: TagTypeId): Future[Int]
}

trait DefaultPersistentTagTypeServiceComponent extends PersistentTagTypeServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentTagTypeService: PersistentTagTypeService = new PersistentTagTypeService
  with ShortenedNames with StrictLogging {

    private val tagTypeAlias = PersistentTagType.tagTypeAlias
    private val column = PersistentTagType.column

    override def get(tagTypeId: TagTypeId): Future[Option[TagType]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTagType = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTagType.as(tagTypeAlias))
            .where(sqls.eq(tagTypeAlias.id, tagTypeId.value))
        }.map(PersistentTagType.apply()).single.apply
      })

      futurePersistentTagType.map(_.map(_.toTagType))
    }

    override def findAll(): Future[Seq[TagType]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTagTypes: Future[List[PersistentTagType]] = Future(NamedDB('READ).retryableTx {
        implicit session =>
          withSQL {
            select
              .from(PersistentTagType.as(tagTypeAlias))
              .orderBy(tagTypeAlias.weightType)
          }.map(PersistentTagType.apply()).list.apply
      })

      futurePersistentTagTypes.map(_.map(_.toTagType))
    }

    override def findAllFromIds(tagTypesIds: Seq[TagTypeId]): Future[Seq[TagType]] = {
      implicit val context: EC = readExecutionContext
      val uniqueTagTypesIds: Seq[String] = tagTypesIds.distinct.map(_.value)
      val futurePersistentTagTypes: Future[List[PersistentTagType]] = Future(NamedDB('READ).retryableTx {
        implicit session =>
          withSQL {
            select
              .from(PersistentTagType.as(tagTypeAlias))
              .where(sqls.in(tagTypeAlias.id, uniqueTagTypesIds))
              .orderBy(tagTypeAlias.weightType)
          }.map(PersistentTagType.apply()).list.apply
      })

      futurePersistentTagTypes.map(_.map(_.toTagType))
    }

    override def persist(tagType: TagType): Future[TagType] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentTagType)
            .namedValues(
              column.id -> tagType.tagTypeId.value,
              column.label -> tagType.label,
              column.display -> tagType.display.shortName,
              column.weightType -> tagType.weight,
              column.createdAt -> DateHelper.now,
              column.updatedAt -> DateHelper.now
            )
        }.execute().apply()
      }).map(_ => tagType)
    }

    override def update(tagType: TagType): Future[Option[TagType]] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          scalikejdbc
            .update(PersistentTagType)
            .set(
              column.label -> tagType.label,
              column.display -> tagType.display.shortName,
              column.weightType -> tagType.weight,
              column.updatedAt -> DateHelper.now
            )
            .where(
              sqls
                .eq(column.id, tagType.tagTypeId.value)
            )
        }.executeUpdate().apply()
      }).map {
        case 1 => Some(tagType)
        case 0 =>
          logger.error(s"TagType '${tagType.tagTypeId.value}' not found")
          None
      }
    }

    override def remove(tagTypeId: TagTypeId): Future[Int] = {
      implicit val context: EC = writeExecutionContext
      val result: Future[Int] = Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          delete
            .from(PersistentTagType.as(tagTypeAlias))
            .where(sqls.eq(tagTypeAlias.id, tagTypeId.value))
        }.update.apply()
      })

      result.onComplete {
        case Success(0) => logger.info(s"Expected 1 row to be removed and get 0 rows with tagType ${tagTypeId.value}")
        case Success(rows) =>
          if (rows != 1) {
            logger.warn(s"Expected 1 row to be removed and get $rows rows with tagType ${tagTypeId.value}")
          } else {
            logger.debug(s"Remove of tagType ${tagTypeId.value} success")
          }
        case _ =>
      }

      result
    }

  }
}

object DefaultPersistentTagTypeServiceComponent {

  case class PersistentTagType(id: String,
                               label: String,
                               display: TagTypeDisplay,
                               weightType: Int,
                               createdAt: ZonedDateTime,
                               updatedAt: ZonedDateTime) {
    def toTagType: TagType =
      TagType(tagTypeId = TagTypeId(id), label = label, display = display, weight = weightType)
  }

  object PersistentTagType extends SQLSyntaxSupport[PersistentTagType] with ShortenedNames with StrictLogging {

    override val columnNames: Seq[String] = Seq("id", "label", "display", "weight_type", "created_at", "updated_at")

    override val tableName: String = "tag_type"

    lazy val tagTypeAlias: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentTagType], PersistentTagType] = syntax("tt")

    def apply(
      tagTypeResultName: ResultName[PersistentTagType] = tagTypeAlias.resultName
    )(resultSet: WrappedResultSet): PersistentTagType = {
      PersistentTagType.apply(
        id = resultSet.string(tagTypeResultName.id),
        label = resultSet.string(tagTypeResultName.label),
        display = TagTypeDisplay.matchTagTypeDisplayOrDefault(resultSet.string(tagTypeResultName.display)),
        weightType = resultSet.int(tagTypeResultName.weightType),
        createdAt = resultSet.zonedDateTime(tagTypeResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(tagTypeResultName.updatedAt)
      )
    }
  }

}
