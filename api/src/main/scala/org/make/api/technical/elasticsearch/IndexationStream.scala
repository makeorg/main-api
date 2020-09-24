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

package org.make.api.technical.elasticsearch

import akka.NotUsed
import akka.stream.scaladsl.Flow
import scala.concurrent.duration.DurationInt

trait IndexationStream {
  //TODO: Load these values from conf
  val parallelism = 5
  val singleAsync = 1

  def filterIsDefined[T]: Flow[Option[T], T, NotUsed] = Flow[Option[T]].collect { case Some(t) => t }
  def filterIsEmpty[T](item: T): Flow[Option[T], T, NotUsed] = Flow[Option[T]].filter(_.isEmpty).map(_ => item)
  def grouped[T]: Flow[T, Seq[T], NotUsed] = Flow[T].groupedWithin(10, 20.milliseconds)

}
