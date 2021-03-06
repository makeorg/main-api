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

package org.make.api.technical.crm

import akka.actor.typed.Behavior
import grizzled.slf4j.Logging
import org.make.api.technical.KafkaProducerBehavior
import org.make.core.{DateHelper, MakeSerializable}

import java.time.{Instant, ZoneOffset, ZonedDateTime}

class MailJetEventProducerBehavior extends KafkaProducerBehavior[MailJetEvent, MailJetEventWrapper] with Logging {
  override protected val topicKey: String = MailJetEventProducerBehavior.topicKey
  override protected def wrapEvent(event: MailJetEvent): MailJetEventWrapper = {
    logger.debug(s"Produce MailJetEvent: ${event.toString}")
    MailJetEventWrapper(version = MakeSerializable.V1, id = event.email, date = event.time.map { timestamp =>
      ZonedDateTime.from(Instant.ofEpochMilli(timestamp * 1000).atZone(ZoneOffset.UTC))
    }.getOrElse(DateHelper.now()), event = event)
  }
}

object MailJetEventProducerBehavior {
  def apply(): Behavior[MailJetEvent] = new MailJetEventProducerBehavior().createBehavior(name)

  val name: String = "mailjet-events-producer"
  val topicKey: String = "mailjet-events"
}
