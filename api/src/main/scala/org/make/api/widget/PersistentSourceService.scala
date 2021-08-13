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
import org.make.core.widget.{Source, SourceId}
import scalikejdbc._

import scala.concurrent.Future

trait PersistentSourceService {
  def get(id: SourceId): Future[Option[Source]]
  def findBySource(source: String): Future[Option[Source]]
  def list(
    start: Option[Start],
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    name: Option[String]
  ): Future[Seq[Source]]
  def persist(source: Source): Future[Source]
  def modify(source: Source): Future[Source]
}

trait PersistentSourceServiceComponent {
  def persistentSourceService: PersistentSourceService
}

trait DefaultPersistentSourceServiceComponent extends PersistentSourceServiceComponent {
  self: MakeDBExecutionContextComponent =>

  override def persistentSourceService: PersistentSourceService = new PersistentSourceService with ShortenedNames {

    private val sources = SQLSyntaxSupportFactory[Source]()
    private val s = sources.syntax

    private implicit val companion: PersistentCompanion[Source, Source] = new PersistentCompanion[Source, Source] {
      override def alias: SyntaxProvider[Source] = s
      override def defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.name)
    }

    override def get(id: SourceId): Future[Option[Source]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL { select.from(sources.as(s)).where.eq(s.id, id) }
          .map(sources.apply(s.resultName))
          .single()
          .apply()
      })
    }

    override def findBySource(source: String): Future[Option[Source]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL { select.from(sources.as(s)).where.eq(s.source, source) }
          .map(sources.apply(s.resultName))
          .single()
          .apply()
      })
    }

    override def list(
      start: Option[Start],
      end: Option[End],
      sort: Option[String],
      order: Option[Order],
      name: Option[String]
    ): Future[Seq[Source]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          sortOrderQuery(
            start.orZero,
            end,
            sort,
            order,
            select.from(sources.as(s)).where(name.map(n => sqls.like(sqls"lower(${s.name})", s"%${n.toLowerCase}%")))
          )
        }.map(sources.apply(s.resultName))
          .list()
          .apply()
      })
    }

    override def persist(source: Source): Future[Source] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL { insert.into(sources).namedValues(autoNamedValues(source, sources.column)) }.update().apply()
      }).map(_ => source)
    }

    override def modify(source: Source): Future[Source] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(sources).set(autoNamedValues(source, sources.column, "id", "createdAt")).where.eq(s.id, source.id)
        }.update().apply()
      }).map(_ => source)
    }
  }
}
