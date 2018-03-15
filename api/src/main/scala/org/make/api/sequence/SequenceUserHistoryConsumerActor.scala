package org.make.api.sequence

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.sequence.PublishedSequenceEvent._
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor, TimeSettings}
import org.make.api.userhistory._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SequenceUserHistoryConsumerActor(userHistoryCoordinator: ActorRef)
    extends KafkaConsumerActor[SequenceEventWrapper]
    with ActorEventBusServiceComponent
    with ActorLogging {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(SequenceProducerActor.topicKey)
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
    (
      userHistoryCoordinator ? LogUserUpdateSequenceEvent(
        userId = event.userId,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = SequenceUpdated.actionType, arguments = event)
      )
    ).map(_ => {})
  }

  def handleSequenceCreated(event: SequenceCreated): Future[Unit] = {
    log.debug(s"received $event")
    (
      userHistoryCoordinator ? LogUserCreateSequenceEvent(
        userId = event.userId,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = SequenceCreated.actionType, arguments = event)
      )
    ).map(_ => {})
  }

  def handleSequenceProposalsRemoved(event: SequenceProposalsRemoved): Future[Unit] = {
    log.debug(s"received $event")
    (
      userHistoryCoordinator ? LogUserRemoveProposalsSequenceEvent(
        userId = event.userId,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = SequenceProposalsRemoved.actionType, arguments = event)
      )
    ).map(_ => {})
  }

  def handleSequenceProposalsAdded(event: SequenceProposalsAdded): Future[Unit] = {
    log.debug(s"received $event")
    (
      userHistoryCoordinator ? LogUserAddProposalsSequenceEvent(
        userId = event.userId,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = SequenceProposalsAdded.actionType, arguments = event)
      )
    ).map(_ => {})
  }
}

object SequenceUserHistoryConsumerActor {
  val name: String = "sequence-events-user-history-consumer"
  def props(userHistoryCoordinator: ActorRef): Props =
    Props(new SequenceUserHistoryConsumerActor(userHistoryCoordinator))
}
