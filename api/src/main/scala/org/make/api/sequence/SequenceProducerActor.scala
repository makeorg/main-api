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

package org.make.api.sequence

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.sequence.PublishedSequenceEvent._
import org.make.api.technical.{BasicProducerActor, ProducerActorCompanion}
import org.make.core.DateHelper

class SequenceProducerActor extends BasicProducerActor[SequenceEventWrapper, PublishedSequenceEvent] {
  override protected lazy val eventClass: Class[PublishedSequenceEvent] = classOf[PublishedSequenceEvent]
  override protected lazy val format: RecordFormat[SequenceEventWrapper] = RecordFormat[SequenceEventWrapper]
  override protected lazy val schema: SchemaFor[SequenceEventWrapper] = SchemaFor[SequenceEventWrapper]
  override val kafkaTopic: String = kafkaConfiguration.topics(SequenceProducerActor.topicKey)

  def convert(event: PublishedSequenceEvent): SequenceEventWrapper = {
    SequenceEventWrapper(
      version = event.version(),
      id = event.id.value,
      date = DateHelper.now(),
      eventType = event.getClass.getSimpleName,
      event = SequenceEventWrapper.wrapEvent(event)
    )
  }
}

object SequenceProducerActor extends ProducerActorCompanion {
  val props: Props = Props[SequenceProducerActor]
  val name: String = "kafka-sequences-event-writer"
  val topicKey = "sequences"
}
