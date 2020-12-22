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

package org.make.core.technical

// It is currently NOT possible to use refine here because we can not mock a refined value.
trait Pagination { val value: Int }

object Pagination {

  final case class Start(value: Int) extends Pagination
  object Start {
    def zero: Start = Start(0)
  }

  final case class End(value: Int) extends Pagination

  final case class Limit(value: Int) extends Pagination {
    def toEnd(start: Start): End = End(value + start.value)
  }

  implicit class RichOptionStart(val self: Option[Start]) extends AnyVal {
    def orZero: Start = self.getOrElse(Start.zero)
  }
}
