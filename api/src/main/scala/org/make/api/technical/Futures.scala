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

import akka.pattern.AskTimeoutException

import scala.concurrent.{ExecutionContext, Future}

object Futures {

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def retry[T](future: () => Future[T], shouldRetry: Throwable => Boolean, times: Int = 3)(
    implicit executionContext: ExecutionContext
  ): Future[T] = {
    future().recoverWith { error =>
      if (shouldRetry(error) && times > 0) {
        retry(future, shouldRetry, times - 1)
      } else {
        Future.failed(error)
      }
    }
  }

  def retryOnAskTimeout[T](future: () => Future[T], times: Int = 3)(
    implicit executionContext: ExecutionContext
  ): Future[T] = {
    retry(future, {
      case _: AskTimeoutException => true
      case _                      => false
    }, times)
  }

  implicit class RichFutures[T](val self: Future[T]) extends AnyVal {
    def withoutFailure(implicit executionContext: ExecutionContext): Future[Either[Throwable, T]] = {
      self.map(Right(_)).recoverWith { case e => Future.successful(Left(e)) }
    }

    def toUnit(implicit executionContext: ExecutionContext): Future[Unit] = {
      self.map(_ => ())
    }
  }

  implicit class Fallback[T](val f: () => Future[Option[Option[T]]]) extends AnyVal {
    def or(fallback: () => Future[Option[T]])(implicit executionContext: ExecutionContext): () => Future[Option[T]] =
      () =>
        f().flatMap {
          case Some(t @ Some(_)) => Future.successful(t)
          case _                 => fallback()
        }
  }

}
