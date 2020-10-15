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

import java.time.{Instant, ZoneOffset, ZonedDateTime}

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import com.typesafe.scalalogging.StrictLogging
import org.make.api.technical.BasicProducerActor
import org.make.core.{DateHelper, MakeSerializable}

class MailJetCallbackProducerActor extends BasicProducerActor[MailJetEventWrapper, MailJetEvent] with StrictLogging {
  override protected lazy val eventClass: Class[MailJetEvent] = classOf[MailJetEvent]
  override protected lazy val format: RecordFormat[MailJetEventWrapper] = MailJetEventWrapper.recordFormat
  override protected lazy val schema: SchemaFor[MailJetEventWrapper] = MailJetEventWrapper.schemaFor
  override val kafkaTopic: String = kafkaConfiguration.topics(MailJetCallbackProducerActor.topicKey)
  override protected def convert(event: MailJetEvent): MailJetEventWrapper = {
    logger.debug(s"Produce MailJetEvent: ${event.toString}")
    MailJetEventWrapper(version = MakeSerializable.V1, id = event.email, date = event.time.map { timestamp =>
      ZonedDateTime.from(Instant.ofEpochMilli(timestamp * 1000).atZone(ZoneOffset.UTC))
    }.getOrElse(DateHelper.now()), event = event)
  }
}

object MailJetCallbackProducerActor {
  val name: String = "mailjet-callback-event-producer"
  val props: Props = Props[MailJetCallbackProducerActor]()
  val topicKey: String = "mailjet-events"
}
