package org.make.api.proposal

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.MailJetTemplateConfigurationExtension
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor}
import org.make.api.userhistory._
import shapeless.Poly1

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ProposalUserHistoryConsumerActor(userHistoryCoordinator: ActorRef)
    extends KafkaConsumerActor[ProposalEventWrapper]
    with ActorEventBusServiceComponent
    with MailJetTemplateConfigurationExtension
    with ActorLogging {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(ProposalProducerActor.topicKey)
  override protected val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]
  override val groupId = "proposal-user-history"

  implicit val timeout: Timeout = Timeout(3.seconds)

  override def handleMessage(message: ProposalEventWrapper): Future[Unit] = {
    message.event.fold(ToProposalEvent) match {
      case event: ProposalViewed           => handleProposalViewed(event)
      case event: ProposalUpdated          => handleProposalUpdated(event)
      case event: ProposalProposed         => handleProposalProposed(event)
      case event: ProposalAccepted         => handleProposalAccepted(event)
      case event: ProposalRefused          => handleProposalRefused(event)
      case event: ProposalPostponed        => handleProposalPostponed(event)
      case event: ProposalVoted            => handleProposalVoted(event)
      case event: ProposalUnvoted          => handleProposalUnvoted(event)
      case event: ProposalQualified        => handleProposalQualified(event)
      case event: ProposalUnqualified      => handleProposalUnqualified(event)
      case event: SimilarProposalsAdded    => handleSimilarProposalsAdded(event)
      case event: ProposalLocked           => handleProposalLocked(event)
      case _: ProposalPatched              => Future.successful {}
      case _: ProposalAddedToOperation     => Future.successful {}
      case _: ProposalRemovedFromOperation => Future.successful {}
    }

  }

  object ToProposalEvent extends Poly1 {
    implicit val atProposalViewed: Case.Aux[ProposalViewed, ProposalViewed] = at(identity)
    implicit val atProposalUpdated: Case.Aux[ProposalUpdated, ProposalUpdated] = at(identity)
    implicit val atProposalProposed: Case.Aux[ProposalProposed, ProposalProposed] = at(identity)
    implicit val atProposalAccepted: Case.Aux[ProposalAccepted, ProposalAccepted] = at(identity)
    implicit val atProposalRefused: Case.Aux[ProposalRefused, ProposalRefused] = at(identity)
    implicit val atProposalPostponed: Case.Aux[ProposalPostponed, ProposalPostponed] = at(identity)
    implicit val atProposalVoted: Case.Aux[ProposalVoted, ProposalVoted] = at(identity)
    implicit val atProposalUnvoted: Case.Aux[ProposalUnvoted, ProposalUnvoted] = at(identity)
    implicit val atProposalQualified: Case.Aux[ProposalQualified, ProposalQualified] = at(identity)
    implicit val atProposalUnqualified: Case.Aux[ProposalUnqualified, ProposalUnqualified] = at(identity)
    implicit val atProposalLocked: Case.Aux[ProposalLocked, ProposalLocked] = at(identity)
    implicit val atSimilarProposalsAdded: Case.Aux[SimilarProposalsAdded, SimilarProposalsAdded] = at(identity)
    implicit val atProposalPatched: Case.Aux[ProposalPatched, ProposalPatched] = at(identity)
    implicit val atProposalAddedToOperation: Case.Aux[ProposalAddedToOperation, ProposalAddedToOperation] = at(identity)
    implicit val atProposalRemovedFromOperation: Case.Aux[ProposalRemovedFromOperation, ProposalRemovedFromOperation] =
      at(identity)
  }

  def handleProposalViewed(event: ProposalViewed): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleSimilarProposalsAdded(event: SimilarProposalsAdded): Future[Unit] = {
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
    (
      userHistoryCoordinator ? LogUserProposalEvent(
        userId = event.userId,
        requestContext = event.requestContext,
        action = UserAction(
          date = event.eventDate,
          actionType = LogUserProposalEvent.action,
          arguments = UserProposal(content = event.content, event.theme)
        )
      )
    ).map(_ => {})
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

  def handleProposalLocked(event: ProposalLocked): Future[Unit] = {
    Future.successful {
      userHistoryCoordinator ! LogLockProposalEvent(
        userId = event.moderatorId,
        moderatorName = event.moderatorName,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = ProposalLocked.actionType, arguments = event)
      )
    }
  }

  def handleProposalAccepted(event: ProposalAccepted): Future[Unit] = {
    (
      userHistoryCoordinator ? LogAcceptProposalEvent(
        userId = event.moderator,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = ProposalAccepted.actionType, arguments = event)
      )
    ).map(_ => {})
  }

  def handleProposalRefused(event: ProposalRefused): Future[Unit] = {
    (
      userHistoryCoordinator ? LogRefuseProposalEvent(
        userId = event.moderator,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = ProposalRefused.actionType, arguments = event)
      )
    ).map(_ => {})
  }

  def handleProposalPostponed(event: ProposalPostponed): Future[Unit] = {
    (
      userHistoryCoordinator ? LogPostponeProposalEvent(
        userId = event.moderator,
        requestContext = event.requestContext,
        action = UserAction(date = event.eventDate, actionType = ProposalPostponed.actionType, arguments = event)
      )
    ).map(_ => {})
  }
}

object ProposalUserHistoryConsumerActor {
  val name: String = "proposal-events-user-history-consumer"
  def props(userHistoryCoordinator: ActorRef): Props =
    Props(new ProposalUserHistoryConsumerActor(userHistoryCoordinator))
}
