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

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, BackoffSupervisorStrategy, Behavior, Scheduler, SupervisorStrategy}
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

object ActorSystemHelper {
  implicit class RichActorSystem(val self: ActorSystem[_]) extends AnyVal {
    import self.executionContext

    def findRefByKey[T](key: ServiceKey[T])(implicit timeout: Timeout, scheduler: Scheduler): Future[ActorRef[T]] = {
      (self.receptionist ? Receptionist.Find(key)).flatMap {
        case key.Listing(services) =>
          services.find(_.path.address.hasLocalScope) match {
            case Some(ref) => Future.successful(ref)
            case None =>
              Future.failed(new IllegalStateException(s"Receptionist unable to find actor ref for key: $key"))
          }
      }

    }
  }

  private val maxRestarts = 50
  val DefaultFallbackStrategy: BackoffSupervisorStrategy =
    SupervisorStrategy
      .restartWithBackoff(minBackoff = 3.seconds, maxBackoff = 30.seconds, randomFactor = 0.2)
      .withMaxRestarts(maxRestarts)

  def superviseWithBackoff[T](behavior: Behavior[T]): Behavior[T] = {
    Behaviors.supervise(behavior).onFailure(DefaultFallbackStrategy)
  }

  implicit def contextToSystem(implicit context: ActorContext[_]): ActorSystem[_] = context.system
}
