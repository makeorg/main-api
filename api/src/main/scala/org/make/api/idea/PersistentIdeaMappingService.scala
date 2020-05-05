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

package org.make.api.idea
import cats.data.NonEmptyList
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.idea.DefaultPersistentIdeaMappingServiceComponent.PersistentIdeaMapping
import org.make.api.technical.DatabaseTransactions.RichDatabase
import org.make.api.technical.PersistentServiceUtils.sortOrderQuery
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.core.idea.IdeaId
import org.make.core.question.QuestionId
import org.make.core.tag.TagId
import scalikejdbc._

import scala.concurrent.Future

trait PersistentIdeaMappingService {

  def persist(mapping: IdeaMapping): Future[IdeaMapping]
  def find(
    start: Int,
    end: Option[Int],
    sort: Option[String],
    order: Option[String],
    questionId: Option[QuestionId],
    stakeTagId: Option[TagIdOrNone],
    solutionTypeTagId: Option[TagIdOrNone],
    ideaId: Option[IdeaId]
  ): Future[Seq[IdeaMapping]]
  def get(id: IdeaMappingId): Future[Option[IdeaMapping]]
  def updateMapping(mapping: IdeaMapping): Future[Option[IdeaMapping]]
  def count(
    questionId: Option[QuestionId],
    stakeTagId: Option[TagIdOrNone],
    solutionTypeTagId: Option[TagIdOrNone],
    ideaId: Option[IdeaId]
  ): Future[Int]
}

trait PersistentIdeaMappingServiceComponent {
  def persistentIdeaMappingService: PersistentIdeaMappingService
}

trait DefaultPersistentIdeaMappingServiceComponent extends PersistentIdeaMappingServiceComponent with ShortenedNames {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentIdeaMappingService: PersistentIdeaMappingService = new DefaultPersistentIdeaMappingService

