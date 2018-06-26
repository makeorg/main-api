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

package org.make.api.technical

import org.make.api.technical.KafkaConsumerActor.{CheckState, Ready, Waiting}
import org.make.core.Sharded

abstract class BasicProducerActor[Wrapper <: Sharded, Event] extends ProducerActor[Wrapper, Event] {

  def kafkaTopic: String

  override def receive: Receive = {
    case CheckState =>
      if (producer.partitionsFor(kafkaConfiguration.topics(kafkaTopic)).size > 0) {
        sender() ! Ready
      } else {
        sender() ! Waiting
      }
    case event if eventClass.isAssignableFrom(event.getClass) =>
      sendRecord(kafkaTopic, convert(eventClass.cast(event)))
    case other => log.warning("Unknown event {}", other)
  }

  protected def convert(event: Event): Wrapper

  override protected def sendRecord(kafkaTopic: String, record: Wrapper): Unit = {
    sendRecord(kafkaTopic, record.id, record)
  }
}
