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

import akka.actor.{Actor, ActorLogging, DeadLetter, Props}
import akka.persistence.SaveSnapshotSuccess

class DeadLettersListenerActor extends Actor with ActorLogging {

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[DeadLetter])
    ()
  }

  override def receive: Receive = {
    case DeadLetter(_: SaveSnapshotSuccess, _, _) =>
    case DeadLetter(msg, from, to) =>
      log.info("[DEADLETTERS] [{}] -> [{}]. Message: {}", from.toString, to.toString, msg.toString)
  }
}

object DeadLettersListenerActor {
  val props: Props = Props(new DeadLettersListenerActor)
  val name: String = "dead-letters-log"
}
