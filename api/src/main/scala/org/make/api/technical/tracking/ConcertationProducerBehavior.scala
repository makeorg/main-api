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
import org.make.core.{DateHelper, MakeSerializable}

import java.util.UUID

class ConcertationProducerBehavior extends KafkaProducerBehavior[ConcertationEvent, ConcertationEventWrapper] {
  override protected val topicKey: String = ConcertationProducerBehavior.topicKey
  override protected def wrapEvent(event: ConcertationEvent): ConcertationEventWrapper = {
    ConcertationEventWrapper(
      version = MakeSerializable.V1,
      id = UUID.randomUUID().toString,
      date = DateHelper.now(),
      eventType = "ConcertationTrackingEvent",
      event = event
    )
  }
}

object ConcertationProducerBehavior {
  def apply(): Behavior[ConcertationEvent] = new ConcertationProducerBehavior().createBehavior(name)
  val name: String = "concertation-producer"
  val topicKey: String = "concertation"
}
