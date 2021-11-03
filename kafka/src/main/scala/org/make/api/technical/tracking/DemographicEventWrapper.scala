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
import org.make.core.question.QuestionId

import java.time.ZonedDateTime

final case class DemographicEventWrapper(
  version: Int,
  id: String,
  date: ZonedDateTime,
  eventType: String,
  event: DemographicEvent
) extends EventWrapper[DemographicEvent]

object DemographicEventWrapper extends AvroSerializers {
  // Force questionId to be encoded as a String in the Avro schema
  implicit lazy val questionIdSchemaFor: SchemaFor[QuestionId] = (_: FieldMapper) => Schema.create(Schema.Type.STRING)
  implicit lazy val questionIdAvroEncoder: avro4s.Encoder[QuestionId] = avro4s.Encoder.StringEncoder.comap(_.value)

  implicit lazy val schemaFor: SchemaFor[DemographicEventWrapper] = SchemaFor.gen[DemographicEventWrapper]
  implicit lazy val avroEncoder: avro4s.Encoder[DemographicEventWrapper] = avro4s.Encoder.gen[DemographicEventWrapper]
}
