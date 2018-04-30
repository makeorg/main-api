package org.make.api.proposal

import akka.actor.{ActorLogging, Props}
import akka.util.Timeout
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.{MailJetTemplateConfigurationExtension, MakeSettingsExtension}
import org.make.api.operation.OperationService
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.technical.mailjet.{Recipient, SendEmail}
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor, TimeSettings}
import org.make.api.user.UserService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProposalEmailConsumer(userService: UserService,
                            proposalCoordinatorService: ProposalCoordinatorService,
                            operationService: OperationService)
    extends KafkaConsumerActor[ProposalEventWrapper]
    with MakeSettingsExtension
    with ActorEventBusServiceComponent
    with MailJetTemplateConfigurationExtension
    with ActorLogging {

  override protected lazy val kafkaTopic = kafkaConfiguration.topics(ProposalProducerActor.topicKey)
  override protected val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]
  override val groupId = "proposal-email"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: ProposalEventWrapper): Future[Unit] = {
    message.event.fold(ToProposalEvent) match {
      case event: ProposalAccepted             => handleProposalAccepted(event)
      case event: ProposalRefused              => handleProposalRefused(event)
      case event: ProposalPostponed            => handleProposalPostponed(event)
      case event: ProposalViewed               => doNothing(event)
      case event: ProposalUpdated              => doNothing(event)
      case event: ProposalProposed             => doNothing(event)
      case event: ProposalVoted                => doNothing(event)
      case event: ProposalUnvoted              => doNothing(event)
      case event: ProposalQualified            => doNothing(event)
      case event: ProposalUnqualified          => doNothing(event)
      case event: SimilarProposalsAdded        => doNothing(event)
      case event: ProposalLocked               => doNothing(event)
      case event: ProposalPatched              => doNothing(event)
      case event: ProposalAddedToOperation     => doNothing(event)
      case event: ProposalRemovedFromOperation => doNothing(event)
    }

  }

  def handleProposalAccepted(event: ProposalAccepted): Future[Unit] = {
    if (event.sendValidationEmail) {
      // OptionT[Future, Unit] is some kind of monad wrapper to be able to unwrap options with no boilerplate
      // it allows here to have a for-comprehension on methods returning Future[Option[_]]
      // Do not use unless it really simplifies the code readability

      val futureOperationSlug: Future[String] = event.operation match {
        case Some(operationId) => operationService.findOne(operationId).map(_.map(_.slug).getOrElse("core"))
        case None              => Future.successful("core")
      }

      futureOperationSlug.map { operationSlug =>
        val maybePublish: OptionT[Future, Unit] = for {
          proposal <- OptionT(proposalCoordinatorService.getProposal(event.id))
          user     <- OptionT(userService.getUser(proposal.author))
        } yield {
          val country = proposal.country.getOrElse(user.country)
          val language = proposal.language.getOrElse(user.language)
          val templateConfiguration = mailJetTemplateConfiguration.proposalAccepted(operationSlug, country, language)
          if (user.verified && templateConfiguration.enabled) {
            eventBusService.publish(
              SendEmail.create(
                templateId = Some(templateConfiguration.templateId),
                recipients = Seq(Recipient(email = user.email, name = user.fullName)),
                from = Some(
                  Recipient(
                    name = Some(mailJetTemplateConfiguration.fromName),
                    email = mailJetTemplateConfiguration.from
                  )
                ),
                variables = Some(
                  Map(
                    "proposal_url" -> s"${mailJetTemplateConfiguration.getFrontUrl()}/#/${proposal.country
                      .getOrElse("FR")}/proposal/${proposal.slug}",
                    "proposal_text" -> proposal.content,
                    "firstname" -> user.firstName.getOrElse(""),
                    "operation" -> event.operation.map(_.value).getOrElse(""),
                    "question" -> event.requestContext.question.getOrElse(""),
                    "location" -> event.requestContext.location.getOrElse(""),
                    "source" -> event.requestContext.source.getOrElse("")
                  )
                ),
                customCampaign = templateConfiguration.customCampaign,
                monitoringCategory = templateConfiguration.monitoringCategory
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

      val futureOperationSlug: Future[String] = event.operation match {
        case Some(operationId) => operationService.findOne(operationId).map(_.map(_.slug).getOrElse("core"))
        case None              => Future.successful("core")
      }

      futureOperationSlug.map { operationSlug =>
        val maybePublish: OptionT[Future, Unit] = for {
          proposal <- OptionT(proposalCoordinatorService.getProposal(event.id))
          user     <- OptionT(userService.getUser(proposal.author))
        } yield {
          val country = proposal.country.getOrElse(user.country)
          val language = proposal.language.getOrElse(user.language)
          val templateConfiguration = mailJetTemplateConfiguration.proposalRefused(operationSlug, country, language)
          if (user.verified && templateConfiguration.enabled) {
            eventBusService.publish(
              SendEmail.create(
                templateId = Some(templateConfiguration.templateId),
                recipients = Seq(Recipient(email = user.email, name = user.fullName)),
                from = Some(
                  Recipient(
                    name = Some(mailJetTemplateConfiguration.fromName),
                    email = mailJetTemplateConfiguration.from
                  )
                ),
                variables = Some(
                  Map(
                    "proposal_text" -> proposal.content,
                    "firstname" -> user.fullName.getOrElse(""),
                    "refusal_reason" -> proposal.refusalReason.getOrElse(""),
                    "registration_context" -> event.requestContext.operationId.map(_.value).getOrElse(""),
                    "operation" -> event.operation.map(_.value).getOrElse(""),
                    "question" -> event.requestContext.question.getOrElse(""),
                    "location" -> event.requestContext.location.getOrElse(""),
                    "source" -> event.requestContext.source.getOrElse("")
                  )
                ),
                customCampaign = templateConfiguration.customCampaign,
                monitoringCategory = templateConfiguration.monitoringCategory
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
  def props(userService: UserService,
            proposalCoordinatorService: ProposalCoordinatorService,
            operationService: OperationService): Props =
    Props(new ProposalEmailConsumer(userService, proposalCoordinatorService, operationService))
}
