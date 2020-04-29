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

import scalikejdbc.PagingSQLBuilder

object PersistentServiceUtils {

  def sortOrderQuery[Persistent, Model](
    start: Int,
    end: Option[Int],
    sort: Option[String],
    order: Option[String],
    query: PagingSQLBuilder[Persistent]
  )(implicit companion: PersistentCompanion[Persistent, Model]): PagingSQLBuilder[Persistent] = {
    val queryOrdered = (sort, order) match {
      case (Some(field), Some("DESC")) if companion.columnNames.contains(field) =>
        query.orderBy(companion.alias.field(field)).desc.offset(start)
      case (Some(field), _) if companion.columnNames.contains(field) =>
        query.orderBy(companion.alias.field(field)).asc.offset(start)
      case (_, _) => query.orderBy(companion.defaultSortColumns.toList: _*).asc.offset(start)
    }

    end match {
      case Some(limit) => queryOrdered.limit(limit)
      case None        => queryOrdered
    }
  }

}
