package org.make.api.proposal

import akka.actor.{ActorLogging, Props}
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.MailJetTemplateConfigurationExtension
import org.make.api.technical.mailjet.{Recipient, SendEmail}
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor}
import org.make.api.user.UserService
import org.make.core.proposal.Proposal
import org.make.core.proposal.ProposalEvent._
import org.make.core.user.User
import shapeless.Poly1

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProposalConsumerActor(userService: UserService, proposalCoordinatorService: ProposalCoordinatorService)
    extends KafkaConsumerActor[ProposalEventWrapper](ProposalProducerActor.topicKey)
    with ActorEventBusServiceComponent
    with MailJetTemplateConfigurationExtension
    with ActorLogging {

  override protected val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]

  override def handleMessage(message: ProposalEventWrapper): Future[Unit] = {
    message.event.fold(HandledMessages)
  }

  object HandledMessages extends Poly1 {

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
      sendValidationEmailIfNecessary(event)
    }

    def sendValidationEmailIfNecessary(event: ProposalAccepted): Future[Unit] = {
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
              variables = Some(
                Map("proposalId" -> proposal.proposalId.value, "slug" -> proposal.slug, "content" -> proposal.content)
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

    implicit val atProposalViewed: Case.Aux[ProposalViewed, Future[Unit]] = at(handleProposalViewed)
    implicit val atProposalUpdated: Case.Aux[ProposalUpdated, Future[Unit]] = at(handleProposalUpdated)
    implicit val atProposalProposed: Case.Aux[ProposalProposed, Future[Unit]] = at(handleProposalProposed)
    implicit val atProposalAccepted: Case.Aux[ProposalAccepted, Future[Unit]] = at(handleProposalAccepted)
  }
}

object ProposalConsumerActor {
  val name: String = "proposal-events-consumer"
  def props(userService: UserService, proposalCoordinatorService: ProposalCoordinatorService): Props =
    Props(new ProposalConsumerActor(userService, proposalCoordinatorService))
}
