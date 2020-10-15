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
import org.make.api.technical.BasicProducerActor
import org.make.core.{DateHelper, MakeSerializable}

class SemanticPredictionsProducerActor extends BasicProducerActor[PredictionsEventWrapper, PredictedTagsEvent] {
  override protected lazy val eventClass: Class[PredictedTagsEvent] = classOf[PredictedTagsEvent]
  override protected lazy val format: RecordFormat[PredictionsEventWrapper] =
    PredictionsEventWrapper.recordFormat
  override protected lazy val schema: SchemaFor[PredictionsEventWrapper] = PredictionsEventWrapper.schemaFor
  override val kafkaTopic: String = kafkaConfiguration.topics(SemanticPredictionsProducerActor.topicKey)
  override protected def convert(trackingEvent: PredictedTagsEvent): PredictionsEventWrapper =
    PredictionsEventWrapper(
      version = MakeSerializable.V1,
      id = trackingEvent.proposalId.value,
      date = DateHelper.now(),
      eventType = trackingEvent.getClass.getSimpleName,
      event = trackingEvent
    )
}
object SemanticPredictionsProducerActor {
  val name: String = "predictions-producer"
  val props: Props = Props[SemanticPredictionsProducerActor]()
  val topicKey: String = "predictions"
}
