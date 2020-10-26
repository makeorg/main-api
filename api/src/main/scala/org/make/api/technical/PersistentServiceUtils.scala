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

package org.make.api.technical

import org.make.core.Order
import scalikejdbc.PagingSQLBuilder
import org.make.core.technical.Pagination._

object PersistentServiceUtils {

  def sortOrderQuery[Persistent, Model](
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    query: PagingSQLBuilder[Persistent]
  )(implicit companion: PersistentCompanion[Persistent, Model]): PagingSQLBuilder[Persistent] = {
    val queryOrdered = (sort, order) match {
      case (Some(field), Some(Order.desc)) if companion.columnNames.contains(field) =>
        query.orderBy(companion.alias.field(field)).desc.offset(start.value)
      case (Some(field), _) if companion.columnNames.contains(field) =>
        query.orderBy(companion.alias.field(field)).asc.offset(start.value)
      case (_, _) => query.orderBy(companion.defaultSortColumns.toList: _*).asc.offset(start.value)
    }

    end match {
      case Some(End(value)) => queryOrdered.limit(value - start.value)
      case None             => queryOrdered
    }
  }

}
