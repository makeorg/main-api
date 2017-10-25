package org.make.api.proposal

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.proposal.ProposalEvent._
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor}
import shapeless.Poly1

import scala.concurrent.Future
import scala.concurrent.duration._

class ProposalSessionHistoryConsumerActor(sessionHistoryCoordinator: ActorRef)
    extends KafkaConsumerActor[ProposalEventWrapper]
    with ActorEventBusServiceComponent
    with ActorLogging {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(ProposalProducerActor.topicKey)
  override protected val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]
  override val groupId = "proposal-session-history"

  implicit val timeout: Timeout = Timeout(3.seconds)

  override def handleMessage(message: ProposalEventWrapper): Future[Unit] = {
    message.event.fold(ToProposalEvent) match {
      case event: ProposalViewed      => handleProposalViewed(event)
      case event: ProposalUpdated     => handleProposalUpdated(event)
      case event: ProposalProposed    => handleProposalProposed(event)
      case event: ProposalAccepted    => handleProposalAccepted(event)
      case event: ProposalRefused     => handleProposalRefused(event)
      case event: ProposalVoted       => handleProposalVoted(event)
      case event: ProposalUnvoted     => handleProposalUnvoted(event)
      case event: ProposalQualified   => handleProposalQualified(event)
      case event: ProposalUnqualified => handleProposalUnqualified(event)
    }

  }

  object ToProposalEvent extends Poly1 {
    implicit val atProposalViewed: Case.Aux[ProposalViewed, ProposalViewed] = at(identity)
    implicit val atProposalUpdated: Case.Aux[ProposalUpdated, ProposalUpdated] = at(identity)
    implicit val atProposalProposed: Case.Aux[ProposalProposed, ProposalProposed] = at(identity)
    implicit val atProposalAccepted: Case.Aux[ProposalAccepted, ProposalAccepted] = at(identity)
    implicit val atProposalRefused: Case.Aux[ProposalRefused, ProposalRefused] = at(identity)
    implicit val atProposalVoted: Case.Aux[ProposalVoted, ProposalVoted] = at(identity)
    implicit val atProposalUnvoted: Case.Aux[ProposalUnvoted, ProposalUnvoted] = at(identity)
    implicit val atProposalQualified: Case.Aux[ProposalQualified, ProposalQualified] = at(identity)
    implicit val atProposalUnqualified: Case.Aux[ProposalUnqualified, ProposalUnqualified] = at(identity)
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
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleProposalAccepted(event: ProposalAccepted): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleProposalRefused(event: ProposalRefused): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleProposalVoted(event: ProposalVoted): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleProposalUnvoted(event: ProposalUnvoted): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleProposalQualified(event: ProposalQualified): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleProposalUnqualified(event: ProposalUnqualified): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }
}

object ProposalSessionHistoryConsumerActor {
  val name: String = "proposal-events-session-history-consumer"
  def props(sessionHistoryCoordinator: ActorRef): Props =
    Props(new ProposalSessionHistoryConsumerActor(sessionHistoryCoordinator))
}