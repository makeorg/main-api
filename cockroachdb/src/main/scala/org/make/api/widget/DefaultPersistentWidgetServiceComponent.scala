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

package org.make.api.widget

import cats.data.NonEmptyList
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.PersistentServiceUtils._
import org.make.api.technical.ScalikeSupport._
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.core.Order
import org.make.core.technical.Pagination.{End, Start}
import org.make.core.widget.{SourceId, Widget, WidgetId}
import scalikejdbc._

import scala.concurrent.Future

trait DefaultPersistentWidgetServiceComponent extends PersistentWidgetServiceComponent {
  self: MakeDBExecutionContextComponent =>

  override def persistentWidgetService: PersistentWidgetService = new PersistentWidgetService with ShortenedNames {

    private val widgets = SQLSyntaxSupportFactory[Widget]()
    private val w = widgets.syntax

    private implicit val companion: PersistentCompanion[Widget, Widget] = new PersistentCompanion[Widget, Widget] {
      override val alias: SyntaxProvider[Widget] = w
      override val columnNames: Seq[String] = Seq("version", "country", "createdAt")
      override val defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.createdAt)
    }

    override def get(id: WidgetId): Future[Option[Widget]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL { select.from(widgets.as(w)).where.eq(w.id, id) }
          .map(widgets.apply(w.resultName))
          .single()
          .apply()
      })
    }

    override def count(sourceId: SourceId): Future[Int] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL { select(sqls.count).from(widgets.as(w)).where.eq(w.sourceId, sourceId) }
          .map(_.int(1))
          .single()
          .apply()
          .getOrElse(0)
      })
    }

    override def list(
      sourceId: SourceId,
      start: Option[Start],
      end: Option[End],
      sort: Option[String],
      order: Option[Order]
    ): Future[Seq[Widget]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          sortOrderQuery(start.orZero, end, sort, order, select.from(widgets.as(w)).where.eq(w.sourceId, sourceId))
        }.map(widgets.apply(w.resultName))
          .list()
          .apply()
      })
    }

    override def persist(widget: Widget): Future[Widget] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL { insert.into(widgets).namedValues(autoNamedValues(widget, widgets.column)) }.update().apply()
      }).map(_ => widget)
    }

  }
}
