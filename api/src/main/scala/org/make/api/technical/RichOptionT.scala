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

import cats.data.OptionT
import cats.implicits._

import scala.concurrent.{ExecutionContext, Future}

object RichOptionT {

  implicit class RichOptionT[T](val self: OptionT[Future, T]) extends AnyVal {
    def orFail(message: String)(implicit executionContext: ExecutionContext): OptionT[Future, T] = {
      self.orElseF(Future.failed[Option[T]](ValueNotFoundException(message)))
    }
  }

  final case class ValueNotFoundException(message: String) extends Exception(message)
}
