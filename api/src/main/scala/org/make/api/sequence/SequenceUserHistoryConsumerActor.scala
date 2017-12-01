package org.make.api.sequence

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.MailJetTemplateConfigurationExtension
import org.make.api.sequence.PublishedSequenceEvent._
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor}
import org.make.api.userhistory._
import shapeless.Poly1

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class SequenceUserHistoryConsumerActor(userHistoryCoordinator: ActorRef)
    extends KafkaConsumerActor[SequenceEventWrapper]
    with ActorEventBusServiceComponent
    with MailJetTemplateConfigurationExtension
    with ActorLogging {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(SequenceProducerActor.topicKey)
  override protected val format: RecordFormat[SequenceEventWrapper] = RecordFormat[SequenceEventWrapper]
  override val groupId = "sequence-user-history"

  implicit val timeout: Timeout = Timeout(3.seconds)

  override def handleMessage(message: SequenceEventWrapper): Future[Unit] = {
    message.event.fold(ToSequenceEvent) match {
      case event: SequenceViewed           => handleSequenceViewed(event)
      case event: SequenceUpdated          => handleSequenceUpdated(event)
      case event: SequenceCreated          => handleSequenceCreated(event)
      case event: SequenceProposalsRemoved => handleSequenceProposalsRemoved(event)
      case event: SequenceProposalsAdded   => handleSequenceProposalsAdded(event)
      case event: SequencePatched          => Future.successful {}
    }
  }

  object ToSequenceEvent extends Poly1 {
    implicit val atSequenceViewed: Case.Aux[SequenceViewed, SequenceViewed] = at(identity)
    implicit val atSequenceUpdated: Case.Aux[SequenceUpdated, SequenceUpdated] = at(identity)
    implicit val atSequenceCreated: Case.Aux[SequenceCreated, SequenceCreated] = at(identity)
    implicit val atSequenceProposalsAdded: Case.Aux[SequenceProposalsAdded, SequenceProposalsAdded] = at(identity)
    implicit val atSequenceProposalsRemoved: Case.Aux[SequenceProposalsRemoved, SequenceProposalsRemoved] = at(identity)
    implicit val atSequencePatched: Case.Aux[SequencePatched, SequencePatched] = at(identity)
  }

  def handleSequenceViewed(event: SequenceViewed): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleSequenceUpdated(event: SequenceUpdated): Future[Unit] = {
    log.debug(s"received $event")
    Future(
      userHistoryCoordinator ? LogUserUpdateSequenceEvent(
        userId = event.userId,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = SequenceUpdated.actionType, arguments = event)
      )
    )
  }

  def handleSequenceCreated(event: SequenceCreated): Future[Unit] = {
    log.debug(s"received $event")
    Future(
      userHistoryCoordinator ? LogUserCreateSequenceEvent(
        userId = event.userId,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = SequenceCreated.actionType, arguments = event)
      )
    )
  }

  def handleSequenceProposalsRemoved(event: SequenceProposalsRemoved): Future[Unit] = {
    log.debug(s"received $event")
    Future(
      userHistoryCoordinator ? LogUserRemoveProposalsSequenceEvent(
        userId = event.userId,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = SequenceProposalsRemoved.actionType, arguments = event)
      )
    )
  }

  def handleSequenceProposalsAdded(event: SequenceProposalsAdded): Future[Unit] = {
    log.debug(s"received $event")
    Future(
      userHistoryCoordinator ? LogUserAddProposalsSequenceEvent(
        userId = event.userId,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = SequenceProposalsAdded.actionType, arguments = event)
      )
    )
  }
}

object SequenceUserHistoryConsumerActor {
  val name: String = "sequence-events-user-history-consumer"
  def props(userHistoryCoordinator: ActorRef): Props =
    Props(new SequenceUserHistoryConsumerActor(userHistoryCoordinator))
}
