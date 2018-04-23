package org.make.api.tag

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.tag.DefaultPersistentTagServiceComponent.PersistentTag
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.core.DateHelper
import org.make.core.operation.OperationId
import org.make.core.reference.ThemeId
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import scalikejdbc._

import scala.concurrent.Future
import scala.util.Success

trait PersistentTagServiceComponent {
  def persistentTagService: PersistentTagService
}

trait PersistentTagService {
  def get(tagId: TagId): Future[Option[Tag]]
  def findAll(): Future[Seq[Tag]]
  def findAllFromIds(tagsIds: Seq[TagId]): Future[Seq[Tag]]
  def findByLabelLike(partialLabel: String): Future[Seq[Tag]]
  def findByOperationId(operationId: OperationId): Future[Seq[Tag]]
  def findByThemeId(themeId: ThemeId): Future[Seq[Tag]]
  def persist(tag: Tag): Future[Tag]
  def update(tag: Tag): Future[Option[Tag]]
  def remove(tagId: TagId): Future[Int]
}

trait DefaultPersistentTagServiceComponent extends PersistentTagServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentTagService: PersistentTagService = new PersistentTagService with ShortenedNames
  with StrictLogging {

    private val tagAlias = PersistentTag.tagAlias
    private val column = PersistentTag.column

    override def get(tagId: TagId): Future[Option[Tag]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTag = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTag.as(tagAlias))
            .where(sqls.eq(tagAlias.id, tagId.value))
        }.map(PersistentTag.apply()).single.apply
      })

      futurePersistentTag.map(_.map(_.toTag))
    }

    override def findAll(): Future[Seq[Tag]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTags: Future[List[PersistentTag]] = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTag.as(tagAlias))
        }.map(PersistentTag.apply()).list.apply
      })

      futurePersistentTags.map(_.map(_.toTag))
    }

    override def findAllFromIds(tagsIds: Seq[TagId]): Future[Seq[Tag]] = {
      implicit val context: EC = readExecutionContext
      val uniqueTagsIds: Seq[String] = tagsIds.distinct.map(_.value)
      val futurePersistentTags: Future[List[PersistentTag]] = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTag.as(tagAlias))
            .where(sqls.in(tagAlias.id, uniqueTagsIds))
        }.map(PersistentTag.apply()).list.apply
      })

      futurePersistentTags.map(_.map(_.toTag))
    }

    def findByLabelLike(partialLabel: String): Future[Seq[Tag]] = {
      implicit val context: EC = readExecutionContext
      val preparedPartialLabel: String = partialLabel.replace("%", "\\%")
      val futurePersistentTags: Future[List[PersistentTag]] = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTag.as(tagAlias))
            .where(sqls.like(tagAlias.label, s"%$preparedPartialLabel%"))
        }.map(PersistentTag.apply()).list.apply
      })

      futurePersistentTags.map(_.map(_.toTag))
    }

    def findByOperationId(operationId: OperationId): Future[Seq[Tag]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTags: Future[List[PersistentTag]] = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTag.as(tagAlias))
            .where(sqls.eq(tagAlias.operationId, operationId.value))
        }.map(PersistentTag.apply()).list.apply
      })

      futurePersistentTags.map(_.map(_.toTag))
    }

    def findByThemeId(themeId: ThemeId): Future[Seq[Tag]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTags: Future[List[PersistentTag]] = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTag.as(tagAlias))
            .where(sqls.eq(tagAlias.themeId, themeId.value))
        }.map(PersistentTag.apply()).list.apply
      })

      futurePersistentTags.map(_.map(_.toTag))
    }

    override def persist(tag: Tag): Future[Tag] = {
      implicit val context: EC = writeExecutionContext
      val nowDate: ZonedDateTime = DateHelper.now()
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentTag)
            .namedValues(
              column.id -> tag.tagId.value,
              column.label -> tag.label,
              column.display -> tag.display.shortName,
              column.tagTypeId -> tag.tagTypeId.value,
              column.operationId -> tag.operationId.map(_.value),
              column.themeId -> tag.themeId.map(_.value),
              column.weight -> tag.weight,
              column.country -> tag.country,
              column.language -> tag.language,
              column.createdAt -> nowDate,
              column.updatedAt -> nowDate
            )
        }.execute().apply()
      }).map(_ => tag)
    }

    override def update(tag: Tag): Future[Option[Tag]] = {
      implicit val ctx: EC = writeExecutionContext
      val nowDate: ZonedDateTime = DateHelper.now()
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          scalikejdbc
            .update(PersistentTag)
            .set(
              column.label -> tag.label,
              column.display -> tag.display.shortName,
              column.tagTypeId -> tag.tagTypeId.value,
              column.operationId -> tag.operationId.map(_.value),
              column.themeId -> tag.themeId.map(_.value),
              column.weight -> tag.weight,
              column.country -> tag.country,
              column.language -> tag.language,
              column.updatedAt -> nowDate
            )
            .where(
              sqls
                .eq(column.id, tag.tagId.value)
            )
        }.executeUpdate().apply()
      }).map {
        case 1 => Some(tag)
        case 0 =>
          logger.error(s"Tag '${tag.tagId.value}' not found")
          None
      }
    }

    override def remove(tagId: TagId): Future[Int] = {
      implicit val context: EC = writeExecutionContext
      val result: Future[Int] = Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          delete
            .from(PersistentTag.as(tagAlias))
            .where(sqls.eq(tagAlias.id, tagId.value))
        }.update.apply()
      })

      result.onComplete {
        case Success(0) => logger.info(s"Expected 1 row to be removed and get 0 rows with tag ${tagId.value}")
        case Success(rows) =>
          if (rows != 1) {
            logger.warn(s"Expected 1 row to be removed and get $rows rows with tag ${tagId.value}")
          } else {
            logger.debug(s"Remove of tag ${tagId.value} success")
          }
        case _ =>
      }

      result
    }

  }
}

