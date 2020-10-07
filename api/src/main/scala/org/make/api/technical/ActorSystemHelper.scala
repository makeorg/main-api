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
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.util.Timeout

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
}
