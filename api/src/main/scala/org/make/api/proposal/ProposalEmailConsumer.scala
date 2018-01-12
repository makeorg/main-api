package org.make.api.proposal

import akka.actor.{ActorLogging, Props}
import akka.util.Timeout
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.{MailJetTemplateConfigurationExtension, MakeSettingsExtension}
import org.make.api.operation.OperationService
import org.make.api.technical.mailjet.{Recipient, SendEmail}
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor}
import org.make.api.user.UserService
import org.make.core.proposal.Proposal
import org.make.api.proposal.PublishedProposalEvent._
import org.make.core.user._
import shapeless.Poly1

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ProposalEmailConsumer(userService: UserService, proposalCoordinatorService: ProposalCoordinatorService, operationService: OperationService)
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
      case event: ProposalViewed           => handleProposalViewed(event)
      case event: ProposalUpdated          => handleProposalUpdated(event)
      case event: ProposalProposed         => handleProposalProposed(event)
      case event: ProposalAccepted         => handleProposalAccepted(event)
      case event: ProposalRefused          => handleProposalRefused(event)
      case event: ProposalPostponed        => handleProposalPostponed(event)
      case event: ProposalVoted            => handleVotedProposal(event)
      case event: ProposalUnvoted          => handleUnvotedProposal(event)
      case event: ProposalQualified        => handleQualifiedProposal(event)
      case event: ProposalUnqualified      => handleUnqualifiedProposal(event)
      case event: SimilarProposalsAdded    => handleSimilarProposalsAdded(event)
      case event: ProposalLocked           => handleLockedProposal(event)
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
    implicit val atSimilarProposalsAdded: Case.Aux[SimilarProposalsAdded, SimilarProposalsAdded] = at(identity)
    implicit val atProposalLocked: Case.Aux[ProposalLocked, ProposalLocked] = at(identity)
    implicit val atProposalPatched: Case.Aux[ProposalPatched, ProposalPatched] = at(identity)
    implicit val atProposalAddedToOperation: Case.Aux[ProposalAddedToOperation, ProposalAddedToOperation] = at(identity)
    implicit val atProposalRemovedFromOperation: Case.Aux[ProposalRemovedFromOperation, ProposalRemovedFromOperation] =
      at(identity)
  }

  def handleVotedProposal(event: ProposalVoted): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleSimilarProposalsAdded(event: SimilarProposalsAdded): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleUnvotedProposal(event: ProposalUnvoted): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleQualifiedProposal(event: ProposalQualified): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleUnqualifiedProposal(event: ProposalUnqualified): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleLockedProposal(event: ProposalLocked): Future[Unit] = {
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
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }

  def handleProposalAccepted(event: ProposalAccepted): Future[Unit] = {
    if (event.sendValidationEmail) {
      // OptionT[Future, Unit] is some kind of monad wrapper to be able to unwrap options with no boilerplate
      // it allows here to have a for-comprehension on methods returning Future[Option[_]]
      // Do not use unless it really simplifies the code readability

      val language = event.requestContext.language.getOrElse("fr")
      val country = event.requestContext.country.getOrElse("FR")


      val futureOperationSlug: Future[String] = event.requestContext.operationId match {
        case Some(operationId) => operationService.findOne(operationId).map(_.map(_.slug).getOrElse("core"))
        case None              => Future.successful("core")
      }

      futureOperationSlug.map { operationSlug =>
        val maybePublish: OptionT[Future, Unit] = for {
          proposal: Proposal <- OptionT(proposalCoordinatorService.getProposal(event.id))
          user: User <- OptionT(userService.getUser(proposal.author))
        } yield {
          val templateConfiguration = mailJetTemplateConfiguration.proposalAccepted(operationSlug, country, language)
          if (user.verified && templateConfiguration.enabled) {
            eventBusService.publish(
              SendEmail(
                templateId = Some(templateConfiguration.templateId),
                recipients = Seq(Recipient(email = user.email, name = user.fullName)),
                from = Some(
                  Recipient(name = Some(mailJetTemplateConfiguration.fromName), email = mailJetTemplateConfiguration.from)
                ),
                variables = Some(
                  Map(
                    "proposal_url" -> s"${mailJetTemplateConfiguration.getFrontUrl()}/#/proposal/${proposal.slug}",
                    "proposal_text" -> proposal.content,
                    "firstname" -> user.firstName.getOrElse(""),
                    "operation" -> event.requestContext.operationId.map(_.value).getOrElse(""),
                    "question" -> event.requestContext.question.getOrElse(""),
                    "location" -> event.requestContext.location.getOrElse(""),
                    "source" -> event.requestContext.source.getOrElse("")
                  )
                ),
                customCampaign = Some(templateConfiguration.customCampaign),
                monitoringCategory = Some(templateConfiguration.monitoringCategory)
              )
            )
          }
        }
        maybePublish.getOrElseF(
          Future.failed(
            new IllegalStateException(s"proposal or user not found or user not verified for proposal ${event.id.value}")
          )
        )
      }
    } else {
      Future.successful[Unit] {}
    }

  }

  def handleProposalRefused(event: ProposalRefused): Future[Unit] = {
    if (event.sendRefuseEmail) {
      // OptionT[Future, Unit] is some kind of monad wrapper to be able to unwrap options with no boilerplate
      // it allows here to have a for-comprehension on methods returning Future[Option[_]]
      // Do not use unless it really simplifies the code readability

      val language = event.requestContext.language.getOrElse("fr")
      val country = event.requestContext.country.getOrElse("FR")


      val futureOperationSlug: Future[String] = event.requestContext.operationId match {
        case Some(operationId) => operationService.findOne(operationId).map(_.map(_.slug).getOrElse("core"))
        case None              => Future.successful("core")
      }

      futureOperationSlug.map { operationSlug =>

        val maybePublish: OptionT[Future, Unit] = for {
          proposal: Proposal <- OptionT(proposalCoordinatorService.getProposal(event.id))
          user: User         <- OptionT(userService.getUser(proposal.author))
        } yield {
          val proposalRefused = mailJetTemplateConfiguration.proposalRefused(operationSlug, country, language)
          if (user.verified && proposalRefused.enabled) {
            eventBusService.publish(
              SendEmail(
                templateId = Some(proposalRefused.templateId),
                recipients = Seq(Recipient(email = user.email, name = user.fullName)),
                from = Some(
                  Recipient(name = Some(mailJetTemplateConfiguration.fromName), email = mailJetTemplateConfiguration.from)
                ),
                variables = Some(
                  Map(
                    "proposal_text" -> proposal.content,
                    "firstname" -> user.fullName.getOrElse(""),
                    "refusal_reason" -> proposal.refusalReason.getOrElse(""),
                    "registration_context" -> event.requestContext.operationId.map(_.value).getOrElse(""),
                    "operation" -> event.requestContext.operationId.map(_.value).getOrElse(""),
                    "question" -> event.requestContext.question.getOrElse(""),
                    "location" -> event.requestContext.location.getOrElse(""),
                    "source" -> event.requestContext.source.getOrElse("")
                  )
                ),
                customCampaign = Some(proposalRefused.customCampaign),
                monitoringCategory = Some(proposalRefused.monitoringCategory)
              )
            )
          }
        }
        maybePublish.getOrElseF(
          Future.failed(
            new IllegalStateException(s"proposal or user not found or user not verified for proposal ${event.id.value}")
          )
        )
      }
    } else {
      Future.successful[Unit] {}
    }

  }

  def handleProposalPostponed(event: ProposalPostponed): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }
}

object ProposalEmailConsumerActor {
  val name: String = "proposal-events-emails-consumer"
  def props(userService: UserService, proposalCoordinatorService: ProposalCoordinatorService, operationService: OperationService): Props =
    Props(new ProposalEmailConsumer(userService, proposalCoordinatorService, operationService))
}
