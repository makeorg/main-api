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

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.BasicProducerActor
import org.make.api.technical.tracking.TrackingEvent.AnyTrackingEvent
import org.make.core.MakeSerializable
import shapeless.Coproduct

class TrackingProducerActor extends BasicProducerActor[TrackingEventWrapper, TrackingEvent] {
  override protected lazy val eventClass: Class[TrackingEvent] = classOf[TrackingEvent]
  override protected lazy val format: RecordFormat[TrackingEventWrapper] = TrackingEventWrapper.recordFormat
  override protected lazy val schema: SchemaFor[TrackingEventWrapper] = TrackingEventWrapper.schemaFor
  override val kafkaTopic: String = kafkaConfiguration.topics(TrackingProducerActor.topicKey)
  override protected def convert(event: TrackingEvent): TrackingEventWrapper = {
    TrackingEventWrapper(
      version = MakeSerializable.V1,
      id = event.requestContext.sessionId.value,
      date = event.createdAt,
      eventType = "TrackingEvent",
      event = Coproduct[AnyTrackingEvent](event)
    )
  }
}

object TrackingProducerActor {
  val name: String = "tracking-event-producer"
  val props: Props = Props[TrackingProducerActor]
  val topicKey: String = "tracking-events"
}
