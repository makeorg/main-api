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

import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

package object migrations extends StrictLogging {

  def retryableFuture[T](future: => Future[T],
                         times: Int = 3)(implicit executionContext: ExecutionContext): Future[T] = {
    future.recoverWith {
      case _ if times > 0 => retryableFuture(future, times - 1)
      case error          => Future.failed(error)
    }
  }

  def sequentially[T](
    objects: Seq[T]
  )(toFuture: T => Future[Unit])(implicit executionContext: ExecutionContext): Future[Unit] = {
    var result = Future.successful {}
    objects.foreach { current =>
      result = result.flatMap(_ => toFuture(current)).recoverWith {
        case e =>
          logger.warn(s"Error with object ${current.toString}", e)
          Future.successful {}
      }
    }
    result
  }

}
