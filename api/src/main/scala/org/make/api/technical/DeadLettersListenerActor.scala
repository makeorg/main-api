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

import akka.actor.DeadLetter
import akka.actor.typed.Behavior
import akka.actor.typed.eventstream.EventStream.Subscribe
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.SaveSnapshotSuccess

object DeadLettersListenerActor {
  val name: String = "dead-letters-log"

  def apply(): Behavior[DeadLetter] = {
    Behaviors.setup { context =>
      context.system.eventStream ! Subscribe(context.self)

      Behaviors.receiveMessage {
        case DeadLetter(_: SaveSnapshotSuccess, _, _) =>
          Behaviors.same
        case DeadLetter(msg, from, to) =>
          context.log.info(s"[DEADLETTERS] [${from.toString}] -> [${to.toString}]. Message: ${msg.toString}")
          Behaviors.same
      }
    }
  }
}
