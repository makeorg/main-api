/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future, Promise}

object BetterLoggingActors {

  implicit class BetterLoggingTypedActorRef[Req](val ref: ActorRef[Req]) extends AnyVal {
    def ??[Res](
      replyTo: ActorRef[Res] => Req
    )(implicit ec: ExecutionContext, system: ActorSystem[_], timeout: Timeout): Future[Res] = {
      val message = Promise[Req]()
      ref.ask { sender: ActorRef[Res] =>
        val value = replyTo(sender)
        message.success(value)
        value
      }.recoverWith {
        case e: TimeoutException => message.future.flatMap(m => Future.failed(ActorTimeoutException(m, e)))
        case other               => Future.failed(other)
      }
    }
  }

}
