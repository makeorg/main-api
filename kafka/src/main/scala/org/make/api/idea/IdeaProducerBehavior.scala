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

package org.make.api.idea

import akka.actor.typed.Behavior
import org.make.api.technical.KafkaProducerBehavior

class IdeaProducerBehavior extends KafkaProducerBehavior[IdeaEvent, IdeaEventWrapper] {
  override protected val topicKey: String = IdeaProducerBehavior.topicKey
  override protected def wrapEvent(event: IdeaEvent): IdeaEventWrapper = {
    IdeaEventWrapper(
      version = event.version(),
      id = event.ideaId.value,
      date = event.eventDate,
      eventType = event.getClass.getSimpleName,
      event = event
    )
  }
}

object IdeaProducerBehavior {
  def apply(): Behavior[IdeaEvent] = new IdeaProducerBehavior().createBehavior(name)
  val name: String = "idea-producer"
  val topicKey: String = "ideas"
}
