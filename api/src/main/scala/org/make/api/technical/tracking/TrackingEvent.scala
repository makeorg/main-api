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

import java.time.ZonedDateTime

import com.sksamuel.avro4s
import com.sksamuel.avro4s.{DefaultFieldMapper, RecordFormat, SchemaFor}
import org.make.core.{AvroSerializers, DateHelper, EventWrapper, RequestContext}

final case class TrackingEvent(eventProvider: String,
                               eventType: Option[String],
                               eventName: Option[String],
                               eventParameters: Option[Map[String, String]],
                               requestContext: RequestContext,
                               createdAt: ZonedDateTime)

final case class TrackingEventWrapper(version: Int,
                                      id: String,
                                      date: ZonedDateTime,
                                      eventType: String,
                                      event: TrackingEvent)
    extends EventWrapper[TrackingEvent]

object TrackingEventWrapper extends AvroSerializers {
  lazy val schemaFor: SchemaFor[TrackingEventWrapper] = SchemaFor.gen[TrackingEventWrapper]
  implicit lazy val avroDecoder: avro4s.Decoder[TrackingEventWrapper] = avro4s.Decoder.gen[TrackingEventWrapper]
  implicit lazy val avroEncoder: avro4s.Encoder[TrackingEventWrapper] = avro4s.Encoder.gen[TrackingEventWrapper]
  lazy val recordFormat: RecordFormat[TrackingEventWrapper] =
    RecordFormat[TrackingEventWrapper](schemaFor.schema(DefaultFieldMapper))
}

object TrackingEvent {

  def eventfromFront(frontRequest: FrontTrackingRequest, requestContext: RequestContext): TrackingEvent = {
    TrackingEvent(
      eventProvider = "front",
      eventType = Some(frontRequest.eventType),
      eventName = frontRequest.eventName,
      eventParameters = frontRequest.eventParameters,
      requestContext = requestContext,
      createdAt = DateHelper.now()
    )
  }
}
