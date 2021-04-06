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

package org.make.api.user

import akka.actor.typed.Behavior
import org.make.api.technical.KafkaProducerBehavior
import org.make.api.userhistory.{UserEvent, UserEventWrapper}

class UserProducerBehavior extends KafkaProducerBehavior[UserEvent, UserEventWrapper] {
  override protected val topicKey: String = UserProducerBehavior.topicKey
  override protected def wrapEvent(event: UserEvent): UserEventWrapper = {
    UserEventWrapper(
      version = event.version(),
      id = event.userId.value,
      date = event.eventDate,
      eventType = event.getClass.getSimpleName,
      event = event,
      eventId = event.eventId
    )
  }
}

object UserProducerBehavior {
  def apply(): Behavior[UserEvent] = new UserProducerBehavior().createBehavior(name)
  val name: String = "users-producer"
  val topicKey: String = "users"
}
