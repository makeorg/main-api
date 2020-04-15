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

import scalikejdbc.WrappedResultSet
import scalikejdbc.interpolation.SQLSyntax

object PersistentServiceUtils {

  def sortOrderQuery[T](start: Int,
                        end: Option[Int],
                        sort: Option[String],
                        order: Option[String],
                        query: scalikejdbc.PagingSQLBuilder[WrappedResultSet],
                        columns: Seq[String],
                        alias: scalikejdbc.SyntaxProvider[T],
                        defaultSort: SQLSyntax): scalikejdbc.PagingSQLBuilder[WrappedResultSet] = {
    val queryOrdered = (sort, order) match {
      case (Some(field), Some("DESC")) if columns.contains(field) =>
        query.orderBy(alias.field(field)).desc.offset(start)
      case (Some(field), _) if columns.contains(field) => query.orderBy(alias.field(field)).asc.offset(start)
      case (_, _)                                      => query.orderBy(defaultSort).asc.offset(start)
    }

    end match {
      case Some(limit) => queryOrdered.limit(limit)
      case None        => queryOrdered
    }
  }

}
