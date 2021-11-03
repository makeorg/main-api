/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.userhistory

import com.sksamuel.avro4s
import com.sksamuel.avro4s.SchemaFor
import org.make.core.{AvroSerializers, EventId, EventWrapper, WithEventId}

import java.time.ZonedDateTime

final case class UserEventWrapper(
  version: Int,
  id: String,
  date: ZonedDateTime,
  eventType: String,
  event: UserEvent,
  eventId: Option[EventId] = None
) extends EventWrapper[UserEvent]
    with WithEventId

object UserEventWrapper extends AvroSerializers {
  implicit lazy val schemaFor: SchemaFor[UserEventWrapper] = SchemaFor.gen[UserEventWrapper]
  implicit lazy val avroDecoder: avro4s.Decoder[UserEventWrapper] = avro4s.Decoder.gen[UserEventWrapper]
  implicit lazy val avroEncoder: avro4s.Encoder[UserEventWrapper] = avro4s.Encoder.gen[UserEventWrapper]
}
