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
import org.make.api.technical.KafkaConsumerBehavior

import scala.concurrent.Future

class MailJetConsumerBehavior(crmService: CrmService) extends KafkaConsumerBehavior[SendEmail] {

  override protected val topicKey: String = MailJetProducerBehavior.topicKey

  override def handleMessage(message: SendEmail): Future[_] = {
    crmService.sendEmail(message)
  }

  override val groupId = "send-email-consumer"
}

object MailJetConsumerBehavior {
  def apply(crmService: CrmService): Behavior[KafkaConsumerBehavior.Protocol] =
    new MailJetConsumerBehavior(crmService).createBehavior(name)
  val name: String = "send-email-consumer"

}
