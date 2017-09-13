package org.make.api.proposal

import akka.actor.{ActorLogging, Props}
import akka.util.Timeout
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.{MailJetTemplateConfigurationExtension, MakeSettingsExtension}
import org.make.api.technical.mailjet.{Recipient, SendEmail}
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor}
import org.make.api.user.UserService
import org.make.core.proposal.Proposal
import org.make.core.proposal.ProposalEvent._
import org.make.core.user._
import shapeless.Poly1

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class ProposalEmailConsumer(userService: UserService, proposalCoordinatorService: ProposalCoordinatorService)
    extends KafkaConsumerActor[ProposalEventWrapper]
    with MakeSettingsExtension
    with ActorEventBusServiceComponent
    with MailJetTemplateConfigurationExtension
    with ActorLogging {

  override protected lazy val kafkaTopic = kafkaConfiguration.topics(ProposalProducerActor.topicKey)
  override protected val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]
  override val groupId = "proposal-email"

  implicit val timeout: Timeout = Timeout(3.seconds)

  override def handleMessage(message: ProposalEventWrapper): Future[Unit] = {
    message.event.fold(ToProposalEvent) match {
      case event: ProposalViewed   => handleProposalViewed(event)
      case event: ProposalUpdated  => handleProposalUpdated(event)
      case event: ProposalProposed => handleProposalProposed(event)
      case event: ProposalAccepted => handleProposalAccepted(event)
      case event: ProposalRefused  => handleProposalRefused(event)
      case event: ProposalVoted    => handleVotedProposal(event)
      case event: ProposalUnvoted  => handleUnvotedProposal(event)
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
  }

  def handleVotedProposal(event: ProposalVoted): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleUnvotedProposal(event: ProposalUnvoted): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
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
    userService.getUser(event.userId).map(_.get).map { user =>
      eventBusService.publish(
        SendEmail(
          templateId = Some(mailJetTemplateConfiguration.proposalSentTemplate),
          recipients = Seq(Recipient(email = user.email, name = user.fullName)),
          from = Some(
            Recipient(name = Some(mailJetTemplateConfiguration.fromName), email = mailJetTemplateConfiguration.from)
          ),
          variables = Some(Map("content" -> event.content, "name" -> user.fullName.getOrElse("")))
        )
      )
    }
  }

  def handleProposalAccepted(event: ProposalAccepted): Future[Unit] = {
    if (event.sendValidationEmail) {
      // OptionT[Future, Unit] is some kind of monad wrapper to be able to unwrap options with no boilerplate
      // it allows here to have a for-comprehension on methods returning Future[Option[_]]
      // Do not use unless it really simplifies the code readability

      val maybePublish: OptionT[Future, Unit] = for {
        proposal: Proposal <- OptionT(proposalCoordinatorService.getProposal(event.id))
        user: User         <- OptionT(userService.getUser(proposal.author))
      } yield
        eventBusService.publish(
          SendEmail(
            templateId = Some(mailJetTemplateConfiguration.proposalValidatedTemplate),
            recipients = Seq(Recipient(email = user.email, name = user.fullName)),
            from = Some(
              Recipient(name = Some(mailJetTemplateConfiguration.fromName), email = mailJetTemplateConfiguration.from)
            ),
            variables = Some(
              Map(
                "url" -> s"${settings.frontUrl}/#/proposal/${proposal.slug}",
                "content" -> proposal.content,
                "name" -> user.fullName.getOrElse("")
              )
            )
          )
        )

      maybePublish.getOrElseF(
        Future.failed(new IllegalStateException(s"proposal or user not found for proposal ${event.id.value}"))
      )
    } else {
      Future.successful[Unit] {}
    }

  }

  def handleProposalRefused(event: ProposalRefused): Future[Unit] = {
    if (event.sendRefuseEmail) {
      // OptionT[Future, Unit] is some kind of monad wrapper to be able to unwrap options with no boilerplate
      // it allows here to have a for-comprehension on methods returning Future[Option[_]]
      // Do not use unless it really simplifies the code readability

      val maybePublish: OptionT[Future, Unit] = for {
        proposal: Proposal <- OptionT(proposalCoordinatorService.getProposal(event.id))
        user: User         <- OptionT(userService.getUser(proposal.author))
      } yield
        eventBusService.publish(
          SendEmail(
            templateId = Some(mailJetTemplateConfiguration.proposalRefusedTemplate),
            recipients = Seq(Recipient(email = user.email, name = user.fullName)),
            from = Some(
              Recipient(name = Some(mailJetTemplateConfiguration.fromName), email = mailJetTemplateConfiguration.from)
            ),
            variables = Some(Map("content" -> proposal.content, "name" -> user.fullName.getOrElse("")))
          )
        )

      maybePublish.getOrElseF(
        Future.failed(new IllegalStateException(s"proposal or user not found for proposal ${event.id.value}"))
      )
    } else {
      Future.successful[Unit] {}
    }

  }

}

object ProposalEmailConsumerActor {
  val name: String = "proposal-events-emails-consumer"
  def props(userService: UserService, proposalCoordinatorService: ProposalCoordinatorService): Props =
    Props(new ProposalEmailConsumer(userService, proposalCoordinatorService))
}
