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

package org.make.api

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.server.{Directive0, Directive1}

import scala.concurrent.duration.FiniteDuration

object Predef {

  implicit class RichScalaDuration(val self: java.time.Duration) extends AnyVal {
    def toScala: FiniteDuration = {
      FiniteDuration(self.toNanos, TimeUnit.NANOSECONDS)
    }
  }

  implicit class RichDirective0(val self: Directive0) extends AnyVal {
    def map[B](f: Unit => B): Directive1[B] = {
      self.tmap(f)
    }

    def flatMap[B](f: Unit => Directive1[B]): Directive1[B] = {
      self.tflatMap(f)
    }
  }
}
