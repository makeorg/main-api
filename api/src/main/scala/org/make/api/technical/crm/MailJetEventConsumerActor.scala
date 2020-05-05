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

import java.time.ZonedDateTime

import akka.actor.{ActorLogging, Props}
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.technical.KafkaConsumerActor
import org.make.api.user.UserService
import org.make.core.user.MailingErrorLog
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class MailJetEventConsumerActor(userService: UserService)
    extends KafkaConsumerActor[MailJetEventWrapper]
    with KafkaConfigurationExtension
    with ActorLogging {
  override protected val kafkaTopic: String = MailJetCallbackProducerActor.topicKey
  override protected val format: RecordFormat[MailJetEventWrapper] = MailJetEventWrapper.recordFormat
  override def groupId: String = "mailJet-event-consumer"

  override def handleMessage(message: MailJetEventWrapper): Future[_] = {
    message.event match {
      case event: MailJetBounceEvent      => handleBounceEvent(event, message.date)
      case event: MailJetBlockedEvent     => doNothing(event)
      case event: MailJetSpamEvent        => handleSpamEvent(event)
      case event: MailJetUnsubscribeEvent => handleUnsubscribeEvent(event)
      case event                          => doNothing(event)
    }
  }

  def handleBounceEvent(event: MailJetBounceEvent, date: ZonedDateTime): Future[Boolean] = {
    for {
      resultUpdateBounce <- userService.updateIsHardBounce(email = event.email, isHardBounce = event.hardBounce)
      resultUpdateError  <- registerMailingError(email = event.email, maybeError = event.error, date = date)
    } yield resultUpdateBounce && resultUpdateError
  }

  def handleSpamEvent(event: MailJetSpamEvent): Future[Boolean] = {
    userService.updateOptInNewsletter(email = event.email, optInNewsletter = false)
  }

  def handleUnsubscribeEvent(event: MailJetUnsubscribeEvent): Future[Boolean] = {
    userService.updateOptInNewsletter(email = event.email, optInNewsletter = false)
  }

  private def registerMailingError(
    email: String,
    maybeError: Option[MailJetError],
    date: ZonedDateTime
  ): Future[Boolean] = {
    maybeError match {
      case None => Future.successful(false)
      case Some(error) =>
        userService.updateLastMailingError(
          email = email,
          lastMailingError = Some(MailingErrorLog(error = error.name, date = date))
        )
    }
  }
}

object MailJetEventConsumerActor {
  def props(userService: UserService): Props = Props(new MailJetEventConsumerActor(userService))
  val name: String = "mailJet-event-consumer"
}
