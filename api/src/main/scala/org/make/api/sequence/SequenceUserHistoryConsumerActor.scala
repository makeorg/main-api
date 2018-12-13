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

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.sequence.PublishedSequenceEvent._
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor, TimeSettings}
import org.make.api.userhistory._

import scala.concurrent.Future

class SequenceUserHistoryConsumerActor(userHistoryCoordinator: ActorRef)
    extends KafkaConsumerActor[SequenceEventWrapper]
    with ActorEventBusServiceComponent
    with ActorLogging {

  override protected lazy val kafkaTopic: String = SequenceProducerActor.topicKey
  override protected val format: RecordFormat[SequenceEventWrapper] = RecordFormat[SequenceEventWrapper]
  override val groupId = "sequence-user-history"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: SequenceEventWrapper): Future[Unit] = {
    message.event.fold(ToSequenceEvent) match {
      case event: SequenceViewed           => doNothing(event)
      case event: SequenceUpdated          => handleSequenceUpdated(event)
      case event: SequenceCreated          => handleSequenceCreated(event)
      case event: SequenceProposalsRemoved => handleSequenceProposalsRemoved(event)
      case event: SequenceProposalsAdded   => handleSequenceProposalsAdded(event)
      case event: SequencePatched          => doNothing(event)
    }
  }

  def handleSequenceUpdated(event: SequenceUpdated): Future[Unit] = {
    log.debug(s"received $event")
    userHistoryCoordinator ! LogUserUpdateSequenceEvent(
      userId = event.userId,
      requestContext = event.requestContext,
      action = UserAction(date = event.eventDate, actionType = SequenceUpdated.actionType, arguments = event)
    )
    Future.successful {}
  }

  def handleSequenceCreated(event: SequenceCreated): Future[Unit] = {
    log.debug(s"received $event")
    userHistoryCoordinator ! LogUserCreateSequenceEvent(
      userId = event.userId,
      requestContext = event.requestContext,
      action = UserAction(date = event.eventDate, actionType = SequenceCreated.actionType, arguments = event)
    )
    Future.successful {}
  }

  def handleSequenceProposalsRemoved(event: SequenceProposalsRemoved): Future[Unit] = {
    log.debug(s"received $event")
    userHistoryCoordinator ! LogUserRemoveProposalsSequenceEvent(
      userId = event.userId,
      requestContext = event.requestContext,
      action = UserAction(date = event.eventDate, actionType = SequenceProposalsRemoved.actionType, arguments = event)
    )
    Future.successful {}
  }

  def handleSequenceProposalsAdded(event: SequenceProposalsAdded): Future[Unit] = {
    log.debug(s"received $event")
    userHistoryCoordinator ! LogUserAddProposalsSequenceEvent(
      userId = event.userId,
      requestContext = event.requestContext,
      action = UserAction(date = event.eventDate, actionType = SequenceProposalsAdded.actionType, arguments = event)
    )
    Future.successful {}
  }
}

object SequenceUserHistoryConsumerActor {
  val name: String = "sequence-events-user-history-consumer"
  def props(userHistoryCoordinator: ActorRef): Props =
    Props(new SequenceUserHistoryConsumerActor(userHistoryCoordinator))
}