object DefaultPersistentTagServiceComponent {

  case class PersistentTag(id: String,
                           label: String,
                           display: TagDisplay,
                           tagTypeId: String,
                           operationId: Option[String],
                           themeId: Option[String],
                           weight: Float,
                           country: String,
                           language: String,
                           createdAt: ZonedDateTime,
                           updatedAt: ZonedDateTime) {
    def toTag: Tag =
      Tag(
        tagId = TagId(id),
        label = label,
        display = display,
        tagTypeId = TagTypeId(tagTypeId),
        operationId = operationId.map(OperationId(_)),
        themeId = themeId.map(ThemeId(_)),
        weight = weight,
        country = country,
        language = language
      )
  }

  object PersistentTag extends SQLSyntaxSupport[PersistentTag] with ShortenedNames with StrictLogging {

    override val columnNames: Seq[String] = Seq(
      "id",
      "label",
      "display",
      "tag_type_id",
      "operation_id",
      "theme_id",
      "weight",
      "country",
      "language",
      "created_at",
      "updated_at"
    )

    override val tableName: String = "tag"

    lazy val tagAlias: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentTag], PersistentTag] = syntax("ta")

    def apply(
      tagResultName: ResultName[PersistentTag] = tagAlias.resultName
    )(resultSet: WrappedResultSet): PersistentTag = {
      PersistentTag.apply(
        id = resultSet.string(tagResultName.id),
        label = resultSet.string(tagResultName.label),
        display = TagDisplay.matchTagDisplayOrDefault(resultSet.string(tagResultName.display)),
        tagTypeId = resultSet.string(tagResultName.tagTypeId),
        operationId = resultSet.stringOpt(tagResultName.operationId),
        themeId = resultSet.stringOpt(tagResultName.themeId),
        weight = resultSet.float(tagResultName.weight),
        country = resultSet.string(tagResultName.country),
        language = resultSet.string(tagResultName.language),
        createdAt = resultSet.zonedDateTime(tagResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(tagResultName.updatedAt)
      )
    }
  }

}
