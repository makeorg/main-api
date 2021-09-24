/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.demographics

import cats.data.NonEmptyList
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.Futures._
import org.make.api.technical.PersistentServiceUtils._
import org.make.api.technical.ScalikeSupport._
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.core.Order
import org.make.core.demographics._
import org.make.core.question.QuestionId
import org.make.core.technical.Pagination.{End, Start}
import scalikejdbc._

import scala.concurrent.Future

trait DefaultPersistentActiveDemographicsCardServiceComponent extends PersistentActiveDemographicsCardServiceComponent {
  self: MakeDBExecutionContextComponent =>

  override val persistentActiveDemographicsCardService: PersistentActiveDemographicsCardService =
    new PersistentActiveDemographicsCardService with ShortenedNames { self =>

      private val table = SQLSyntaxSupportFactory[ActiveDemographicsCard]()
      private val alias = table.syntax

      private implicit val companion: PersistentCompanion[ActiveDemographicsCard, ActiveDemographicsCard] =
        new PersistentCompanion[ActiveDemographicsCard, ActiveDemographicsCard] {
          override val alias: SyntaxProvider[ActiveDemographicsCard] = self.alias
          override val columnNames: Seq[String] = Seq("id", "demographicsCardId", "questionId")
          override val defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.id)
        }

      override def get(id: ActiveDemographicsCardId): Future[Option[ActiveDemographicsCard]] = {
        implicit val context: EC = readExecutionContext
        Future(NamedDB("READ").retryableTx { implicit session =>
          withSQL { select.from(table.as(alias)).where.eq(alias.id, id) }
            .map(table.apply(alias.resultName))
            .single()
            .apply()
        })
      }

      override def count(questionId: Option[QuestionId], cardId: Option[DemographicsCardId]): Future[Int] = {
        implicit val context: EC = readExecutionContext
        Future(NamedDB("READ").retryableTx { implicit session =>
          withSQL {
            select(sqls.count)
              .from(table.as(alias))
              .where(
                sqls.toAndConditionOpt(
                  questionId.map(qId => sqls.eq(alias.questionId, qId)),
                  cardId.map(cId     => sqls.eq(alias.demographicsCardId, cId))
                )
              )
          }.map(_.int(1))
            .single()
            .apply()
            .getOrElse(0)
        })
      }

      override def list(
        start: Option[Start],
        end: Option[End],
        sort: Option[String],
        order: Option[Order],
        questionId: Option[QuestionId],
        cardId: Option[DemographicsCardId]
      ): Future[Seq[ActiveDemographicsCard]] = {
        implicit val context: EC = readExecutionContext
        Future(NamedDB("READ").retryableTx { implicit session =>
          withSQL {
            sortOrderQuery(
              start.orZero,
              end,
              sort,
              order,
              select
                .from(table.as(alias))
                .where(
                  sqls.toAndConditionOpt(
                    questionId.map(qId => sqls.eq(alias.questionId, qId)),
                    cardId.map(cId     => sqls.eq(alias.demographicsCardId, cId))
                  )
                )
            )
          }.map(table.apply(alias.resultName))
            .list()
            .apply()
        })
      }

      override def persist(activeDemographicsCard: ActiveDemographicsCard): Future[ActiveDemographicsCard] = {
        implicit val context: EC = writeExecutionContext
        Future(NamedDB("WRITE").retryableTx { implicit session =>
          withSQL { insert.into(table).namedValues(autoNamedValues(activeDemographicsCard, table.column)) }
            .update()
            .apply()
        }).map(_ => activeDemographicsCard)
      }

      override def delete(activeDemographicsCardId: ActiveDemographicsCardId): Future[Unit] = {
        implicit val context: EC = readExecutionContext
        Future(NamedDB("WRITE").retryableTx { implicit session =>
          withSQL {
            deleteFrom(table).where.eq(alias.id, activeDemographicsCardId)
          }.execute().apply()
        }).toUnit
      }
    }
}
