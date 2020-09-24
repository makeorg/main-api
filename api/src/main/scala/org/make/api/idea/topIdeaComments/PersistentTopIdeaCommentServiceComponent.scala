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

package org.make.api.idea.topIdeaComments

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.idea.topIdeaComments.DefaultPersistentTopIdeaCommentServiceComponent.PersistentTopIdeaComment
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ScalikeSupport._
import org.make.api.technical.ShortenedNames
import org.make.core.DateHelper
import org.make.core.idea._
import org.make.core.user.UserId
import scalikejdbc._

import scala.concurrent.Future

trait PersistentTopIdeaCommentServiceComponent {
  def persistentTopIdeaCommentService: PersistentTopIdeaCommentService
}

trait PersistentTopIdeaCommentService {
  def getById(topIdeaCommentId: TopIdeaCommentId): Future[Option[TopIdeaComment]]
  def persist(topIdeaComment: TopIdeaComment): Future[TopIdeaComment]
  def modify(topIdeaComment: TopIdeaComment): Future[TopIdeaComment]
  def remove(topIdeaCommentId: TopIdeaCommentId): Future[Unit]
  def search(
    start: Int,
    end: Option[Int],
    topIdeaIds: Option[Seq[TopIdeaId]],
    personalityIds: Option[Seq[UserId]]
  ): Future[Seq[TopIdeaComment]]
  def count(topIdeaIds: Option[Seq[TopIdeaId]], personalityIds: Option[Seq[UserId]]): Future[Int]
  def countForAll(topIdeaIds: Seq[TopIdeaId]): Future[Map[String, Int]]
}

trait DefaultPersistentTopIdeaCommentServiceComponent extends PersistentTopIdeaCommentServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentTopIdeaCommentService: PersistentTopIdeaCommentService =
    new DefaultPersistentTopIdeaCommentService

  class DefaultPersistentTopIdeaCommentService
      extends PersistentTopIdeaCommentService
      with ShortenedNames
      with StrictLogging {

    private val topIdeaCommentAlias = PersistentTopIdeaComment.topIdeaCommentAlias
    private val column = PersistentTopIdeaComment.column

    override def getById(topIdeaCommentId: TopIdeaCommentId): Future[Option[TopIdeaComment]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTopIdeaComment = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTopIdeaComment.as(topIdeaCommentAlias))
            .where(sqls.eq(topIdeaCommentAlias.id, topIdeaCommentId.value))
        }.map(PersistentTopIdeaComment.apply()).single.apply
      })

      futurePersistentTopIdeaComment.map(_.map(_.toTopIdeaComment))
    }

    override def persist(topIdeaComment: TopIdeaComment): Future[TopIdeaComment] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentTopIdeaComment)
            .namedValues(
              column.id -> topIdeaComment.topIdeaCommentId.value,
              column.topIdeaId -> topIdeaComment.topIdeaId.value,
              column.personalityId -> topIdeaComment.personalityId.value,
              column.comment1 -> topIdeaComment.comment1,
              column.comment2 -> topIdeaComment.comment2,
              column.comment3 -> topIdeaComment.comment3,
              column.vote -> topIdeaComment.vote,
              column.qualification -> topIdeaComment.qualification,
              column.createdAt -> DateHelper.now()
            )
        }.execute().apply()
      }).map(_ => topIdeaComment)
    }

    override def modify(topIdeaComment: TopIdeaComment): Future[TopIdeaComment] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentTopIdeaComment)
            .set(
              column.topIdeaId -> topIdeaComment.topIdeaId.value,
              column.personalityId -> topIdeaComment.personalityId.value,
              column.comment1 -> topIdeaComment.comment1,
              column.comment2 -> topIdeaComment.comment2,
              column.comment3 -> topIdeaComment.comment3,
              column.vote -> topIdeaComment.vote,
              column.qualification -> topIdeaComment.qualification,
              column.updatedAt -> DateHelper.now()
            )
            .where(
              sqls
                .eq(column.id, topIdeaComment.topIdeaCommentId.value)
            )
        }.update().apply()
      }).map(_ => topIdeaComment)
    }

    override def remove(topIdeaCommentId: TopIdeaCommentId): Future[Unit] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          delete
            .from(PersistentTopIdeaComment.as(topIdeaCommentAlias))
            .where(sqls.eq(topIdeaCommentAlias.id, topIdeaCommentId.value))
        }.update.apply()
        () // TODO check success
      })
    }

    override def search(
      start: Int,
      end: Option[Int],
      topIdeaIds: Option[Seq[TopIdeaId]],
      personalityIds: Option[Seq[UserId]]
    ): Future[Seq[TopIdeaComment]] = {
      implicit val context: EC = readExecutionContext

      val futurePersistentTopIdeaComments = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          val query: scalikejdbc.PagingSQLBuilder[PersistentTopIdeaComment] = select
            .from(PersistentTopIdeaComment.as(topIdeaCommentAlias))
            .where(
              sqls.toAndConditionOpt(
                topIdeaIds.map(ids     => sqls.in(topIdeaCommentAlias.topIdeaId, ids.map(_.value))),
                personalityIds.map(ids => sqls.in(topIdeaCommentAlias.personalityId, ids.map(_.value)))
              )
            )
            .orderBy(topIdeaCommentAlias.createdAt)
            .asc

          end match {
            case Some(limit) => query.limit(limit)
            case None        => query
          }
        }.map(PersistentTopIdeaComment.apply()).list.apply
      })

      futurePersistentTopIdeaComments.map(_.map(_.toTopIdeaComment))
    }

    override def count(topIdeaIds: Option[Seq[TopIdeaId]], personalityIds: Option[Seq[UserId]]): Future[Int] = {
      implicit val context: EC = readExecutionContext

      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(sqls.count)
            .from(PersistentTopIdeaComment.as(topIdeaCommentAlias))
            .where(
              sqls.toAndConditionOpt(
                topIdeaIds.map(ids     => sqls.in(topIdeaCommentAlias.topIdeaId, ids.map(_.value))),
                personalityIds.map(ids => sqls.in(topIdeaCommentAlias.personalityId, ids.map(_.value)))
              )
            )
        }.map(_.int(1)).single.apply().getOrElse(0)
      })
    }

    def countForAll(topIdeaIds: Seq[TopIdeaId]): Future[Map[String, Int]] = {
      implicit val context: EC = readExecutionContext

      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(topIdeaCommentAlias.topIdeaId, sqls.count)
            .from(PersistentTopIdeaComment.as(topIdeaCommentAlias))
            .where(sqls.in(topIdeaCommentAlias.topIdeaId, topIdeaIds.map(_.value)))
            .groupBy(topIdeaCommentAlias.topIdeaId)
        }.map(rs => (rs.string(1), rs.int(2))).list.apply().toMap
      })
    }

  }
}

