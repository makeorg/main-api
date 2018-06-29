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

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.semantic.PredictDuplicateEvent.AnyPredictDuplicateEventEvent
import org.make.api.technical.BasicProducerActor
import org.make.core.{DateHelper, MakeSerializable}
import shapeless.Coproduct

class SemanticProducerActor extends BasicProducerActor[PredictDuplicateEventWrapper, PredictDuplicateEvent] {
  override protected lazy val eventClass: Class[PredictDuplicateEvent] = classOf[PredictDuplicateEvent]
  override protected lazy val format: RecordFormat[PredictDuplicateEventWrapper] =
    RecordFormat[PredictDuplicateEventWrapper]
  override protected lazy val schema: SchemaFor[PredictDuplicateEventWrapper] = SchemaFor[PredictDuplicateEventWrapper]
  override val kafkaTopic: String = kafkaConfiguration.topics(SemanticProducerActor.topicKey)
  override protected def convert(trackingEvent: PredictDuplicateEvent): PredictDuplicateEventWrapper =
    PredictDuplicateEventWrapper(
      version = MakeSerializable.V1,
      id = trackingEvent.proposalId.value,
      date = DateHelper.now(),
      eventType = trackingEvent.getClass.getSimpleName,
      event = Coproduct[AnyPredictDuplicateEventEvent](trackingEvent)
    )
}

object SemanticProducerActor {
  val name: String = "duplicate-detector-producer"
  val props: Props = Props[SemanticProducerActor]
  val topicKey: String = "duplicates-predicted"
}
