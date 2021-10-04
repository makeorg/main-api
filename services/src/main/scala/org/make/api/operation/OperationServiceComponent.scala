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

package org.make.api.operation

import java.time.LocalDate
import org.make.core.Order
import org.make.core.operation._
import org.make.core.reference.Country
import org.make.core.user.UserId

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait OperationServiceComponent {
  def operationService: OperationService
}

trait OperationService {
  def find(
    slug: Option[String] = None,
    country: Option[Country] = None,
    maybeSource: Option[String],
    openAt: Option[LocalDate] = None
  ): Future[Seq[Operation]]
  def findSimple(
    start: Start = Start.zero,
    end: Option[End] = None,
    sort: Option[String] = None,
    order: Option[Order] = None,
    slug: Option[String] = None,
    operationKinds: Option[Seq[OperationKind]] = None
  ): Future[Seq[SimpleOperation]]
  def findOne(operationId: OperationId): Future[Option[Operation]]
  def findOneSimple(operationId: OperationId): Future[Option[SimpleOperation]]
  def findOneBySlug(slug: String): Future[Option[Operation]]
  def create(userId: UserId, slug: String, operationKind: OperationKind): Future[OperationId]
  def update(
    operationId: OperationId,
    userId: UserId,
    slug: Option[String] = None,
    status: Option[OperationStatus] = None,
    operationKind: Option[OperationKind] = None
  ): Future[Option[OperationId]]
  def count(slug: Option[String], operationKinds: Option[Seq[OperationKind]]): Future[Int]
}
