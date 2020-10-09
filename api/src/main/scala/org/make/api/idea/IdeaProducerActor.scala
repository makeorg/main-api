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

package org.make.api.idea

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.{BasicProducerActor, ProducerActorCompanion}

class IdeaProducerActor extends BasicProducerActor[IdeaEventWrapper, IdeaEvent] {
  override protected lazy val eventClass: Class[IdeaEvent] = classOf[IdeaEvent]
  override protected lazy val format: RecordFormat[IdeaEventWrapper] = IdeaEventWrapper.recordFormat
  override protected lazy val schema: SchemaFor[IdeaEventWrapper] = IdeaEventWrapper.schemaFor
  override val kafkaTopic: String = kafkaConfiguration.topics(IdeaProducerActor.topicKey)

  override protected def convert(event: IdeaEvent): IdeaEventWrapper = {
    IdeaEventWrapper(
      version = event.version(),
      id = event.ideaId.value,
      date = event.eventDate,
      eventType = event.getClass.getSimpleName,
      event = event
    )
  }
}

object IdeaProducerActor extends ProducerActorCompanion {
  val props: Props = Props[IdeaProducerActor]()
  override val name: String = "kafka-idea-event-writer"
  override val topicKey: String = "ideas"
}
