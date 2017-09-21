package org.make.api.tag

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.tag.DefaultPersistentTagServiceComponent.PersistentTag
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.core.DateHelper
import org.make.core.reference.{Tag, TagId}
import scalikejdbc._

import scala.concurrent.Future

trait PersistentTagServiceComponent {
  def persistentTagService: PersistentTagService
}

trait PersistentTagService {
  def get(slug: TagId): Future[Option[Tag]]
  def findAllEnabled(): Future[Seq[Tag]]
  def findAllEnabledFromIds(tagsIds: Seq[TagId]): Future[Seq[Tag]]
  def persist(tag: Tag): Future[Tag]
}

trait DefaultPersistentTagServiceComponent extends PersistentTagServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentTagService = new PersistentTagService with ShortenedNames with StrictLogging {

    private val tagAlias = PersistentTag.tagAlias
    private val column = PersistentTag.column

    override def get(slug: TagId): Future[Option[Tag]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTag = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTag.as(tagAlias))
            .where(sqls.eq(tagAlias.slug, slug.value))
        }.map(PersistentTag.apply()).single.apply
      })

      futurePersistentTag.map(_.map(_.toTag))
    }

    override def findAllEnabled(): Future[Seq[Tag]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTags = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTag.as(tagAlias))
            .where(sqls.eq(tagAlias.enabled, true))
        }.map(PersistentTag.apply()).list.apply
      })

      futurePersistentTags.map(_.map(_.toTag))
    }

    override def findAllEnabledFromIds(tagsIds: Seq[TagId]): Future[Seq[Tag]] = {
      implicit val context: EC = readExecutionContext
      val uniqueTagsIds: Seq[String] = tagsIds.distinct.map(_.value)
      val futurePersistentTags: Future[List[PersistentTag]] = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTag.as(tagAlias))
            .where(
              sqls
                .eq(tagAlias.enabled, true)
                .and(sqls.in(tagAlias.slug, uniqueTagsIds))
            )
        }.map(PersistentTag.apply()).list.apply
      })

      futurePersistentTags.map(_.map(_.toTag))
    }

    override def persist(tag: Tag): Future[Tag] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentTag)
            .namedValues(
              column.slug -> tag.tagId.value,
              column.label -> tag.label,
              column.enabled -> true,
              column.createdAt -> DateHelper.now,
              column.updatedAt -> DateHelper.now
            )
        }.execute().apply()
      }).map(_ => tag)
    }
  }
}

object DefaultPersistentTagServiceComponent {

  case class PersistentTag(slug: String,
                           label: String,
                           enabled: Boolean,
                           createdAt: ZonedDateTime,
                           updatedAt: ZonedDateTime) {
    def toTag: Tag =
      Tag(tagId = TagId(slug), label = label)
  }

  object PersistentTag extends SQLSyntaxSupport[PersistentTag] with ShortenedNames with StrictLogging {

    override val columnNames: Seq[String] = Seq("slug", "label", "enabled", "created_at", "updated_at")

    override val tableName: String = "tag"

    lazy val tagAlias: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentTag], PersistentTag] = syntax("ta")

    def apply(
      tagResultName: ResultName[PersistentTag] = tagAlias.resultName
    )(resultSet: WrappedResultSet): PersistentTag = {
      PersistentTag.apply(
        slug = resultSet.string(tagResultName.slug),
        label = resultSet.string(tagResultName.label),
        enabled = resultSet.boolean(tagResultName.enabled),
        createdAt = resultSet.zonedDateTime(tagResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(tagResultName.updatedAt)
      )
    }
  }

}
