package org.make.api.proposal

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.MailJetTemplateConfigurationExtension
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor}
import org.make.core.proposal.ProposalEvent._
import org.make.core.user._
import shapeless.Poly1

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class ProposalUserHistoryConsumerActor(userHistoryCoordinator: ActorRef)
    extends KafkaConsumerActor[ProposalEventWrapper](ProposalProducerActor.topicKey)
    with ActorEventBusServiceComponent
    with MailJetTemplateConfigurationExtension
    with ActorLogging {

  override protected val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]
  override val groupId = "proposal-user-history"

  implicit val timeout: Timeout = Timeout(3.seconds)

  override def handleMessage(message: ProposalEventWrapper): Future[Unit] = {
    message.event.fold(ToProposalEvent) match {
      case event: ProposalViewed   => handleProposalViewed(event)
      case event: ProposalUpdated  => handleProposalUpdated(event)
      case event: ProposalProposed => handleProposalProposed(event)
      case event: ProposalAccepted => handleProposalAccepted(event)
    }

  }

  object ToProposalEvent extends Poly1 {
    implicit val atProposalViewed: Case.Aux[ProposalViewed, ProposalViewed] = at(identity)
    implicit val atProposalUpdated: Case.Aux[ProposalUpdated, ProposalUpdated] = at(identity)
    implicit val atProposalProposed: Case.Aux[ProposalProposed, ProposalProposed] = at(identity)
    implicit val atProposalAccepted: Case.Aux[ProposalAccepted, ProposalAccepted] = at(identity)
  }

  def handleProposalViewed(event: ProposalViewed): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleProposalUpdated(event: ProposalUpdated): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleProposalProposed(event: ProposalProposed): Future[Unit] = {
    (userHistoryCoordinator ? LogUserProposalEvent(
      userId = event.userId,
      requestContext = event.requestContext,
      action = UserAction(
        date = event.eventDate,
        actionType = LogUserProposalEvent.action,
        arguments = UserProposal(content = event.content)
      )
    )).map { _ =>
      {}
    }
  }

  def handleProposalAccepted(event: ProposalAccepted): Future[Unit] = {
    (userHistoryCoordinator ? LogAcceptProposalEvent(
      userId = event.moderator,
      requestContext = event.requestContext,
      action = UserAction(date = event.eventDate, actionType = ProposalAccepted.actionType, arguments = event)
    )).map { _ =>
      {}
    }
  }
}

object ProposalUserHistoryConsumerActor {
  val name: String = "proposal-events-user-history-consumer"
  def props(userHistoryCoordinator: ActorRef): Props =
    Props(new ProposalUserHistoryConsumerActor(userHistoryCoordinator))
}
