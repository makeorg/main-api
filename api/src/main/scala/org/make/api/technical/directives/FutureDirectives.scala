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

package org.make.api.technical.directives

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive1, Directives}

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait FutureDirectives extends Directives {
  def provideAsync[T](provider: => Future[T]): Directive1[T] =
    extract(_ => provider).flatMap { fa =>
      onComplete(fa).flatMap {
        case Success(value) => provide(value)
        case Failure(e)     => failWith(e)
      }
    }

  def provideAsyncOrNotFound[T](provider: => Future[Option[T]]): Directive1[T] =
    provideAsync(provider).flatMap {
      case Some(value) => provide(value)
      case None        => complete(StatusCodes.NotFound)
    }
}
