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

package org.make.core
package common.indexed

import com.sksamuel.elastic4s.searches.sort.SortOrder
import org.make.core.SprayJsonFormatters._
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

final case class Sort(field: Option[String], mode: Option[SortOrder])

object Sort {

  def parse(sort: Option[String], order: Option[Order]): Option[Sort] = (sort, order) match {
    case (None, None) => None
    case _            => Some(Sort(sort, order.map(_.sortOrder)))
  }

  implicit val sortFormatted: RootJsonFormat[Sort] =
    DefaultJsonProtocol.jsonFormat2(Sort.apply)
}
