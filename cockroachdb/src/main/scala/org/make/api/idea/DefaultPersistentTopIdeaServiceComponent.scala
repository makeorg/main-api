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
import grizzled.slf4j.Logging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.idea.DefaultPersistentTopIdeaServiceComponent.PersistentTopIdea
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.PersistentServiceUtils.sortOrderQuery
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.core.idea.{IdeaId, TopIdea, TopIdeaId, TopIdeaScores}
import org.make.core.question.QuestionId
import org.make.core.Order
import scalikejdbc._

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait DefaultPersistentTopIdeaServiceComponent extends PersistentTopIdeaServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentTopIdeaService: PersistentTopIdeaService = new DefaultPersistentTopIdeaService

  class DefaultPersistentTopIdeaService extends PersistentTopIdeaService with ShortenedNames with Logging {

    private val topIdeaAlias = PersistentTopIdea.alias
    private val column = PersistentTopIdea.column

    override def getById(topIdeaId: TopIdeaId): Future[Option[TopIdea]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTopIdea = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTopIdea.as(topIdeaAlias))
            .where(sqls.eq(topIdeaAlias.id, topIdeaId.value))
        }.map(PersistentTopIdea.apply()).single().apply()
      })

      futurePersistentTopIdea.map(_.map(_.toTopIdea))
    }

    override def getByIdAndQuestionId(topIdeaId: TopIdeaId, questionId: QuestionId): Future[Option[TopIdea]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTopIdea = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentTopIdea.as(topIdeaAlias))
            .where(sqls.eq(topIdeaAlias.id, topIdeaId.value).and(sqls.eq(topIdeaAlias.questionId, questionId.value)))
        }.map(PersistentTopIdea.apply()).single().apply()
      })

      futurePersistentTopIdea.map(_.map(_.toTopIdea))
    }

    override def search(
      start: Start,
      end: Option[End],
      sort: Option[String],
      order: Option[Order],
      ideaId: Option[IdeaId],
      questionIds: Option[Seq[QuestionId]],
      name: Option[String]
    ): Future[Seq[TopIdea]] = {
      implicit val context: EC = readExecutionContext

      val futurePersistentTopIdeas = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          val query: scalikejdbc.PagingSQLBuilder[PersistentTopIdea] = select
            .from(PersistentTopIdea.as(topIdeaAlias))
            .where(
              sqls.toAndConditionOpt(
                ideaId.map(ideaId   => sqls.eq(topIdeaAlias.ideaId, ideaId.value)),
                questionIds.map(ids => sqls.in(topIdeaAlias.questionId, ids.map(_.value))),
                name.map(name       => sqls.like(topIdeaAlias.name, name))
              )
            )

          sortOrderQuery(start, end, sort, order, query)
        }.map(PersistentTopIdea.apply()).list().apply()
      })

      futurePersistentTopIdeas.map(_.map(_.toTopIdea))
    }

    override def persist(topIdea: TopIdea): Future[TopIdea] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentTopIdea)
            .namedValues(
              column.id -> topIdea.topIdeaId.value,
              column.ideaId -> topIdea.ideaId.value,
              column.questionId -> topIdea.questionId.value,
              column.name -> topIdea.name,
              column.label -> topIdea.label,
              column.totalProposalsRatio -> topIdea.scores.totalProposalsRatio,
              column.agreementRatio -> topIdea.scores.agreementRatio,
              column.likeItRatio -> topIdea.scores.likeItRatio,
              column.weight -> topIdea.weight
            )
        }.execute().apply()
      }).map(_ => topIdea)
    }

    override def modify(topIdea: TopIdea): Future[TopIdea] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentTopIdea)
            .set(
              column.ideaId -> topIdea.ideaId.value,
              column.questionId -> topIdea.questionId.value,
              column.name -> topIdea.name,
              column.label -> topIdea.label,
              column.totalProposalsRatio -> topIdea.scores.totalProposalsRatio,
              column.agreementRatio -> topIdea.scores.agreementRatio,
              column.likeItRatio -> topIdea.scores.likeItRatio,
              column.weight -> topIdea.weight
            )
            .where(
              sqls
                .eq(column.id, topIdea.topIdeaId.value)
            )
        }.update().apply()
      }).map(_ => topIdea)
    }

    override def remove(topIdeaId: TopIdeaId): Future[Unit] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          delete
            .from(PersistentTopIdea.as(topIdeaAlias))
            .where(sqls.eq(topIdeaAlias.id, topIdeaId.value))
        }.update().apply()
        () // TODO check success
      })
    }

    override def count(ideaId: Option[IdeaId], questionId: Option[QuestionId], name: Option[String]): Future[Int] = {
      implicit val context: EC = readExecutionContext

      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(sqls.count)
            .from(PersistentTopIdea.as(topIdeaAlias))
            .where(
              sqls.toAndConditionOpt(
                ideaId.map(ideaId         => sqls.eq(topIdeaAlias.ideaId, ideaId.value)),
                questionId.map(questionId => sqls.eq(topIdeaAlias.questionId, questionId.value)),
                name.map(name             => sqls.like(topIdeaAlias.name, name))
              )
            )
        }.map(_.int(1)).single().apply().getOrElse(0)
      })
    }

  }
}

object DefaultPersistentTopIdeaServiceComponent {

  final case class PersistentTopIdea(
    id: String,
    ideaId: String,
    questionId: String,
    name: String,
    label: String,
    totalProposalsRatio: Float,
    agreementRatio: Float,
    likeItRatio: Float,
    weight: Float
  ) {
    def toTopIdea: TopIdea =
      TopIdea(
        topIdeaId = TopIdeaId(id),
        ideaId = IdeaId(ideaId),
        questionId = QuestionId(questionId),
        name = name,
        label = label,
        scores = TopIdeaScores(totalProposalsRatio, agreementRatio, likeItRatio),
        weight = weight
      )
  }

  implicit object PersistentTopIdea
      extends PersistentCompanion[PersistentTopIdea, TopIdea]
      with ShortenedNames
      with Logging {

    override val columnNames: Seq[String] =
      Seq(
        "id",
        "idea_id",
        "question_id",
        "name",
        "label",
        "total_proposals_ratio",
        "agreement_ratio",
        "like_it_ratio",
        "weight"
      )

    override val tableName: String = "top_idea"

    override lazy val alias: SyntaxProvider[PersistentTopIdea] = syntax("top_idea")

    override lazy val defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.weight)

    def apply(
      topIdeaResultName: ResultName[PersistentTopIdea] = alias.resultName
    )(resultSet: WrappedResultSet): PersistentTopIdea = {
      PersistentTopIdea.apply(
        id = resultSet.string(topIdeaResultName.id),
        ideaId = resultSet.string(topIdeaResultName.ideaId),
        questionId = resultSet.string(topIdeaResultName.questionId),
        name = resultSet.string(topIdeaResultName.name),
        label = resultSet.string(topIdeaResultName.label),
        totalProposalsRatio = resultSet.float(topIdeaResultName.totalProposalsRatio),
        agreementRatio = resultSet.float(topIdeaResultName.agreementRatio),
        likeItRatio = resultSet.float(topIdeaResultName.likeItRatio),
        weight = resultSet.float(topIdeaResultName.weight)
      )
    }
  }
}
