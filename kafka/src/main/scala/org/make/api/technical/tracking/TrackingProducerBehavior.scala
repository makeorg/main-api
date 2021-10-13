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

package org.make.api.technical.tracking

import akka.actor.typed.Behavior
import org.make.api.technical.KafkaProducerBehavior
import org.make.core.MakeSerializable

class TrackingProducerBehavior extends KafkaProducerBehavior[TrackingEvent, TrackingEventWrapper] {
  override protected val topicKey: String = TrackingProducerBehavior.topicKey
  override protected def wrapEvent(event: TrackingEvent): TrackingEventWrapper = {
    TrackingEventWrapper(
      version = MakeSerializable.V1,
      id = event.requestContext.sessionId.value,
      date = event.createdAt,
      eventType = "TrackingEvent",
      event = event
    )
  }
}

object TrackingProducerBehavior {
  def apply(): Behavior[TrackingEvent] = new TrackingProducerBehavior().createBehavior(name)
  val name: String = "tracking-events-producer"
  val topicKey: String = "tracking-events"
}
