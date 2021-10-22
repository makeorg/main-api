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

package org.make.api.feature

import cats.data.NonEmptyList
import grizzled.slf4j.Logging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.feature.DefaultPersistentActiveFeatureServiceComponent.PersistentActiveFeature
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.PersistentServiceUtils.sortOrderQuery
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.api.technical.Futures._
import org.make.api.technical.ScalikeSupport._
import org.make.core.feature.{ActiveFeature, ActiveFeatureId, FeatureId}
import org.make.core.question.QuestionId
import org.make.core.Order
import scalikejdbc._
import org.make.core.technical.Pagination._

import scala.concurrent.Future

trait DefaultPersistentActiveFeatureServiceComponent extends PersistentActiveFeatureServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentActiveFeatureService: PersistentActiveFeatureService =
    new DefaultPersistentActiveFeatureService

  class DefaultPersistentActiveFeatureService extends PersistentActiveFeatureService with ShortenedNames with Logging {

    private val activeFeatureAlias = PersistentActiveFeature.alias

    private val column = PersistentActiveFeature.column

    override def get(activeFeatureId: ActiveFeatureId): Future[Option[ActiveFeature]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentActiveFeature = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentActiveFeature.as(activeFeatureAlias))
            .where(sqls.eq(activeFeatureAlias.id, activeFeatureId))
        }.map(PersistentActiveFeature.apply()).single().apply()
      })

      futurePersistentActiveFeature.map(_.map(_.toActiveFeature))
    }

    override def persist(activeFeature: ActiveFeature): Future[ActiveFeature] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentActiveFeature)
            .namedValues(
              column.id -> activeFeature.activeFeatureId,
              column.featureId -> activeFeature.featureId,
              column.questionId -> activeFeature.maybeQuestionId
            )
        }.execute().apply()
      }).map(_ => activeFeature)
    }

    override def remove(activeFeatureId: ActiveFeatureId): Future[Unit] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          delete
            .from(PersistentActiveFeature.as(activeFeatureAlias))
            .where(sqls.eq(activeFeatureAlias.id, activeFeatureId))
        }.update().apply()
      }).toUnit
    }

    override def find(
      start: Start,
      end: Option[End],
      sort: Option[String],
      order: Option[Order],
      maybeQuestionId: Option[Seq[QuestionId]],
      featureId: Option[Seq[FeatureId]]
    ): Future[Seq[ActiveFeature]] = {
      implicit val context: EC = readExecutionContext

      val futurePersistentActiveFeatures: Future[List[PersistentActiveFeature]] = Future(NamedDB("READ").retryableTx {
        implicit session =>
          withSQL {
            val query: scalikejdbc.PagingSQLBuilder[PersistentActiveFeature] =
              select
                .from(PersistentActiveFeature.as(activeFeatureAlias))
                .where(
                  sqls.toAndConditionOpt(
                    maybeQuestionId.map(sqls.in(activeFeatureAlias.questionId, _)),
                    featureId.map(sqls.in(activeFeatureAlias.featureId, _))
                  )
                )
            sortOrderQuery(start, end, sort, order, query)
          }.map(PersistentActiveFeature.apply()).list().apply()
      })

      futurePersistentActiveFeatures.map(_.map(_.toActiveFeature))
    }

    override def count(maybeQuestionId: Option[QuestionId]): Future[Int] = {
      implicit val context: EC = readExecutionContext

      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(sqls.count)
            .from(PersistentActiveFeature.as(activeFeatureAlias))
            .where(
              sqls.toAndConditionOpt(
                maybeQuestionId.map(questionId => sqls.eq(activeFeatureAlias.questionId, questionId))
              )
            )
        }.map(_.int(1)).single().apply().getOrElse(0)
      })
    }
  }
}

object DefaultPersistentActiveFeatureServiceComponent {

  final case class PersistentActiveFeature(id: String, featureId: String, questionId: Option[String]) {
    def toActiveFeature: ActiveFeature =
      ActiveFeature(
        activeFeatureId = ActiveFeatureId(id),
        featureId = FeatureId(featureId),
        maybeQuestionId = questionId.map(QuestionId(_))
      )
  }

  implicit object PersistentActiveFeature
      extends PersistentCompanion[PersistentActiveFeature, ActiveFeature]
      with ShortenedNames
      with Logging {

    override val columnNames: Seq[String] = Seq("id", "feature_id", "question_id")

    override val tableName: String = "active_feature"

    override lazy val alias: SyntaxProvider[PersistentActiveFeature] = syntax("active_feature")

    override lazy val defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.id)

    def apply(
      activeFeatureResultName: ResultName[PersistentActiveFeature] = alias.resultName
    )(resultSet: WrappedResultSet): PersistentActiveFeature = {
      PersistentActiveFeature.apply(
        id = resultSet.string(activeFeatureResultName.id),
        featureId = resultSet.string(activeFeatureResultName.featureId),
        questionId = resultSet.stringOpt(activeFeatureResultName.questionId)
      )
    }
  }

}
