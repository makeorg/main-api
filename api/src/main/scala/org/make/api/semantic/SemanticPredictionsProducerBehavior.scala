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

import akka.actor.typed.Behavior
import org.make.api.technical.KafkaProducerBehavior
import org.make.core.{DateHelper, MakeSerializable}

class SemanticPredictionsProducerBehavior extends KafkaProducerBehavior[PredictedTagsEvent, PredictionsEventWrapper] {
  override protected val topicKey: String = SemanticPredictionsProducerBehavior.topicKey
  override protected def wrapEvent(trackingEvent: PredictedTagsEvent): PredictionsEventWrapper =
    PredictionsEventWrapper(
      version = MakeSerializable.V1,
      id = trackingEvent.proposalId.value,
      date = DateHelper.now(),
      eventType = trackingEvent.getClass.getSimpleName,
      event = trackingEvent
    )
}

object SemanticPredictionsProducerBehavior {
  def apply(): Behavior[PredictedTagsEvent] = new SemanticPredictionsProducerBehavior().createBehavior(name)
  val name: String = "predictions-producer"
  val topicKey: String = "predictions"
}
