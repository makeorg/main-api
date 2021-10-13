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

import com.sksamuel.avro4s
import com.sksamuel.avro4s.{FieldMapper, SchemaFor}
import org.apache.avro.Schema
import org.make.core.{AvroSerializers, EventWrapper}
import org.make.core.session.SessionId

import java.time.ZonedDateTime

final case class ConcertationEventWrapper(
  id: String,
  version: Int,
  date: ZonedDateTime,
  eventType: String,
  event: ConcertationEvent
) extends EventWrapper[ConcertationEvent]

object ConcertationEventWrapper extends AvroSerializers {
  implicit lazy val sessionIdSchemaFor: SchemaFor[SessionId] = (_: FieldMapper) => Schema.create(Schema.Type.STRING)
  implicit lazy val sessionIdAvroEncoder: avro4s.Encoder[SessionId] = avro4s.Encoder.StringEncoder.comap(_.value)
  implicit lazy val sectionIdSchemaFor: SchemaFor[SectionId] = (_: FieldMapper) => Schema.create(Schema.Type.STRING)
  implicit lazy val sectionIdAvroEncoder: avro4s.Encoder[SectionId] = avro4s.Encoder.StringEncoder.comap(_.value)

  implicit lazy val schemaFor: SchemaFor[ConcertationEventWrapper] = SchemaFor.gen[ConcertationEventWrapper]
  implicit lazy val avroEncoder: avro4s.Encoder[ConcertationEventWrapper] = avro4s.Encoder.gen[ConcertationEventWrapper]
}