object DefaultPersistentTopIdeaCommentServiceComponent {

  final case class PersistentTopIdeaComment(
    id: String,
    topIdeaId: String,
    personalityId: String,
    comment1: Option[String],
    comment2: Option[String],
    comment3: Option[String],
    vote: String,
    qualification: Option[String],
    createdAt: Option[ZonedDateTime],
    updatedAt: Option[ZonedDateTime]
  ) {
    def toTopIdeaComment: TopIdeaComment =
      TopIdeaComment(
        topIdeaCommentId = TopIdeaCommentId(id),
        topIdeaId = TopIdeaId(topIdeaId),
        personalityId = UserId(personalityId),
        comment1 = comment1,
        comment2 = comment2,
        comment3 = comment3,
        vote = CommentVoteKey.withValue(vote),
        qualification = qualification.flatMap(CommentQualificationKey.withValueOpt),
        createdAt = createdAt,
        updatedAt = updatedAt
      )
  }

  object PersistentTopIdeaComment
      extends SQLSyntaxSupport[PersistentTopIdeaComment]
      with ShortenedNames
      with StrictLogging {

    override val columnNames: Seq[String] =
      Seq(
        "id",
        "top_idea_id",
        "personality_id",
        "comment1",
        "comment2",
        "comment3",
        "vote",
        "qualification",
        "created_at",
        "updated_at"
      )

    override val tableName: String = "top_idea_comment"

    lazy val topIdeaCommentAlias: SyntaxProvider[PersistentTopIdeaComment] = syntax("top_idea_comment")

    def apply(
      topIdeaCommentResultName: ResultName[PersistentTopIdeaComment] = topIdeaCommentAlias.resultName
    )(resultSet: WrappedResultSet): PersistentTopIdeaComment = {
      PersistentTopIdeaComment.apply(
        id = resultSet.string(topIdeaCommentResultName.id),
        topIdeaId = resultSet.string(topIdeaCommentResultName.topIdeaId),
        personalityId = resultSet.string(topIdeaCommentResultName.personalityId),
        comment1 = resultSet.stringOpt(topIdeaCommentResultName.comment1),
        comment2 = resultSet.stringOpt(topIdeaCommentResultName.comment2),
        comment3 = resultSet.stringOpt(topIdeaCommentResultName.comment3),
        vote = resultSet.string(topIdeaCommentResultName.vote),
        qualification = resultSet.stringOpt(topIdeaCommentResultName.qualification),
        createdAt = resultSet.zonedDateTimeOpt(topIdeaCommentResultName.createdAt),
        updatedAt = resultSet.zonedDateTimeOpt(topIdeaCommentResultName.updatedAt)
      )
    }
  }
}
