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

import com.sksamuel.avro4s.{FromRecord, RecordFormat, SchemaFor, ToRecord}
import org.make.api.semantic.PredictDuplicateEvent.AnyPredictDuplicateEventEvent
import org.make.core.{AvroSerializers, EventWrapper}
import org.make.core.proposal.ProposalId
import shapeless.{:+:, CNil}

case class PredictDuplicateEvent(proposalId: ProposalId,
                                 predictedDuplicates: Seq[ProposalId],
                                 predictedScores: Seq[Double],
                                 algoLabel: String)

final case class PredictDuplicateEventWrapper(version: Int,
                                              id: String,
                                              date: ZonedDateTime,
                                              eventType: String,
                                              event: AnyPredictDuplicateEventEvent)
    extends EventWrapper

object PredictDuplicateEventWrapper extends AvroSerializers {
  implicit lazy val schemaFor: SchemaFor[PredictDuplicateEventWrapper] = SchemaFor[PredictDuplicateEventWrapper]
  implicit lazy val fromRecord: FromRecord[PredictDuplicateEventWrapper] = FromRecord[PredictDuplicateEventWrapper]
  implicit lazy val toRecord: ToRecord[PredictDuplicateEventWrapper] = ToRecord[PredictDuplicateEventWrapper]
  implicit lazy val recordFormat: RecordFormat[PredictDuplicateEventWrapper] =
    RecordFormat[PredictDuplicateEventWrapper]
}

object PredictDuplicateEvent {
  type AnyPredictDuplicateEventEvent = PredictDuplicateEvent :+: CNil
}