  class DefaultPersistentIdeaMappingService extends PersistentIdeaMappingService with StrictLogging {
    override def persist(mapping: IdeaMapping): Future[IdeaMapping] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentIdeaMapping)
            .namedValues(
              PersistentIdeaMapping.column.id -> mapping.id.value,
              PersistentIdeaMapping.column.questionId -> mapping.questionId.value,
              PersistentIdeaMapping.column.stakeTagId -> mapping.stakeTagId.map(_.value),
              PersistentIdeaMapping.column.solutionTypeTagId -> mapping.solutionTypeTagId.map(_.value),
              PersistentIdeaMapping.column.ideaId -> mapping.ideaId.value
            )
        }.execute().apply()
      }).map(_ => mapping)
    }

    override def find(
      start: Int,
      end: Option[Int],
      sort: Option[String],
      order: Option[String],
      questionId: Option[QuestionId],
      stakeTagId: Option[TagIdOrNone],
      solutionTypeTagId: Option[TagIdOrNone],
      ideaId: Option[IdeaId]
    ): Future[Seq[IdeaMapping]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          val query: scalikejdbc.PagingSQLBuilder[PersistentIdeaMapping] =
            select
              .from(PersistentIdeaMapping.as(PersistentIdeaMapping.alias))
              .where(
                sqls.toAndConditionOpt(
                  questionId.map(question => sqls.eq(PersistentIdeaMapping.column.questionId, question.value)),
                  stakeTagId.map {
                    case Left(None)          => sqls.isNull(PersistentIdeaMapping.column.stakeTagId)
                    case Right(TagId(tagId)) => sqls.eq(PersistentIdeaMapping.column.stakeTagId, tagId)
                  },
                  solutionTypeTagId.map {
                    case Left(None)          => sqls.isNull(PersistentIdeaMapping.column.solutionTypeTagId)
                    case Right(TagId(tagId)) => sqls.eq(PersistentIdeaMapping.column.solutionTypeTagId, tagId)
                  },
                  ideaId.map(idea => sqls.eq(PersistentIdeaMapping.column.ideaId, idea.value))
                )
              )
          sortOrderQuery(start, end, sort, order, query)
        }.map(PersistentIdeaMapping.apply()).list().apply()
      }).map(_.map(_.toIdeaMapping))
    }

    override def get(id: IdeaMappingId): Future[Option[IdeaMapping]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL[PersistentIdeaMapping] {
          select
            .from(PersistentIdeaMapping.as(PersistentIdeaMapping.alias))
            .where(sqls.eq(PersistentIdeaMapping.column.id, id.value))
        }.map(PersistentIdeaMapping(PersistentIdeaMapping.alias.resultName)(_)).single.apply()
      }).map(_.map(_.toIdeaMapping))
    }

    override def updateMapping(mapping: IdeaMapping): Future[Option[IdeaMapping]] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentIdeaMapping)
            .set(
              PersistentIdeaMapping.column.questionId -> mapping.questionId.value,
              PersistentIdeaMapping.column.stakeTagId -> mapping.stakeTagId.map(_.value),
              PersistentIdeaMapping.column.solutionTypeTagId -> mapping.solutionTypeTagId.map(_.value),
              PersistentIdeaMapping.column.ideaId -> mapping.ideaId.value
            )
            .where(sqls.eq(PersistentIdeaMapping.column.id, mapping.id.value))

        }.execute().apply()
      }).flatMap(_ => get(mapping.id))
    }

    override def count(
      questionId: Option[QuestionId],
      stakeTagId: Option[TagIdOrNone],
      solutionTypeTagId: Option[TagIdOrNone],
      ideaId: Option[IdeaId]
    ): Future[Int] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL[PersistentIdeaMapping] {
          select(sqls.count)
            .from(PersistentIdeaMapping.as(PersistentIdeaMapping.alias))
            .where(
              sqls.toAndConditionOpt(
                questionId.map(question => sqls.eq(PersistentIdeaMapping.column.questionId, question.value)),
                stakeTagId.map {
                  case Left(None)          => sqls.isNull(PersistentIdeaMapping.column.stakeTagId)
                  case Right(TagId(tagId)) => sqls.eq(PersistentIdeaMapping.column.stakeTagId, tagId)
                },
                solutionTypeTagId.map {
                  case Left(None)          => sqls.isNull(PersistentIdeaMapping.column.solutionTypeTagId)
                  case Right(TagId(tagId)) => sqls.eq(PersistentIdeaMapping.column.solutionTypeTagId, tagId)
                },
                ideaId.map(idea => sqls.eq(PersistentIdeaMapping.column.ideaId, idea.value))
              )
            )
        }.map(_.int(1)).single.apply().getOrElse(0)
      })
    }

  }
}

object DefaultPersistentIdeaMappingServiceComponent {

  final case class PersistentIdeaMapping(
    id: String,
    questionId: String,
    stakeTagId: Option[String],
    solutionTypeTagId: Option[String],
    ideaId: String
  ) {
    def toIdeaMapping: IdeaMapping =
      IdeaMapping(
        IdeaMappingId(id),
        QuestionId(questionId),
        stakeTagId.map(TagId.apply),
        solutionTypeTagId.map(TagId.apply),
        IdeaId(ideaId)
      )
  }

  implicit object PersistentIdeaMapping
      extends PersistentCompanion[PersistentIdeaMapping, IdeaMapping]
      with ShortenedNames
      with StrictLogging {

    override val columnNames: Seq[String] = Seq("id", "question_id", "stake_tag_id", "solution_type_tag_id", "idea_id")
    override val tableName: String = "idea_mapping"

    override lazy val alias: SyntaxProvider[PersistentIdeaMapping] = syntax("ideaMapping")
    override lazy val defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.id)

    def apply(
      resultName: ResultName[PersistentIdeaMapping] = alias.resultName
    )(resultSet: WrappedResultSet): PersistentIdeaMapping = {
      PersistentIdeaMapping(
        id = resultSet.string(resultName.id),
        questionId = resultSet.string(resultName.questionId),
        stakeTagId = resultSet.stringOpt(resultName.stakeTagId),
        solutionTypeTagId = resultSet.stringOpt(resultName.solutionTypeTagId),
        ideaId = resultSet.string(resultName.ideaId)
      )
    }
  }
}
