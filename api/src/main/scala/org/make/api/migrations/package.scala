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
