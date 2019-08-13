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

package org.make.api.tag

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.tag.DefaultPersistentTagServiceComponent.PersistentTag
import org.make.api.tagtype.DefaultPersistentTagTypeServiceComponent.PersistentTagType
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.core.DateHelper
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
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
  def findAllDisplayed(): Future[Seq[Tag]]
  def findByLabel(label: String): Future[Seq[Tag]]
  def findByLabelLike(partialLabel: String): Future[Seq[Tag]]
  def findByQuestion(questionId: QuestionId): Future[Seq[Tag]]
  def findByQuestions(questionIds: Seq[QuestionId]): Future[Seq[Tag]]
  def persist(tag: Tag): Future[Tag]
  def update(tag: Tag): Future[Option[Tag]]
  def remove(tagId: TagId): Future[Int]
  def find(start: Int,
           end: Option[Int],
           sort: Option[String],
           order: Option[String],
           onlyDisplayed: Boolean,
           persistentTagFilter: PersistentTagFilter): Future[Seq[Tag]]
  def count(persistentTagFilter: PersistentTagFilter): Future[Int]
}

case class PersistentTagFilter(label: Option[String], questionId: Option[QuestionId], tagTypeId: Option[TagTypeId])
object PersistentTagFilter {
  def empty: PersistentTagFilter = PersistentTagFilter(None, None, None)
}

