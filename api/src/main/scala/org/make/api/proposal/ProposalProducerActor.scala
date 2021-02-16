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

package org.make.api.proposal

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.technical.{BasicProducerActor, ProducerActorCompanion}
import org.make.core.DateHelper

class ProposalProducerActor extends BasicProducerActor[ProposalEventWrapper, PublishedProposalEvent] {
  override protected lazy val eventClass: Class[PublishedProposalEvent] = classOf[PublishedProposalEvent]
  override protected lazy val format: RecordFormat[ProposalEventWrapper] = ProposalEventWrapper.recordFormat
  override protected lazy val schema: SchemaFor[ProposalEventWrapper] = ProposalEventWrapper.schemaFor
  override val kafkaTopic: String = kafkaConfiguration.topics(ProposalProducerActor.topicKey)

  override protected def convert(event: PublishedProposalEvent): ProposalEventWrapper = {
    ProposalEventWrapper(
      version = event.version(),
      id = event.id.value,
      date = DateHelper.now(),
      eventType = event.getClass.getSimpleName,
      event = event,
      eventId = event.eventId
    )
  }
}

object ProposalProducerActor extends ProducerActorCompanion {
  val props: Props = Props[ProposalProducerActor]()
  val name: String = "kafka-proposals-event-writer"
  val topicKey = "proposals"
}
