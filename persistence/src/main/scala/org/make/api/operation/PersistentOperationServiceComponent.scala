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

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait PersistentOperationServiceComponent {
  def persistentOperationService: PersistentOperationService
}

trait PersistentOperationService {
  def find(
    slug: Option[String] = None,
    country: Option[Country] = None,
    openAt: Option[LocalDate] = None
  ): Future[Seq[Operation]]
  def findSimple(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    slug: Option[String] = None,
    operationKinds: Option[Seq[OperationKind]]
  ): Future[Seq[SimpleOperation]]
  def getById(operationId: OperationId): Future[Option[Operation]]
  def getSimpleById(operationId: OperationId): Future[Option[SimpleOperation]]
  def getBySlug(slug: String): Future[Option[Operation]]
  def persist(operation: SimpleOperation): Future[SimpleOperation]
  def modify(operation: SimpleOperation): Future[SimpleOperation]
  def addActionToOperation(action: OperationAction, operationId: OperationId): Future[Boolean]
  def count(slug: Option[String] = None, operationKinds: Option[Seq[OperationKind]]): Future[Int]
}