trait DefaultPersistentTagServiceComponent extends PersistentTagServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentTagService: PersistentTagService = new DefaultPersistentTagService

  class DefaultPersistentTagService extends PersistentTagService with ShortenedNames with StrictLogging {

    private val tagAlias = PersistentTag.tagAlias
    private val tagTypeAlias = PersistentTagType.tagTypeAlias

    private val column = PersistentTag.column

    override def findByQuestion(questionId: QuestionId): Future[Seq[Tag]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTag = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTag.as(tagAlias))
            .where(sqls.eq(tagAlias.questionId, questionId.value))
        }.map(PersistentTag.apply()).list().apply
      })

      futurePersistentTag.map(_.map(_.toTag))
    }

    override def findByQuestions(questionIds: Seq[QuestionId]): Future[Seq[Tag]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTag = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTag.as(tagAlias))
            .where(sqls.in(tagAlias.questionId, questionIds.map(_.value)))
        }.map(PersistentTag.apply()).list().apply
      })

      futurePersistentTag.map(_.map(_.toTag))
    }

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
            .orderBy(tagAlias.weight, tagAlias.label)
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

    override def findAllDisplayed(): Future[Seq[Tag]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTags: Future[List[PersistentTag]] = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTag.as(tagAlias))
            .leftJoin(PersistentTagType.as(tagTypeAlias))
            .on(tagAlias.tagTypeId, tagTypeAlias.id)
            .where(
              sqls
                .eq(tagAlias.display, TagDisplay.Displayed.shortName)
                .or(
                  sqls
                    .eq(tagAlias.display, TagDisplay.Inherit.shortName)
                    .and(sqls.eq(tagTypeAlias.display, TagDisplay.Displayed.shortName))
                )
            )
            .orderBy(tagAlias.weight, tagAlias.label)
        }.map(PersistentTag.apply()).list.apply
      })

      futurePersistentTags.map(_.map(_.toTag))
    }

    def findByLabel(label: String): Future[Seq[Tag]] = {
      implicit val context: EC = readExecutionContext
      val preparedLabel: String = label.replace("%", "\\%")
      val futurePersistentTags: Future[List[PersistentTag]] = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTag.as(tagAlias))
            .where(sqls.eq(tagAlias.label, preparedLabel))
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
              column.questionId -> tag.questionId.map(_.value),
              column.weight -> tag.weight,
              column.country -> tag.country.value,
              column.language -> tag.language.value,
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
              column.questionId -> tag.questionId.map(_.value),
              column.weight -> tag.weight,
              column.country -> tag.country.value,
              column.language -> tag.language.value,
              column.updatedAt -> nowDate
            )
            .where(sqls.eq(column.id, tag.tagId.value))
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

    override def find(start: Int,
                      end: Option[Int],
                      sort: Option[String],
                      order: Option[String],
                      onlyDisplayed: Boolean,
                      persistentTagFilter: PersistentTagFilter): Future[Seq[Tag]] = {
      implicit val context: EC = readExecutionContext

      val futurePersistentTags: Future[List[PersistentTag]] = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {

          val query: scalikejdbc.PagingSQLBuilder[WrappedResultSet] =
            select
              .from(PersistentTag.as(tagAlias))
              .leftJoin(PersistentTagType.as(tagTypeAlias))
              .on(tagAlias.tagTypeId, tagTypeAlias.id)
              .where(
                sqls.toAndConditionOpt(
                  persistentTagFilter.label
                    .map(
                      label => sqls.like(sqls"lower(${tagAlias.label})", s"%${label.toLowerCase.replace("%", "\\%")}%")
                    ),
                  persistentTagFilter.tagTypeId.map(tagTypeId   => sqls.eq(tagAlias.tagTypeId, tagTypeId.value)),
                  persistentTagFilter.questionId.map(questionId => sqls.eq(tagAlias.questionId, questionId.value)),
                  if (onlyDisplayed) {
                    Some(
                      sqls
                        .eq(tagAlias.display, TagDisplay.Displayed.shortName)
                        .or(
                          sqls
                            .eq(tagAlias.display, TagDisplay.Inherit.shortName)
                            .and(sqls.eq(tagTypeAlias.display, TagDisplay.Displayed.shortName))
                        )
                    )
                  } else {
                    None
                  }
                )
              )

          val queryOrdered = (sort, order) match {
            case (Some(field), Some("DESC")) if PersistentTag.columnNames.contains(field) =>
              query.orderBy(tagAlias.field(field)).desc.offset(start)
            case (Some(field), _) if PersistentTag.columnNames.contains(field) =>
              query.orderBy(tagAlias.field(field)).asc.offset(start)
            case (Some(field), _) =>
              logger.warn(s"Unsupported filter '$field'")
              query.orderBy(tagAlias.weight, tagAlias.label).asc.offset(start)
            case (_, _) => query.orderBy(tagAlias.weight, tagAlias.label).asc.offset(start)
          }
          end match {
            case Some(limit) => queryOrdered.limit(limit)
            case None        => queryOrdered
          }
        }.map(PersistentTag.apply()).list.apply
      })

      futurePersistentTags.map(_.map(_.toTag))
    }

    override def count(persistentTagFilter: PersistentTagFilter): Future[Int] = {
      implicit val context: EC = readExecutionContext

      Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {

          select(sqls.count)
            .from(PersistentTag.as(tagAlias))
            .where(
              sqls.toAndConditionOpt(
                persistentTagFilter.label
                  .map(
                    label => sqls.like(sqls"lower(${tagAlias.label})", s"%${label.toLowerCase.replace("%", "\\%")}%")
                  ),
                persistentTagFilter.tagTypeId.map(tagTypeId   => sqls.eq(tagAlias.tagTypeId, tagTypeId.value)),
                persistentTagFilter.questionId.map(questionId => sqls.eq(tagAlias.questionId, questionId.value))
              )
            )
        }.map(_.int(1)).single.apply().getOrElse(0)
      })
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
                           questionId: Option[String],
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
        questionId = questionId.map(QuestionId(_)),
        weight = weight,
        country = Country(country),
        language = Language(language)
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
      "updated_at",
      "question_id"
    )

    override val tableName: String = "tag"

    lazy val tagAlias: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentTag], PersistentTag] = syntax("tag")

    def apply(
      tagResultName: ResultName[PersistentTag] = tagAlias.resultName
    )(resultSet: WrappedResultSet): PersistentTag = {
      PersistentTag.apply(
        id = resultSet.string(tagResultName.id),
        label = resultSet.string(tagResultName.label),
        display = TagDisplay.matchTagDisplayOrDefault(resultSet.string(tagResultName.display)),
        tagTypeId = resultSet.string(tagResultName.tagTypeId),
        operationId = resultSet.stringOpt(tagResultName.operationId),
        questionId = resultSet.stringOpt(tagResultName.questionId),
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
