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

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.BasicProducerActor

class MailJetProducerActor extends BasicProducerActor[SendEmail, SendEmail] {
  override protected lazy val eventClass: Class[SendEmail] = classOf[SendEmail]
  override protected lazy val format: RecordFormat[SendEmail] = SendEmail.recordFormat
  override protected lazy val schema: SchemaFor[SendEmail] = SendEmail.schemaFor
  override val kafkaTopic: String = kafkaConfiguration.topics(MailJetProducerActor.topicKey)
  override protected def convert(event: SendEmail): SendEmail = event
}

object MailJetProducerActor {
  val name: String = "mailjet-email-producer"
  val props: Props = Props[MailJetProducerActor]()
  val topicKey: String = "emails"
}
