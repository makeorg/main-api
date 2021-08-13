/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

import cats.implicits._
import org.make.api.technical.IdGeneratorComponent
import org.make.core.{DateHelperComponent, Order}
import org.make.core.technical.Pagination.{End, Start}
import org.make.core.user.UserId
import org.make.core.widget.{Source, SourceId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SourceService {
  def get(id: SourceId): Future[Option[Source]]
  def findBySource(source: String): Future[Option[Source]]
  def list(
    start: Option[Start],
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    name: Option[String]
  ): Future[Seq[Source]]
  def create(name: String, source: String, userId: UserId): Future[Source]
  def update(id: SourceId, name: String, source: String, userId: UserId): Future[Option[Source]]
}

trait SourceServiceComponent {
  def sourceService: SourceService
}

trait DefaultSourceServiceComponent extends SourceServiceComponent {
  self: DateHelperComponent with IdGeneratorComponent with PersistentSourceServiceComponent =>

  override def sourceService: SourceService = new SourceService {

    override def get(id: SourceId): Future[Option[Source]] = persistentSourceService.get(id)

    override def findBySource(source: String): Future[Option[Source]] = persistentSourceService.findBySource(source)

    override def list(
      start: Option[Start],
      end: Option[End],
      sort: Option[String],
      order: Option[Order],
      name: Option[String]
    ): Future[Seq[Source]] = persistentSourceService.list(start, end, sort, order, name)

    override def create(name: String, source: String, userId: UserId): Future[Source] =
      persistentSourceService.persist(
        Source(
          id = idGenerator.nextSourceId(),
          name = name,
          source = source,
          createdAt = dateHelper.now(),
          updatedAt = dateHelper.now(),
          userId = userId
        )
      )

    override def update(id: SourceId, name: String, source: String, userId: UserId): Future[Option[Source]] = {
      get(id).flatMap(
        _.traverse(
          existing =>
            persistentSourceService
              .modify(existing.copy(name = name, source = source, updatedAt = dateHelper.now(), userId = userId))
        )
      )
    }

  }
}
