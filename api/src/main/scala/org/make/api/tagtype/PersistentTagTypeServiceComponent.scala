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
              column.createdAt -> DateHelper.now,
              column.updatedAt -> DateHelper.now
            )
        }.execute().apply()
      }).map(_ => tagType)
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
                               createdAt: ZonedDateTime,
                               updatedAt: ZonedDateTime) {
    def toTagType: TagType =
      TagType(tagTypeId = TagTypeId(id), label = label, display = display)
  }

  object PersistentTagType extends SQLSyntaxSupport[PersistentTagType] with ShortenedNames with StrictLogging {

    override val columnNames: Seq[String] = Seq("id", "label", "display", "created_at", "updated_at")

    override val tableName: String = "tag_type"

    lazy val tagTypeAlias: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentTagType], PersistentTagType] = syntax("tt")

    def apply(
      tagTypeResultName: ResultName[PersistentTagType] = tagTypeAlias.resultName
    )(resultSet: WrappedResultSet): PersistentTagType = {
      PersistentTagType.apply(
        id = resultSet.string(tagTypeResultName.id),
        label = resultSet.string(tagTypeResultName.label),
        display = TagTypeDisplay.matchTagTypeDisplayOrDefault(resultSet.string(tagTypeResultName.display)),
        createdAt = resultSet.zonedDateTime(tagTypeResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(tagTypeResultName.updatedAt)
      )
    }
  }

}