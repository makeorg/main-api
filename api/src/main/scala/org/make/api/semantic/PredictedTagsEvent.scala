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

package org.make.api.semantic
import java.time.ZonedDateTime
import com.sksamuel.avro4s._
import org.make.core.proposal.ProposalId
import org.make.core.tag.TagId
import org.make.core.{AvroSerializers, EventWrapper}

sealed trait PredictedTagsEvents

final case class PredictedTagsEvent(
  proposalId: ProposalId,
  predictedTags: Seq[TagId],
  selectedTags: Seq[TagId],
  modelName: String
) extends PredictedTagsEvents

final case class PredictionsEventWrapper(
  version: Int,
  id: String,
  date: ZonedDateTime,
  eventType: String,
  event: PredictedTagsEvents
) extends EventWrapper[PredictedTagsEvents]

object PredictionsEventWrapper extends AvroSerializers {
  lazy val schemaFor: SchemaFor[PredictionsEventWrapper] = SchemaFor.gen[PredictionsEventWrapper]
  implicit lazy val avroDecoder: Decoder[PredictionsEventWrapper] = Decoder.gen[PredictionsEventWrapper]
  implicit lazy val avroEncoder: Encoder[PredictionsEventWrapper] = Encoder.gen[PredictionsEventWrapper]
  lazy val recordFormat: RecordFormat[PredictionsEventWrapper] =
    RecordFormat[PredictionsEventWrapper](schemaFor.schema(DefaultFieldMapper))

}
