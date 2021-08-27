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
import org.make.api.technical.PersistentServiceUtils._
import org.make.api.technical.ScalikeSupport._
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.core.Order
import org.make.core.demographics.{DemographicsCard, DemographicsCardId}
import org.make.core.reference.Language
import org.make.core.technical.Pagination.{End, Start}
import scalikejdbc._

import scala.concurrent.Future

trait PersistentDemographicsCardService {
  def get(id: DemographicsCardId): Future[Option[DemographicsCard]]
  def list(
    start: Option[Start],
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    language: Option[Language],
    dataType: Option[String]
  ): Future[Seq[DemographicsCard]]
  def persist(demographicsCard: DemographicsCard): Future[DemographicsCard]
  def modify(demographicsCard: DemographicsCard): Future[DemographicsCard]
  def count(language: Option[Language], dataType: Option[String]): Future[Int]
}

trait PersistentDemographicsCardServiceComponent {
  def persistentDemographicsCardService: PersistentDemographicsCardService
}

trait DefaultPersistentDemographicsCardServiceComponent extends PersistentDemographicsCardServiceComponent {
  self: MakeDBExecutionContextComponent =>

  override def persistentDemographicsCardService: PersistentDemographicsCardService =
    new PersistentDemographicsCardService with ShortenedNames {

      private val demographicsCards = SQLSyntaxSupportFactory[DemographicsCard]()
      private val dc = demographicsCards.syntax

      private implicit val companion: PersistentCompanion[DemographicsCard, DemographicsCard] =
        new PersistentCompanion[DemographicsCard, DemographicsCard] {
          override def alias: SyntaxProvider[DemographicsCard] = dc
          override def defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.name)
          override val columnNames: Seq[String] =
            Seq("name", "layout", "dataType", "language", "title", "createdAt", "updatedAt")
        }

      override def get(id: DemographicsCardId): Future[Option[DemographicsCard]] = {
        implicit val context: EC = readExecutionContext
        Future(NamedDB("READ").retryableTx { implicit session =>
          withSQL { select.from(demographicsCards.as(dc)).where.eq(dc.id, id) }
            .map(demographicsCards.apply(dc.resultName))
            .single()
            .apply()
        })
      }

      override def list(
        start: Option[Start],
        end: Option[End],
        sort: Option[String],
        order: Option[Order],
        language: Option[Language],
        dataType: Option[String]
      ): Future[Seq[DemographicsCard]] = {
        implicit val context: EC = readExecutionContext
        Future(NamedDB("READ").retryableTx { implicit session =>
          withSQL {
            sortOrderQuery(
              start.orZero,
              end,
              sort,
              order,
              select
                .from(demographicsCards.as(dc))
                .where(
                  sqls.toAndConditionOpt(
                    language.map(l => sqls.eq(dc.language, l.value)),
                    dataType.map(d => sqls.like(dc.dataType, d))
                  )
                )
            )
          }.map(demographicsCards.apply(dc.resultName))
            .list()
            .apply()
        })
      }

      override def persist(demographicsCard: DemographicsCard): Future[DemographicsCard] = {
        implicit val context: EC = writeExecutionContext
        Future(NamedDB("WRITE").retryableTx { implicit session =>
          withSQL {
            insert.into(demographicsCards).namedValues(autoNamedValues(demographicsCard, demographicsCards.column))
          }.update().apply()
        }).map(_ => demographicsCard)
      }

      override def modify(demographicsCard: DemographicsCard): Future[DemographicsCard] = {
        implicit val context: EC = writeExecutionContext
        Future(NamedDB("WRITE").retryableTx { implicit session =>
          withSQL {
            update(demographicsCards)
              .set(autoNamedValues(demographicsCard, demographicsCards.column, "id", "createdAt"))
              .where
              .eq(dc.id, demographicsCard.id)
          }.update().apply()
        }).map(_ => demographicsCard)
      }

      override def count(language: Option[Language], dataType: Option[String]): Future[Int] = {
        implicit val context: EC = writeExecutionContext
        Future(NamedDB("READ").retryableTx { implicit session =>
          withSQL {
            select(sqls.count)
              .from(demographicsCards.as(dc))
              .where(
                sqls.toAndConditionOpt(
                  language.map(l => sqls.eq(dc.language, l.value)),
                  dataType.map(d => sqls.like(dc.dataType, d))
                )
              )
          }.map(_.int(1)).single().apply().getOrElse(0)
        })
      }
    }
}
